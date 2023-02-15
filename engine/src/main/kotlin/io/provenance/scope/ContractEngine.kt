package io.provenance.scope

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.Message
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.contract.proto.Contracts.ExecutionResult.Result.SKIP
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.util.ContractDefinitionException
import io.provenance.scope.util.ProtoUtil.proposedRecordOf
import io.provenance.scope.util.scopeOrNull
import io.provenance.scope.util.toMessageWithStackTrace
import io.provenance.scope.util.toUuid
import io.provenance.scope.util.withoutLogging
import org.bouncycastle.asn1.x500.style.RFC4519Style.name
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.security.PublicKey

class ContractEngine(
    private val osClient: CachedOsClient,
    private val disableContractLogs: Boolean = true,
) {
    private val log = LoggerFactory.getLogger(this::class.java);

    private fun <T> withConfigurableLogging(block: () -> T): T = if (disableContractLogs) {
        withoutLogging(block)
    } else {
        block()
    }

    fun handle(
        encryptionKeyRef: KeyRef,
        signingKeyRef: KeyRef,
        envelope: Envelope,
        affiliateSharePublicKeys: Collection<PublicKey>,
    ): Envelope {
        log.trace("Running contract engine")

        val contract = envelope.contract

        val spec = osClient.getRecord(contract.spec.dataLocation.classname, contract.spec.dataLocation.ref.hash.base64Decode(), encryptionKeyRef).get() as? ContractSpec
                ?: throw ContractDefinitionException("Spec stored at contract.spec.dataLocation is not of type ${ContractSpec::class.java.name}")
        val wasm = osClient.getJar(spec.definition.resourceLocation.ref.hash.base64Decode(), encryptionKeyRef).get().readAllBytes()

        return internalRun(
                contract,
                wasm,
                envelope,
                encryptionKeyRef,
                signingKeyRef,
                affiliateSharePublicKeys,
                spec
            )
    }

    private fun internalRun(
        contract: Contracts.Contract,
        contractWasmBytes: ByteArray,
        envelope: Envelope,
        encryptionKeyRef: KeyRef,
        signingKeyRef: KeyRef,
        shares: Collection<PublicKey>,
        spec: ContractSpec
    ): Envelope {
        val signer = signingKeyRef.signer()
        val scope = envelope.scopeOrNull()

        // validate contract
        contract.validateAgainst(spec)

        // todo: validate that all shares passed in are already on the scope in the case of an existing scope??? Or will the contract always have all parties in recitals anyways for data share purposes?

        val contractBuilder = contract.toBuilder()
        val contractWrapper = ContractWrapper(
            contractWasmBytes,
            encryptionKeyRef,
            osClient,
            contractBuilder,
            disableContractLogs,
        )

        val (execList, skipList) = contractWrapper.functions.partition { it.canExecute() }

        log.trace("Skipped Records: ${skipList.map { it.method.name }}")

        val functionResults =
            execList
                .map { function ->
                    val (considerationBuilder, result) = try {
                        withConfigurableLogging { function.invoke() }
                    } catch (t: Throwable) {
                        // Abort execution on a failed condition
                        log.error("Error executing condition ${contractWrapper.contract.structure.name}.${function.method.name} [Exception classname: ${t.javaClass.name}]", t)
                        function.considerationBuilder.result = failResult(t) // TODO: how to handle failures properly

                        val contractForSignature = contractBuilder.build()
                        return envelope.toBuilder()
                            .setContract(contractForSignature)
                            .addSignatures(signer.sign(contractForSignature))
                            .build()
                    }
                    if (result == null) {
                        throw ContractDefinitionException(
                            """
                                Invoked function returned null instead of type ${Message::class.java.name}
                                [class: ${contractWrapper.contract.structure.name}]
                                [invoked function: ${function.method.name}]
                            """.trimIndent()
                        )
                    }

                    ResultSetter {
                        signAndStore(
                            function.method,
                            result,
                            contract.toAudience(scope, shares),
                            encryptionKeyRef,
                            signingKeyRef,
                            scope
                        ).let { signFuture ->
                            Futures.transform(signFuture, {
                                considerationBuilder.result = it
                            }, MoreExecutors.directExecutor()) // todo: should this be a different type of executor?
                        }
                    }
                } + skipList.map { function ->
                ResultSetter {
                    function.considerationBuilder.resultBuilder.setResult(SKIP)
                        .outputBuilder
                        .setName(function.method.name)
                        .setClassname(function.method.returnType)
                    Futures.immediateFuture(Unit)
                }
            }

        functionResults
            .also { log.trace("Saving ${it.size} results for ContractEngine.handle") }
            .map { it.setter() }
            .forEach { it.get() }

        val contractForSignature = contractBuilder.build()
        log.trace("Inputs list for execution: ${contractForSignature.considerationsList.flatMap { it.inputsList }}")
        log.trace("Outputs list for execution: ${contractForSignature.considerationsList.map { it.result.output }}")
        return envelope.toBuilder()
            .setContract(contractForSignature)
            .addSignatures(signer.sign(contractForSignature))
            .build()
    }

    private fun signAndStore(
        method: P8eFunction,
        message: ByteArray,
        audience: Set<PublicKey>,
        encryptionKeyRef: KeyRef,
        signingKeyRef: KeyRef,
        scope: ScopeResponse?
    ): ListenableFuture<Contracts.ExecutionResult> {
        val putResponse = osClient.putJar(
            ByteArrayInputStream(message),
            signingKeyRef,
            encryptionKeyRef,
            message.size.toLong(),
            audience
        )

        return Futures.transform(putResponse, {
            val sha512 = it!!.value

            // todo: it would appear that the ancestorHash isn't a thing on chain anymore? can we remove this?
            val ancestorHash = scope?.recordsList
                ?.map { it.record }
                ?.find { it.name == method.name }
                ?.outputsList
                ?.first() // todo: how to handle multiple outputs?
                ?.hash

            Contracts.ExecutionResult.newBuilder()
                .setResult(Contracts.ExecutionResult.Result.PASS)
                .setOutput(proposedRecordOf(
                    method.name,
                    sha512,
                    method.returnType,
                    scope?.scope?.scopeIdInfo?.scopeUuid?.toUuid(),
                    ancestorHash
                )
                ).build()

        }, MoreExecutors.directExecutor()) // todo: should this be a different type of executor?
    }

    private fun failResult(t: Throwable): Contracts.ExecutionResult {
        return Contracts.ExecutionResult
            .newBuilder()
            .setResult(Contracts.ExecutionResult.Result.FAIL)
            .setErrorMessage(t.toMessageWithStackTrace())
            .build()
    }
}

data class ResultSetter(val setter: () -> ListenableFuture<Unit>)

fun Contract.toAudience(scope: ScopeResponse?, shares: Collection<PublicKey>): Set<PublicKey> = recitalsList
    .filter { it.hasSigner() }
    .map { it.signer.encryptionPublicKey }
//    .plus( // can't pull from existing scope, so all parties must be on recitals list/shares passed in
        // todo: maybe pull these via address from AffiliateRepository?
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
