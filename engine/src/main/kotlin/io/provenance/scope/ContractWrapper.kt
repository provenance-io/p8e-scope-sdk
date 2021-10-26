package io.provenance.scope

import arrow.core.Either
import arrow.core.right
import com.google.protobuf.Message
import io.provenance.scope.contract.proto.Contracts.ExecutionResult
import io.provenance.scope.contract.proto.Contracts.Record
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.objectstore.util.orGet
import io.provenance.scope.objectstore.util.orThrow
import io.provenance.scope.util.orThrowContractDefinition
import io.provenance.scope.util.toOffsetDateTime
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Parameter

class ContractWrapper(
    private val contractSpecClass: Class<out Any>,
    private val encryptionKeyRef: KeyRef,
    private val definitionService: DefinitionService,
    private val osClient: CachedOsClient,
    private val contractBuilder: Contract.Builder,
) {
    private val records = buildRecords()

    val contractClass = definitionService.loadClass(
        contractBuilder.definition
    ).takeIf {
        contractSpecClass.isAssignableFrom(it)
    }.orThrowContractDefinition("Contract class ${contractBuilder.definition.resourceLocation.classname} must implement ${contractSpecClass.name}")
    .takeIf {
        P8eContract::class.java.isAssignableFrom(it)
    }.orThrowContractDefinition("Contract class ${contractBuilder.definition.resourceLocation.classname} must extend ${P8eContract::class.java.name}")

    private val constructor = getConstructor(contractClass)

    private val constructorParameters = getConstructorParameters(constructor, records).map { (it as Either.Left<Any>).value }

   // val constructorParam = if (constructorParameters.size > 0) arrayOf((constructorParameters.get(0).orGet { null } as Either.Left<Any>).value) else emptyArray()
    private val contract = (constructor.newInstance(*constructorParameters.toTypedArray()) as P8eContract)
        .also { it.currentTime.set(contractBuilder.startTime.toOffsetDateTime()) }

    val functions = contractBuilder.considerationsBuilderList
        .filter { it.result == ExecutionResult.getDefaultInstance() }
        .map { consideration -> consideration to getConsiderationMethod(contract.javaClass, consideration.considerationName) }
        .map { (consideration, method) -> Function(encryptionKeyRef, osClient, contract, consideration, method, records) }

    private fun getConstructor(
        clazz: Class<*>
    ): Constructor<*> =
        clazz.declaredConstructors
            .takeIf { it.size == 1 }
            ?.first()
            .orThrowContractDefinition("Class ${clazz.name} must have only one constructor.")
            .takeIf { it.parameters.all { parameter -> parameter.getAnnotation(io.provenance.scope.contract.annotations.Record::class.java) != null } }
            .orThrowContractDefinition("All constructor arguments for ${clazz.name} must have an @Record annotation ")

    private fun getConstructorParameters(
        constructor: Constructor<*>,
        records: List<RecordInstance>
    ): List<Any> =
        constructor.parameters
            .mapNotNull { getParameterRecord(it, records) }
            .map { it.messageOrCollection }

    private fun getParameterRecord(
        parameter: Parameter,
        records: List<RecordInstance>
    ): RecordInstance? {
        return records.find {
            parameter.getAnnotation(io.provenance.scope.contract.annotations.Record::class.java)?.name == it.name
        }
    }

    private fun getConsiderationMethod(
        contractClass: Class<*>,
        name: String
    ): Method {
        return contractClass.methods
            .filter { it.isAnnotationPresent(io.provenance.scope.contract.annotations.Function::class.java) }
            .find { it.name == name }
            .orThrowContractDefinition("Unable to find method on class ${contractClass.name} with annotation ${io.provenance.scope.contract.annotations.Function::class.java} with name $name")
    }

    private fun buildRecords(): List<RecordInstance> {
        return contractBuilder.inputsList
            .filter { it.dataLocation.ref.hash.isNotEmpty() }
            .toRecordInstance(encryptionKeyRef)
            .takeIf { records -> records.map { it.name }.toSet().size == records.size }
            .orThrowContractDefinition("Found duplicate record messages by name.")
    }

    private fun List<Record>.toRecordInstance(
        encryptionKeyRef: KeyRef
    ): List<RecordInstance> {
        val recordMap: Map<String, List<Message>> = groupByTo(mutableMapOf(), { it.name }) { record ->
            osClient.getRecord(
                record.dataLocation.classname,
                record.dataLocation.ref.hash.base64Decode(),
                encryptionKeyRef,
            )
        }.map { (name, futures) ->
            name to futures.map { it.get() }
        }.toMap()

        val records = recordMap.entries
            .filter { it.value.size == 1 }
            .flatMap { (name, messages) ->
                messages.map { message ->
                    RecordInstance(
                        name,
                        message.javaClass,
                        Either.Left(message)
                    )
                }
            }.toMutableList()

        recordMap.entries
            .filter { it.value.size > 1 }
            .map { (name, messages) ->
                RecordInstance(
                    name,
                    messages.first().javaClass,
                    Either.Right(messages)
                )
            }.let(records::addAll)
        return records
    }
}
