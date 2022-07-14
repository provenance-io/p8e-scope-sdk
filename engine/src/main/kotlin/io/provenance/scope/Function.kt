package io.provenance.scope

import arrow.core.Either
import com.google.protobuf.Message
import io.provenance.scope.contract.annotations.Input
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.SkipIfRecordExists
import io.provenance.scope.contract.proto.Commons.ProvenanceReference
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.contract.proto.Contracts.ProposedRecord
import io.provenance.scope.contract.proto.Contracts.ConsiderationProto
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.util.ContractDefinitionException
import io.provenance.scope.util.ProtoUtil
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import kotlin.Function

class Function<T: P8eContract>(
    private val encryptionKeyRef: KeyRef,
    osClient: CachedOsClient,
    private val contract: T,
    val considerationBuilder: ConsiderationProto.Builder,
    val method: Method,
    records: List<RecordInstance>
): Function<Message> {

    val fact = method.getAnnotation(Record::class.java)
        ?: throw ContractDefinitionException("${contract.javaClass.name}.${method.name} must have the ${Record::class.java.name} annotation.")

    val skipBecauseRecordAlreadyExists: Boolean = method.getAnnotation(SkipIfRecordExists::class.java)?.let {
        records.find { record -> record.name == it.name }
    } != null

    val returnType = method.returnType

    private val methodParameters = getFunctionParameters(
        encryptionKeyRef,
        considerationBuilder,
        method,
        records,
        osClient
    )

    fun canExecute(): Boolean {
        return methodParameters.size == method.parameters.size && !skipBecauseRecordAlreadyExists
    }

    operator fun invoke(): Pair<ConsiderationProto.Builder, Message?> {
        return considerationBuilder to method.invoke(contract, *methodParameters.toTypedArray()) as? Message
    }

    private fun getFunctionParameters(
        encryptionKeyRef: KeyRef,
        considerationProto: ConsiderationProto.Builder,
        method: Method,
        records: List<RecordInstance>,
        osClient: CachedOsClient
    ): List<Message> {
        val proposed = considerationProto.inputsList
            .map { proposedFact ->
                val contractSpec = proposedFact.let(::proposedRecordToDef)
                val message = osClient.getRecord(
                    contractSpec.resourceLocation.classname,
                    contractSpec.resourceLocation.ref.hash.base64Decode(),
//                    signaturePublicKey = signer.getPublicKey() // todo: was optional in DefinitionService.loadProto (via DefintionService.get)... is this every necessary and does it need to be accounted for in CachedOsClient?
                    encryptionKeyRef,
                ).get()
                RecordInstance(
                    proposedFact.name,
                    message.javaClass,
                    Either.Left(message)
                )
            }
        return method.parameters.mapNotNull { parameter ->
            getFunctionParameterFact(
                parameter,
                records,
                proposed
            )
        }.map { (it.messageOrCollection as Either.Left).value }
    }

    private fun getFunctionParameterFact(
        parameter: Parameter,
        records: List<RecordInstance>,
        proposed: List<RecordInstance>
    ): RecordInstance? {
        val record = parameter.getAnnotation(Record::class.java)
        val input = parameter.getAnnotation(Input::class.java)
        if (record != null && input != null ||
            record == null && input == null) {
            throw ContractDefinitionException("Method parameter ${parameter.name} of type ${parameter.type.name} must have only one of (${Record::class.java.name}|${Input::class.java.name}) annotations")
        }
        return if (record != null) {
            records.find {
                it.name == record.name
            }
        } else {
            proposed.find {
                it.name == input.name
            }
        }?.takeIf {
            parameter.type == it.clazz
        }
    }

    private fun proposedRecordToDef(proposedRecord: ProposedRecord) =
        proposedRecord.let {
            ProtoUtil.defSpecBuilderOf(
                it.name,
                ProtoUtil.locationBuilderOf(
                    proposedRecord.classname,
                    ProvenanceReference.newBuilder().setHash(proposedRecord.hash).build()
                ),
                DefinitionSpec.Type.FACT).build()
        }
}
