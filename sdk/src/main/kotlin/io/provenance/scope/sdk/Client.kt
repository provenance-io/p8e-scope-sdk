package io.provenance.scope.sdk

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.ContractEngine
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition
import io.provenance.scope.contract.contracts.ContractHash
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.Envelopes.EnvelopeState
import io.provenance.scope.contract.proto.ProtoHash
import io.provenance.scope.proto.PK
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.signer
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.sdk.ContractSpecMapper.dehydrateSpec
import io.provenance.scope.sdk.ContractSpecMapper.orThrowContractDefinition
import io.provenance.scope.sdk.extensions.isSigned
import io.provenance.scope.sdk.extensions.resultHash
import io.provenance.scope.sdk.extensions.resultType
import io.provenance.scope.sdk.extensions.uuid
import io.provenance.scope.sdk.extensions.validateRecordsRequested
import io.provenance.scope.sdk.mailbox.MailHandlerFn
import io.provenance.scope.sdk.mailbox.MailboxService
import io.provenance.scope.sdk.mailbox.PollAffiliateMailbox
import io.provenance.scope.util.ContractBootstrapException
import io.provenance.scope.util.toUuid
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.ServiceLoader
import io.opentracing.util.GlobalTracer;
import io.provenance.scope.contract.proto.Commons
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

// TODO (@steve)
// add error handling and start with extensions present here
// make resolver that can go from byte array to Message class

// todo: need to consolidate/organize key helpers
fun java.security.PublicKey.toPublicKeyProto(): PK.PublicKey =
    PK.PublicKey.newBuilder()
        .setCurve(PK.KeyCurve.SECP256K1)
        .setType(PK.KeyType.ELLIPTIC)
        .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(this)))
        .setCompressed(false)
        .build()

class SharedClient(val config: ClientConfig) : Closeable {
    val osClient: CachedOsClient = CachedOsClient(OsClient(config.osGrpcUrl, config.osGrpcDeadlineMs), config.osDecryptionWorkerThreads, config.osConcurrencySize, config.cacheRecordSizeInBytes)
    val contractEngine: ContractEngine = ContractEngine(osClient)

    val affiliateRepository = AffiliateRepository(config.mainNet)

    val mailboxService = MailboxService(osClient.osClient, affiliateRepository)

    override fun close() {
        osClient.osClient.close()
    }

    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return osClient.osClient.awaitTermination(timeout, unit)
    }
}

class Client(val inner: SharedClient, val affiliate: Affiliate) {

    private val log = LoggerFactory.getLogger(this::class.java);
    private val tracer = GlobalTracer.get()

    val indexer: ProtoIndexer = ProtoIndexer(inner.osClient, inner.config.mainNet, affiliate)

    companion object {
        // TODO add a set of affiliates here - every time we create a new Client we should add to it and verify the new affiliate is unique
        private val contractHashes = ServiceLoader.load(ContractHash::class.java).toList() // todo: can we use the contract/proto hashes to generate a dynamic list of what should/should not be loaded from memory vs. system class loader
        private val protoHashes = ServiceLoader.load(ProtoHash::class.java).toList()
    }

    fun<T: P8eContract> newSession(clazz: Class<T>, scope: ScopeResponse): Session.Builder {
        val contractHash = getContractHash(clazz)
        val protoHash = clazz.methods
            .find { Message::class.java.isAssignableFrom(it.returnType) }
            ?.returnType
            ?.let { getProtoHash(contractHash, it) }
            .orThrow {
                IllegalStateException("Unable to find hash for proto JAR for return types on ${clazz.name}")
            }
        val contractRef = Commons.ProvenanceReference.newBuilder().setHash(contractHash.getHash()).build()
        val protoRef = Commons.ProvenanceReference.newBuilder().setHash(protoHash.getHash()).build()

        val contractSpec = dehydrateSpec(clazz.kotlin, contractRef, protoRef)

        return Session.Builder(scope.scope.scopeSpecIdInfo.scopeSpecUuid.toUuid())
            .also { it.client = this } // TODO remove when class is moved over
            .setContractSpec(contractSpec)
            .setProvenanceReference(contractRef)
            .setScope(scope)
            .addParticipant(affiliate.partyType, affiliate.encryptionKeyRef.publicKey.toPublicKeyProto())
            .addDataAccessKeys(scope.scope.scope.dataAccessList.map { inner.affiliateRepository.getAffiliateKeysByAddress(it).encryptionPublicKey })
    }

    // executes the first session against a non-existent scope
    fun<T: P8eContract, S: P8eScopeSpecification> newSession(clazz: Class<T>, scopeSpecificationDef: Class<S>): Session.Builder {
        val contractHash = getContractHash(clazz)
        val protoHash = clazz.methods
            .find { Message::class.java.isAssignableFrom(it.returnType) }
            ?.returnType
            ?.let { getProtoHash(contractHash, it) }
            .orThrow {
                IllegalStateException("Unable to find hash for proto JAR for return types on ${clazz.name}")
            }
        val contractRef = Commons.ProvenanceReference.newBuilder().setHash(contractHash.getHash()).build()
        val protoRef = Commons.ProvenanceReference.newBuilder().setHash(protoHash.getHash()).build()

        val contractSpec = dehydrateSpec(clazz.kotlin, contractRef, protoRef)

        val scopeSpecAnnotation = scopeSpecificationDef.getAnnotation(ScopeSpecificationDefinition::class.java)

        requireNotNull(scopeSpecAnnotation) {
            "The annotation for the scope specifications must not be null"
        }

        return Session.Builder(scopeSpecAnnotation.uuid.toUuid())
            .also { it.client = this } // TODO remove when class is moved over
            .setContractSpec(contractSpec)
            .setProvenanceReference(contractRef)
            .addParticipant(affiliate.partyType, affiliate.encryptionKeyRef.publicKey.toPublicKeyProto())
    }

    fun execute(session: Session): ExecutionResult {
        val span = tracer.buildSpan("Execution").start().also { tracer.activateSpan(it) }
        val input = session.packageContract(inner.config.mainNet)
        log.debug("Contract name: ${input.contract.definition.name}")
        log.debug("Session Id: ${session.sessionUuid}")
        log.debug("Execution UUID: ${input.executionUuid}")

        val result = inner.contractEngine.handle(affiliate.encryptionKeyRef, affiliate.signingKeyRef, input, session.dataAccessKeys)

        val envelopeState = EnvelopeState.newBuilder()
            .setInput(input)
            .setResult(result)
            .build()

        return when (result.isSigned()) {
            true -> {
                SignedResult(envelopeState).also { signedResult ->
                    log.debug("Number of each type: ${signedResult.executionInfo.groupingBy { it.second }.eachCount()}")
                    log.debug("List of ID/Address ${signedResult.executionInfo.map { it.third + it.first }}")
                    log.trace("Full Content of TX Protos: ${signedResult.messages}")
                }
            }
            false -> FragmentResult(envelopeState)
        }.also {
            span.finish() }
    }

    fun execute(envelope: Envelope): ExecutionResult {
        // todo: should affiliateSharePublicKeys be an empty list in this non-invoking-party-execution-case?
        val result = inner.contractEngine.handle(affiliate.encryptionKeyRef, affiliate.signingKeyRef, envelope, listOf())

        val envelopeState = EnvelopeState.newBuilder()
            .setInput(envelope)
            .setResult(result)
            .build()

        return FragmentResult(envelopeState)
    }

    fun registerMailHandler(executor: ScheduledExecutorService, handler: MailHandlerFn): ScheduledFuture<*> =
        executor.scheduleAtFixedRate(PollAffiliateMailbox(
            inner.osClient.osClient,
            inner.mailboxService,
            signingKeyRef = affiliate.signingKeyRef,
            encryptionKeyRef = affiliate.encryptionKeyRef,
            maxResults = 100,
            inner.config.mainNet,
            handler
        ), 1, 1, TimeUnit.SECONDS)

    fun requestAffiliateExecution(envelopeState: EnvelopeState) {
        // todo: verify this Client instance's affiliate is on the envelope?

        if (envelopeState.result.isSigned()) {
            TODO("should we throw an exception or something in the event that an affiliate is requesting signatures on an already fully-signed envelope?")
        }

        inner.mailboxService.fragment(affiliate.encryptionKeyRef.publicKey, affiliate.signingKeyRef.signer(), envelopeState.input)
    }

    fun respondWithSignedResult(envelopeState: EnvelopeState) {
        inner.mailboxService.result(affiliate.encryptionKeyRef.publicKey, affiliate.signingKeyRef.signer(), envelopeState.result)
    }

    fun<T> hydrate(clazz: Class<T>, scope: ScopeResponse): T {
        val span = tracer.buildSpan("Hydration").start()
        tracer.activateSpan(span)
        scope.validateRecordsRequested()

        val constructor = clazz.declaredConstructors
            .filter {
                it.parameters.isNotEmpty() &&
                    it.parameters.all { param ->
                        Message::class.java.isAssignableFrom(param.type) &&
                            param.getAnnotation(Record::class.java) != null
                    }
            }
            .takeIf { it.isNotEmpty() }
            // TODO different error type?
            .orThrowContractDefinition("Unable to build POJO of type ${clazz.name} because not all constructor params implement ${Message::class.java.name} and have a \"Record\" annotation")
            .firstOrNull {
                it.parameters.any { param ->
                    scope.recordsList.any { wrapper ->
                        (wrapper.record.name == param.getAnnotation(Record::class.java)?.name &&
                            wrapper.record.resultType() == param.type.name)
                    }
                }
            }
            .orThrowContractDefinition("No constructor params have a matching record in scope ${scope.uuid()}")
        val params = constructor.parameters
            .map { it.getAnnotation(Record::class.java).name to it.type }
            .map { (name, type) ->
                // TODO change this to find or null and throw exception message
                scope.recordsList.first { wrapper ->
                    wrapper.record.name == name && wrapper.record.resultType() == type.name
                }.record to type
            }.map { (record, type) ->
                inner.osClient.getRecord(type.name, record.resultHash(), affiliate.encryptionKeyRef)
            }.map { it.get() }

        return clazz.cast(constructor.newInstance(*params.toList().toTypedArray())).also { span.finish() }
    }

    private fun <T: P8eContract> getContractHash(clazz: Class<T>): ContractHash {
        return contractHashes.find {
            it.getClasses()[clazz.name] == true
        }.orThrow { ContractBootstrapException("Unable to find ContractHash instance to match ${clazz.name}, please verify you are running a Provenance bootstrapped JAR.") }
    }

    private fun getProtoHash(contractHash: ContractHash, clazz: Class<*>): ProtoHash {
        return protoHashes.find {
            it.getUuid() == contractHash.getUuid() && it.getClasses()[clazz.name] == true
        }.orThrow { ContractBootstrapException("Unable to find ProtoHash instance to match ${clazz.name}, please verify you are running a Provenance bootstrapped JAR.") }
    }

    fun <T : Any, X : Throwable> T?.orThrow(supplier: () -> X) = this ?: throw supplier()
}
