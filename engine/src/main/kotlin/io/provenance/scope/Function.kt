package io.provenance.scope

import arrow.core.Either
import com.google.protobuf.Message
import io.provenance.scope.contract.proto.Commons.ProvenanceReference
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.contract.proto.Contracts.ProposedRecord
import io.provenance.scope.contract.proto.Contracts.ConsiderationProto
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.util.ProtoUtil
import kotlin.Function

class Function(
    private val encryptionKeyRef: KeyRef,
    private val osClient: CachedOsClient,
    private val contract: ContractWasm,
    val considerationBuilder: ConsiderationProto.Builder,
    val method: P8eFunction,
    private val records: List<RecordInstance>
): Function<Message> {
    // todo: implement skip if record exists in p8e-wasm
//    val skipBecauseRecordAlreadyExists: Boolean = method.getAnnotation(SkipIfRecordExists::class.java)?.let {
//        records.find { record -> record.name == it.name }
//    } != null

//    val returnType = method.returnType

    private val methodParameters = getFunctionParameters()

    fun canExecute(): Boolean {
        return methodParameters.size == method.parameters.size// && !skipBecauseRecordAlreadyExists // todo: implement skip if record exists in p8e-wasm
    }

    operator fun invoke(): Pair<ConsiderationProto.Builder, ByteArray> {
        return considerationBuilder to contract.callFunByName(method.name, *methodParameters.map { it.toByteArray() }.toTypedArray())
    }

    private fun getFunctionParameters(): List<Message> {
        val proposed = considerationBuilder.inputsList
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
        parameter: P8eFunctionParameter,
        records: List<RecordInstance>,
        proposed: List<RecordInstance>
    ): RecordInstance? {
        return if (parameter.type == RecordType.Existing.name) {
            records
        } else {
            proposed
        }.find {
            it.name == parameter.name
        }?.takeIf {
            parameter.type == it.clazz.name
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
