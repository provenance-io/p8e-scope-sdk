package io.provenance.scope.sdk

import com.google.common.util.concurrent.Futures
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

/**
 * A base client containing configuration (object store location, cache sizes, etc.) and shared services that can be
 * used by [Clients][Client] for various individual affiliates.
 *
 * Note: This class implements [Closeable] and should be properly closed when no longer in use
 */
class SharedClient(val config: ClientConfig) : Closeable {
    /**
     * A client for communcation with the Object Store
     */
    val osClient: CachedOsClient = CachedOsClient(OsClient(config.osGrpcUrl, config.osGrpcDeadlineMs), config.osDecryptionWorkerThreads, config.osConcurrencySize, config.cacheRecordSizeInBytes)

    /** @suppress */
    val contractEngine: ContractEngine = ContractEngine(osClient)

    /**
     * A registry of all other affiliates (identified by signing and encryption public keys) that you interact with in contract execution.
     * Used to look up appropriate public keys based on blockchain account addresses.
     */
    val affiliateRepository = AffiliateRepository(config.mainNet)

    /** @suppress */
    val mailboxService = MailboxService(osClient.osClient, affiliateRepository)

    override fun close() {
        osClient.osClient.close()
    }

    /**
     * Wait for all resources to properly close and be cleaned up
     *
     * @param [timeout] the timeout value, after which to give up on waiting
     * @param [unit] the time unit corresponding to the [timeout] value
     *
     * @return whether the resources were fully terminated
     */
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return osClient.osClient.awaitTermination(timeout, unit)
    }
}

/**
 * An SDK Client for interacting with contracts as a particular affiliate.
 *
 * @property [inner] the base [SharedClient] instance containing configuration and shared resources
 * @property [affiliate] an object representing an affiliate with the appropriate keys for signing/encryption and the role of this affiliate on contracts
 */
class Client(val inner: SharedClient, val affiliate: Affiliate) {

    private val log = LoggerFactory.getLogger(this::class.java);
    private val tracer = GlobalTracer.get()

    /**
     * The [ProtoIndexer] utility class to use for generating an indexable map of record values from a scope
     * according to the indexing behavior defined on the contract proto messages.
     */
    val indexer: ProtoIndexer = ProtoIndexer(inner.osClient, inner.config.mainNet, affiliate)

    companion object {
        // TODO add a set of affiliates here - every time we create a new Client we should add to it and verify the new affiliate is unique
        private val contractHashes = ServiceLoader.load(ContractHash::class.java).toList() // todo: can we use the contract/proto hashes to generate a dynamic list of what should/should not be loaded from memory vs. system class loader
        private val protoHashes = ServiceLoader.load(ProtoHash::class.java).toList()
    }

    /**
     * Create a new contract session against an existing scope on chain.
     *
     * @param [clazz] the contract class to run in this session
     * @param [scope] the [ScopeResponse] queried from chain. Note: this must include all sessions/records on the scope
     *
     * @return a [Session Builder][Session.Builder] object allowing you to set proposed record (Input) values, contract participants and
     *          various properties on the session before proceeding to execution.
     */
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
            .addParticipant(affiliate.partyType, affiliate.signingKeyRef.publicKey, affiliate.encryptionKeyRef.publicKey)
            .addDataAccessKeys(scope.scope.scope.dataAccessList.map { inner.affiliateRepository.getAffiliateKeysByAddress(it).encryptionPublicKey })
    }

    /**
     * Create a new contract session against a new scope. Both the provided contract and scope specification must have been
     * properly bootstrapped in order for the messages produced by executing this session to be accepted by the Provenance chain.
     *
     * @param [clazz] the contract class to run in this session
     * @param [scopeSpecificationDef] the [P8eScopeSpecification] class annotated with the [ScopeSpecificationDefinition]
     *          annotation. Note: In order for this session to be written/accepted by the Provenance chain, the contract class
     *          must be allowed to run against this scope specification via the [ScopeSpecification][io.provenance.scope.contract.annotations.ScopeSpecification] annotation.
     *
     * @return a [Session Builder][Session.Builder] object allowing you to set proposed record (Input) values, contract participants and
     *          various properties on the session before proceeding to execution.
     */
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
            .addParticipant(affiliate.partyType, affiliate.signingKeyRef.publicKey, affiliate.encryptionKeyRef.publicKey)
    }

    /**
     * Execute a built contract [Session], storing the results of executed functions in Object Store.
     *
     * This will actually run the contract code, executing functions for this affiliate's role that have all necessary arguments present.
     *
     * @param [session] the [Session] object containing details about the contract, session and scope to execute, including
     *          all proposed records (inputs to functions)
     *
     * @return an [ExecutionResult] either a
     * [SignedResult] consisting of Provenance Proto Messages to submit to chain in a transaction, or a
     * [FragmentResult] with the results of execution in order to persist and notify other parties of a request to sign.
     */
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

    /**
     * Execute a contract based on an envelope received from another party. Only execute the envelope if you have inspected
     * its contents and approve of the proposed inputs/function results
     *
     * @param [envelope] the envelope received for execution.
     *
     * @return a [FragmentResult] containing the result of the execution, to be mailed back to the invoking affiliate for memorialization
     */
    fun execute(envelope: Envelope): ExecutionResult {
        // todo: should affiliateSharePublicKeys be an empty list in this non-invoking-party-execution-case?
        val result = inner.contractEngine.handle(affiliate.encryptionKeyRef, affiliate.signingKeyRef, envelope, listOf())

        val envelopeState = EnvelopeState.newBuilder()
            .setInput(envelope)
            .setResult(result)
            .build()

        return FragmentResult(envelopeState)
    }

    /**
     * Register a mail handler to process incoming mail from other affiliates.
     *
     * @param [executor] a [ScheduledExecutorService] on which to poll for mail
     * @param [handler] a [MailHandlerFn] to receive incoming mail for this client's affiliate
     *
     * @return a [ScheduledFuture] that can be used to cancel the scheduled polling for this handler
     */
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

    /**
     * Submit an envelope to other contract parties for execution
     *
     * @param [envelopeState] the [EnvelopeState] object from an execution [FragmentResult] for mailing
     */
    fun requestAffiliateExecution(envelopeState: EnvelopeState) {
        // todo: verify this Client instance's affiliate is on the envelope?

        if (envelopeState.result.isSigned()) {
            TODO("should we throw an exception or something in the event that an affiliate is requesting signatures on an already fully-signed envelope?")
        }

        inner.mailboxService.fragment(affiliate.encryptionKeyRef.publicKey, affiliate.signingKeyRef.signer(), envelopeState.input)
    }

    /**
     * Return an envelope to the invoking party with the result of execution
     *
     * @param [envelopeState] the result of executing an incoming envelope
     */
    fun respondWithSignedResult(envelopeState: EnvelopeState) {
        inner.mailboxService.result(affiliate.encryptionKeyRef.publicKey, affiliate.signingKeyRef.signer(), envelopeState.result)
    }

    /**
     * Hydrate records from a scope into a class
     *
     * @param [clazz] the class to hydrate records into. This class *must* have a constructor with at least one parameter
     *          where each parameter is annotated with the [Record] annotation specifying which scope record
     *          to populate the parameter with and a type that is a [proto Message][Message] that matches the type of the record
     * @param [scope] the [ScopeResponse] to hydrate hashes from into their respective concrete types in the provided [clazz]
     *
     * @return the hydrated [clazz] of type [T]
     */
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
                scope.recordsList.firstOrNull { wrapper ->
                    wrapper.record.name == name && wrapper.record.resultType() == type.name
                }?.record to type
            }.map { (record, type) ->
                when (record) {
                    null -> Futures.immediateFuture(record)
                    else -> inner.osClient.getRecord(type.name, record.resultHash(), affiliate.encryptionKeyRef)
                }
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

    private fun <T : Any, X : Throwable> T?.orThrow(supplier: () -> X) = this ?: throw supplier()
}
