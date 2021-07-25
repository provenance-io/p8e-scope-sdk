package io.provenance.scope.sdk

import com.google.protobuf.Message
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.objectstore.client.OsClient

// TODO (@steve)
// add Client class that takes in a config
// how does signer fit in?

class Client(config: ClientConfig) {

    val osClient: CachedOsClient = CachedOsClient(config, OsClient(config.osGrpcUrl, config.osGrpcDeadlineMs))

    // TODO create repo for provenance bindings
    // add error handling and start with extensions present here
    // finish this function implementation
    // make resolver that can go from byte array to Message class

    fun<T> hydrate(clazz: Class<T>, scope: Scope): T {
        val constructor = clazz.declaredConstructors
            .filter {
                it.parameters.isNotEmpty() &&
                    it.parameters.all { param ->
                        Message::class.java.isAssignableFrom(param.type) &&
                            param.getAnnotation(Record::class.java) != null
                    }
            }
            .takeIf { it.isNotEmpty() }
            .orThrowContractDefinition("Unable to build POJO of type ${clazz.name} because not all constructor params implement ${Message::class.java.name} and have a \"Record\" annotation")
            .firstOrNull {
                it.parameters.any { param ->
                    scope.recordGroupList.flatMap { it.recordsList }.any { record ->
                        (record.resultName == param.getAnnotation(Fact::class.java)?.name &&
                            record.classname == param.type.name) ||
                            param.type == Scope::class.java
                    }
                }
            }
            .orThrowContractDefinition("No constructor params have a matching record in scope ${scope.uuid.value}")

        val params = constructor.parameters
            .map {
                it.getAnnotation(Fact::class.java)?.name to it.type
            }
            .map { (name, type) ->
                if (type == Scope::class.java) {
                    type to scope
                } else {
                    type to scope.recordGroupList
                        .flatMap { it.recordsList }
                        .find { record ->
                            (record.resultName == name &&
                                record.classname == type.name) ||
                                type == Scope::class.java
                        }
                }
            }.map { (type, record) ->
                when (record) {
                    is Record ->
                        completableFuture(executor) {
                            client.loadProto(
                                record.resultHash,
                                type.name
                            )
                        }
                    is Scope -> completableFuture(executor) { record }
                    else -> null
                }
            }

        return clazz.cast(constructor.newInstance(*params.map { it?.get() }.toList().toTypedArray()))
    }
}
