package io.provenance.scope.sdk

import com.google.protobuf.Message
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.metadata.v1.ScopeSpecification
import io.provenance.metadata.v1.Session
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.sdk.ContractSpecMapper.orThrowContractDefinition
import io.provenance.scope.sdk.extensions.resultHash
import io.provenance.scope.sdk.extensions.resultType
import io.provenance.scope.sdk.extensions.uuid

// TODO (@steve)
// add Client class that takes in a config
// how does signer fit in?

// TODO pair with Wyatt
// add notion of keypair/signer for client
// - java key pair
// - smart key reference
// add config for that keypair such as invoker


class Client(config: ClientConfig, val affiliate: Affiliate) {

    // TODO make this a singleton shared across Clients
    val osClient: CachedOsClient = CachedOsClient(config, OsClient(config.osGrpcUrl, config.osGrpcDeadlineMs))

    // TODO
    // add error handling and start with extensions present here
    // finish this function implementation
    // make resolver that can go from byte array to Message class

    // TODO return type of both of these will be a Builder that accepts functions like .addRecord(...) / .addProposedRecord(...)
    // contractManager.newContract(...).addProposedFact(...)
    // executes a new session against an existing scope
    fun<T: P8eContract> newSession(clazz: Class<T>, scope: ScopeResponse, session: Session) {
        // dehydrate clazz into ContractSpec
        // return some class like what Contract.kt used to do
        // SessionBuilder
        // addProposedSession()
        // addParticipant()
        // this function will .addParticipant() for affiliate.partyType
    }

    // executes the first session against a non-existent scope
    fun<T: P8eContract> newSession(clazz: Class<T>, scopeSpecification: ScopeSpecification, session: Session) {

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
                osClient.getRecord(type.name, record.resultHash(), affiliate.encryptionKeyRef)
            }

        return clazz.cast(constructor.newInstance(*params.toList().toTypedArray()))
    }
}
