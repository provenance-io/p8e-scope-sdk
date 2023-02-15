package io.provenance.scope

import arrow.core.Either
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.protobuf.Message
import io.provenance.scope.contract.proto.Contracts.ExecutionResult
import io.provenance.scope.contract.proto.Contracts.Record
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.util.orThrowContractDefinition
import java.lang.reflect.Parameter

class ContractWrapper(
    contractBytes: ByteArray,
    private val encryptionKeyRef: KeyRef,
    private val osClient: CachedOsClient,
    private val contractBuilder: Contract.Builder,
    private val disableContractLogs: Boolean = true,
) {
    private val records = buildRecords()

    // todo: bring configurable logging back to contracts? (via some sort of wasm log import)
//    private fun <T> withConfigurableLogging(block: () -> T): T = if (disableContractLogs) {
//        withoutLogging(block)
//    } else {
//        block()
//    }

    val contract = ContractWasm(contractBytes)

    val functions = contractBuilder.considerationsBuilderList
        .filter { it.result == ExecutionResult.getDefaultInstance() }
        .map { consideration -> consideration to getConsiderationMethod(consideration.considerationName) }
        .map { (consideration, method) -> Function(encryptionKeyRef, osClient, contract, consideration, method, records) }

    private fun getParameterRecord(
        parameter: Parameter,
        records: List<RecordInstance>
    ): Pair<io.provenance.scope.contract.annotations.Record, RecordInstance?> {
        val recordAnnotation = parameter.getAnnotation(io.provenance.scope.contract.annotations.Record::class.java)
        return recordAnnotation to records.find {
            recordAnnotation?.name == it.name
        }
    }

    private fun getConsiderationMethod(
        name: String
    ): P8eFunction {
        return contract.structure.functions
            .find { it.name == name }
            .orThrowContractDefinition("Unable to find method on class ${contract.structure.name} with name $name")
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
