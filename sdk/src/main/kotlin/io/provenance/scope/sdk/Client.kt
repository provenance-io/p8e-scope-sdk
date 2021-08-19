package io.provenance.scope.sdk

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.ContractEngine
import io.provenance.metadata.v1.Session as SessionProto
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition
import io.provenance.scope.contract.contracts.ContractHash
import io.provenance.scope.contract.proto.Commons
import io.provenance.scope.contract.proto.ProtoHash
import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification
import io.provenance.scope.encryption.crypto.SignerFactory
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.sdk.ContractSpecMapper.dehydrateSpec
import io.provenance.scope.sdk.ContractSpecMapper.orThrowContractDefinition
import io.provenance.scope.sdk.extensions.isSigned
import io.provenance.scope.sdk.extensions.resultHash
import io.provenance.scope.sdk.extensions.resultType
import io.provenance.scope.sdk.extensions.uuid
import io.provenance.scope.util.toUuidProv
import java.io.Closeable
import java.util.ServiceLoader
import java.security.PublicKey

// TODO (@steve)
// how does signer fit in?
// support multiple size hashes in the sdk - object-store should already support hashes of any length

class SharedClient(val config: ClientConfig, val signerFactory: SignerFactory = SignerFactory()) : Closeable {
    val osClient: CachedOsClient = CachedOsClient(OsClient(config.osGrpcUrl, config.osGrpcDeadlineMs), config.osDecryptionWorkerThreads, config.osConcurrencySize, config.cacheRecordSizeInBytes)
    
    val contractEngine: ContractEngine = ContractEngine(osClient, signerFactory)

    override fun close() {
        TODO("Implement Closeable and close osClient channel - needs to shutdown and wait for shutdown or timeout")
    }
}

class Client(val inner: SharedClient, val affiliate: Affiliate) {

    companion object {
        // TODO add a set of affiliates here - every time we create a new Client we should add to it and verify the new affiliate is unique
        private val contractHashes = ServiceLoader.load(ContractHash::class.java).toList() // todo: can we use the contract/proto hashes to generate a dynamic list of what should/should not be loaded from memory vs. system class loader
        private val protoHashes = ServiceLoader.load(ProtoHash::class.java).toList()
    }

    // TODO
    // add error handling and start with extensions present here
    // finish this function implementation
    // make resolver that can go from byte array to Message class

    fun<T: P8eContract> newSession(clazz: Class<T>, scope: ScopeResponse, session: SessionProto): Session.Builder {
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

        return Session.Builder(scope.scope.scopeSpecIdInfo.scopeSpecUuid.toUuidProv())
            .also { it.client = this } // TODO remove when class is moved over
            .setContractSpec(contractSpec)
            .setProvenanceReference(contractRef)
            .setProposedSession(session)
            .setScope(scope)
            .addParticipant(affiliate.partyType, affiliate.encryptionKeyRef.publicKey.toPublicKeyProtoOS())
    }

    fun java.security.PublicKey.toPublicKeyProtoOS(): PublicKeys.PublicKey =
        PublicKeys.PublicKey.newBuilder()
            .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(this)))
            .build()

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

        return Session.Builder(scopeSpecAnnotation.uuid.toUuidProv())
            .also { it.client = this } // TODO remove when class is moved over
            .setContractSpec(contractSpec)
            .setProvenanceReference(contractRef)
            .addParticipant(affiliate.partyType, affiliate.encryptionKeyRef.publicKey.toPublicKeyProtoOS())
    }

    fun execute(session: Session, affiliateSharePublicKeys: Collection<PublicKey> = listOf()): ExecutionResult {
        val input = session.packageContract()
        val result = inner.contractEngine.handle(affiliate.encryptionKeyRef, affiliate.signingKeyRef, input, session.scope, affiliateSharePublicKeys)

        return when (result.isSigned(session.scope, inner.config.mainNet)) {
            true -> SignedResult(session.scopeUuid, session.scopeSpecUuid, session.sessionUuid, result, inner.config.mainNet) // todo: better way to get the scope/session, we will always need some minimal info for creating a new scope if not existant
            false -> throw NotImplementedError("Multi-party contract support not yet implemented")
        }
    }

    fun<T> hydrate(clazz: Class<T>, scope: ScopeResponse): T {
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

        return clazz.cast(constructor.newInstance(*params.toList().toTypedArray()))
    }

    private fun <T: P8eContract> getContractHash(clazz: Class<T>): ContractHash {
        return contractHashes.find {
            it.getClasses()[clazz.name] == true
        }.orThrow { IllegalStateException("Unable to find ContractHash instance to match ${clazz.name}, please verify you are running a Provenance bootstrapped JAR.") }
    }

    private fun getProtoHash(contractHash: ContractHash, clazz: Class<*>): ProtoHash {
        return protoHashes.find {
            it.getUuid() == contractHash.getUuid() && it.getClasses()[clazz.name] == true
        }.orThrow { IllegalStateException("Unable to find ProtoHash instance to match ${clazz.name}, please verify you are running a Provenance bootstrapped JAR.") }
    }

    fun <T : Any, X : Throwable> T?.orThrow(supplier: () -> X) = this ?: throw supplier()
}
