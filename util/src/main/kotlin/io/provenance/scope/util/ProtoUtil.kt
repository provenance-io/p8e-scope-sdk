package io.provenance.scope.util

import com.google.protobuf.Message
import com.google.protobuf.Message.Builder
import com.google.protobuf.util.JsonFormat
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.contract.proto.Commons.DefinitionSpec.Type
import io.provenance.scope.contract.proto.Commons.Location
import io.provenance.scope.contract.proto.Commons.OutputSpec
import io.provenance.scope.contract.proto.Commons.ProvenanceReference
import io.provenance.scope.contract.proto.Contracts.ProposedRecord
import io.provenance.scope.contract.proto.Utils
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.staticFunctions

object ProtoJsonUtil {
    val messageCache = mutableMapOf<KClass<out Message>, KFunction<*>>()
    inline fun <reified T: Message> String.toMessage() =
        (messageCache.computeIfAbsent(T::class) {
            T::class.staticFunctions.find { it.name == "newBuilder" && it.parameters.size == 0 }
                ?: throw IllegalStateException("Unable to find newBuilder function on ${T::class.java.name}")
        }.call() as Builder)
            .let {
                JsonFormat.parser().merge(this, it)
                it.build() as T
            }

    fun Message.toJson() = JsonFormat.printer().print(this)
}

object ProtoUtil {
    fun defSpecBuilderOf(name: String, location: Location.Builder, type: Type): DefinitionSpec.Builder =
        DefinitionSpec.newBuilder()
            .setName(name)
            .setType(type)
            .setResourceLocation(
                location
            )

    fun outputSpecBuilderOf(
        defSpec: DefinitionSpec.Builder
    ): OutputSpec.Builder {
        return OutputSpec.newBuilder()
            .setSpec(defSpec)
    }

    fun provenanceReferenceOf(scopeUuid: UUID, contractUuid: UUID, hash: String) =
        ProvenanceReference.newBuilder()
            .setScopeUuid(Utils.UUID.newBuilder().setValue(scopeUuid.toString()).build())
            .setGroupUuid(Utils.UUID.newBuilder().setValue(contractUuid.toString()).build())
            .setHash(hash)


    fun locationBuilderOf(classname: String, ref: ProvenanceReference) =
        Location.newBuilder()
            .setRef(ref)
            .setClassname(classname)

//    outputDef.name,
//    String(objectWithItem.obj.unencryptedSha512.base64Encode()),
//    message.javaClass.name,
//    scope?.uuid?.toUuid(),
//    ancestorHash
    fun proposedRecordOf(name: String, hash: String, classname: String, scopeUuid: UUID? = null, ancestorHash: String? = null) =
        ProposedRecord.newBuilder()
            .setClassname(classname)
            .setName(name)
            .setHash(hash)
            .apply {
                if (ancestorHash != null && scopeUuid != null) {
                    setAncestor(
                        ProvenanceReference.newBuilder()
                            .setScopeUuid(Utils.UUID.newBuilder().setValue(scopeUuid.toString()).build())
                            .setHash(ancestorHash)
                            .setName(name)
                    )
                }
            }
}
