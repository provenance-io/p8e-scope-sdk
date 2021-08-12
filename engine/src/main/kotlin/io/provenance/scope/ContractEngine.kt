package io.provenance.scope

import com.google.protobuf.Message
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.classloader.ClassLoaderCache
import io.provenance.scope.classloader.MemoryClassLoader
import io.provenance.scope.contract.proto.Commons
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.contract.proto.Contracts.ExecutionResult.Result.SKIP
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.encryption.crypto.SignerFactory
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.proto.Common
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.objectstore.util.base64Encode
import io.provenance.scope.objectstore.util.toPublicKeyProtoOS
import io.provenance.scope.util.ContractDefinitionException
import io.provenance.scope.util.ProtoUtil.proposedRecordOf
import io.provenance.scope.util.ThreadPoolFactory
import io.provenance.scope.util.toHexString
import io.provenance.scope.util.toMessageWithStackTrace
import io.provenance.scope.util.toUuidProv
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.concurrent.thread

// TODO move somewhere else
fun PublicKey.toHex() = toPublicKeyProtoOS().toByteArray().toHexString()

class ContractEngine(
    private val osClient: CachedOsClient,
    private val signerFactory: SignerFactory,
) {
    companion object {
        private val executor = ThreadPoolFactory.newFixedThreadPool(
            System.getenv("CONTRACT_ENGINE_THREAD_POOL_SIZE")
                ?.takeIf { it.isNotEmpty() }
                ?.toInt() ?: 32,
            "contract-engine-%d"
        )
    }

    private val _definitionService = DefinitionService(osClient)

    private val log = LoggerFactory.getLogger(this::class.java);

    fun handle(
        encryptionKeyRef: KeyRef,
        signingKeyRef: KeyRef,
        envelope: Envelope,
        scope: ScopeResponse?,
        affiliateSharePublicKeys: Collection<PublicKey>, // todo: separate array for other scope parties that are not on the contract, or just have them all supplied here?
    ): Envelope {
        log.info("Running contract engine")

        val contract = envelope.contract

        val spec = osClient.getRecord(contract.spec.dataLocation.classname, contract.spec.dataLocation.ref.hash.base64Decode(), encryptionKeyRef).get() as? ContractSpec
                ?: throw ContractDefinitionException("Spec stored at contract.spec.dataLocation is not of type ${ContractSpec::class.java.name}")

        val classLoaderKey = "${spec.definition.resourceLocation.ref.hash}-${contract.definition.resourceLocation.ref.hash}-${spec.functionSpecsList.first().outputSpec.spec.resourceLocation.ref.hash}"
        val memoryClassLoader = ClassLoaderCache.classLoaderCache.get(classLoaderKey) {
            MemoryClassLoader("", ByteArrayInputStream(ByteArray(0)))
        }

        return memoryClassLoader.forThread {
            internalRun(
                contract,
                envelope,
                encryptionKeyRef,
                signingKeyRef,
                memoryClassLoader,
                affiliateSharePublicKeys,
                scope,
                spec
            )
        }
    }

    private fun internalRun(
        contract: Contracts.Contract,
        envelope: Envelope,
        encryptionKeyRef: KeyRef,
        signingKeyRef: KeyRef,
        memoryClassLoader: MemoryClassLoader,
        shares: Collection<PublicKey>,
        scope: ScopeResponse?,
        spec: ContractSpec
    ): Envelope {
        val definitionService = DefinitionService(osClient, memoryClassLoader)
        val signer = signerFactory.getSigner(signingKeyRef)

        // Load contract spec class
        val contractSpecClass = try {
                definitionService.loadClass(encryptionKeyRef, spec.definition)
            } catch (e: StatusRuntimeException) {
                if (e.status.code == Status.Code.NOT_FOUND) {
                    throw ContractDefinitionException(
                        """
                            Unable to load contract jar. Verify that you're using a jar that has been bootstrapped.
                            [classname: ${spec.definition.resourceLocation.classname}]
                            [public key: ${encryptionKeyRef.publicKey.toHex()}]
                            [hash: ${spec.definition.resourceLocation.ref.hash}]
                        """.trimIndent()
                    )
                }
                throw e
            }

        // Ensure all the classes listed in the spec are loaded into the MemoryClassLoader
        loadAllClasses(
            encryptionKeyRef,
            definitionService,
            spec
        )

        // validate contract
        contract.validateAgainst(spec)

        // todo: validate that all shares passed in are already on the scope in the case of an existing scope??? Or will the contract always have all parties in recitals anyways for data share purposes?

//        when (contract.type!!) {
//            Contracts.ContractType.CHANGE_SCOPE -> { // todo: it would appear contract type is no longer a thing...
//                if (scope != null &&
//                    envelope.status == Envelope.Status.CREATED &&
//                    contract.invoker.encryptionPublicKey == encryptionKeyRef.publicKey.toPublicKeyProto()
//                ) {
//                    val audience = scope.partiesList.map { it.signer.signingPublicKey }
//                        .plus(contract.recitalsList.map { it.signer.signingPublicKey })
//                        .map { it.toPublicKey() }
//                        .let { it + affiliateService.getSharePublicKeys(it).value }
//                        .toSet()
//
//                    log.debug("Change scope ownership - adding ${audience.map { it.toHex() }} [scope: ${scope.uuid.value}] [executionUuid: ${envelope.executionUuid.value}]")
//
//                    this.getScopeData(encryptionKeyRef, definitionService, scope, signer)
//                        .threadedMap(executor) { definitionService.save(encryptionKeyRef.publicKey, it, signer, audience) }
//                } else { }
//            }
//            Contracts.ContractType.FACT_BASED, Contracts.ContractType.UNRECOGNIZED -> Unit
//        } as Unit

        val contractBuilder = contract.toBuilder()
        val contractWrapper = ContractWrapper(
            executor,
            contractSpecClass,
            encryptionKeyRef,
            definitionService,
            osClient,
            contractBuilder,
        )

        val (execList, skipList) = contractWrapper.functions.partition { it.canExecute() }
        val functionResults =
            execList
                .map { function ->
                    val (considerationBuilder, result) = try {
                        function.invoke()
                    } catch (t: Throwable) {
                        // Abort execution on a failed condition
                        log.error("Error executing condition ${contractWrapper.contractClass}.${function.method.name} [Exception classname: ${t.javaClass.name}]", t)
                        function.considerationBuilder.result = failResult(t)

                        val contractForSignature = contractBuilder.build()
                        return envelope.toBuilder()
                            .setContract(contractForSignature)
                            .addSignatures(signer.sign(contractForSignature).toContractSignature())
                            .build()
                    }
                    if (result == null) {
                        throw ContractDefinitionException(
                            """
                                Invoked function returned null instead of type ${Message::class.java.name}
                                [class: ${contractWrapper.contractClass.name}]
                                [invoked function: ${function.method.name}]
                            """.trimIndent()
                        )
                    }

                    ResultSetter {
                        considerationBuilder.result = signAndStore(
                            definitionService,
                            function.fact.name,
                            result,
                            contract.toAudience(scope, shares),
                            encryptionKeyRef,
                            signingKeyRef,
                            scope
                        )
                    }
                } + skipList.map { function ->
                ResultSetter {
                    function.considerationBuilder.result = Contracts.ExecutionResult.newBuilder().setResult(SKIP).build()
                }
            }

        functionResults
            .also { log.info("Saving ${it.size} results for ContractEngine.handle") }
            .threadedMap(executor) { resultSetter -> resultSetter.setter().run { null } }

        val contractForSignature = contractBuilder.build()
        return envelope.toBuilder()
            .setContract(contractForSignature)
            .addSignatures(signer.sign(contractForSignature).toContractSignature())
            .build()
    }

//    private fun getScopeData(
//        encryptionKeyRef: KeyRef,
//        definitionService: DefinitionService,
//        scope: ScopeResponse,
//        signer: SignerImpl
//    ): List<ByteArray> =
//        scope.recordsList
//            // todo: is the process hash the same as the old record/fact hash? Is the record process name the same as the old record classname?
//            // todo: what to do about the potential for multiple outputs in the future?
//            .flatMap { record -> record.record.inputsList.map { input -> Pair(input.typeName, input.hash) } + Pair("unset", record.record.process.hash) + Pair(record.record.process.name, record.record.outputsList.first().hash) }
////            .plus(scope.sessionsList.map { Pair(it.classname, it.specification) }) // todo: is this still needed? Sessions don't appear to have anything like a classname, looks like there is still a specification id available in the wrapper/session
//            .toSet()
//            .threadedMap(executor) { (classname, hash) -> definitionService.get(encryptionKeyRef = encryptionKeyRef, hash = hash, classname = classname, signer = signer).readAllBytes() }
//            .toList()

    private fun loadAllClasses(
        encryptionKeyRef: KeyRef,
        definitionService: DefinitionService,
        spec: ContractSpec
    ) {
        mutableListOf(spec.definition)
            .apply {
                add(
                    spec.functionSpecsList
                        .first()
                        .outputSpec
                        .spec
                )
            }.map { definition ->
                with (definition.resourceLocation) {
                    this to osClient.getJar(
                        this.ref.hash.base64Decode(),
                        encryptionKeyRef,
                    )
                }
            }.toList()
            .map { (resourceLocation, future) -> resourceLocation to future.get() }
            .forEach { (location, inputStream) ->  definitionService.addJar(location.ref.hash, inputStream) }
    }

    private fun signAndStore(
        definitionService: DefinitionService,
        name: String,
        message: Message,
        audiences: Set<PublicKey>,
        encryptionKeyRef: KeyRef,
        signingKeyRef: KeyRef,
        scope: ScopeResponse?
    ): Contracts.ExecutionResult {
        val sha512 = osClient.putRecord(
            message,
            signingKeyRef,
            encryptionKeyRef,
            audiences
        ).get().value.toByteArray() // todo: is this the correct decode here? Appears to be utf-8 encoded hash (not base64'd as well)

        val ancestorHash = scope?.recordsList
            ?.map { it.record }
            ?.find { it.name == name }
            ?.outputsList
            ?.first() // todo: how to handle multiple outputs?
            ?.hash

        return Contracts.ExecutionResult.newBuilder()
            .setResult(Contracts.ExecutionResult.Result.PASS)
            .setOutput(proposedRecordOf(
                name,
                String(sha512.base64Encode()),
                message.javaClass.name,
                scope?.scope?.scopeIdInfo?.scopeUuid?.toUuidProv(),
                ancestorHash
            )
            ).build()
    }

    private fun failResult(t: Throwable): Contracts.ExecutionResult {
        return Contracts.ExecutionResult
            .newBuilder()
            .setResult(Contracts.ExecutionResult.Result.FAIL)
            .setErrorMessage(t.toMessageWithStackTrace())
            .build()
    }
}

data class ResultSetter(val setter: () -> Unit)

fun Contract.toAudience(scope: ScopeResponse?, shares: Collection<PublicKey>): Set<PublicKey> = recitalsList
    .filter { it.hasSigner() }
    .map { it.signer.encryptionPublicKey }
//    .plus( // can't pull from existing scope, so all parties must be on recitals list/shares passed in
//        scope?.sessionsList
//            ?.flatMap { it.session.partiesList }
//            ?.filter { it.hasSigner() }
//            ?.map { it.signer.encryptionPublicKey }
//            ?.filter { it.isInitialized }
//            ?: listOf()
//    )
    .map { ECUtils.convertBytesToPublicKey(it.publicKeyBytes.toByteArray()) }
    .plus(shares)
    .toSet()

// todo: why do we have two identical signature protos currently between encryption/contract?
fun Common.Signature.toContractSignature(): Commons.Signature = Commons.Signature.parseFrom(toByteArray())

fun<T, K> Collection<T>.threadedMap(executor: ExecutorService, fn: (T) -> K): Collection<K> =
    this.map { item ->
        CompletableFuture<K>().also { future ->
            thread(start = false) {
                try {
                    future.complete(fn(item))
                } catch (t: Throwable) {
                    future.completeExceptionally(t)
                }
            }.let(executor::submit)
        }
    }.map { it.get() }
