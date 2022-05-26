package io.provenance.scope.sdk

import com.google.common.hash.Hashing
import com.google.protobuf.Message
import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.Input
import io.provenance.scope.contract.annotations.Participants
import io.provenance.scope.contract.annotations.ScopeSpecification
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.contract.proto.Commons.DefinitionSpec.Type.FACT
import io.provenance.scope.contract.proto.Commons.DefinitionSpec.Type.FACT_LIST
import io.provenance.scope.contract.proto.Commons.DefinitionSpec.Type.PROPOSED
import io.provenance.scope.contract.proto.Commons.Location
import io.provenance.scope.contract.proto.Commons.ProvenanceReference
import io.provenance.scope.contract.proto.Specifications.ConditionSpec
import io.provenance.scope.contract.proto.Specifications.FunctionSpec
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Contracts.ConditionProto
import io.provenance.scope.contract.proto.Contracts.ConsiderationProto
import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.objectstore.util.sha256LoBytes
import io.provenance.scope.util.ProtoUtil
import io.provenance.scope.util.base64String
import io.provenance.scope.contract.proto.Contracts.Record as RecordProto
import java.lang.reflect.ParameterizedType
import java.util.Base64
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmName

object ContractSpecMapper {

    fun ContractSpec.newContract(): Contract.Builder =
        Contract.newBuilder()
            .setSpec(
                RecordProto.newBuilder()
                    .setName(this.definition.name)
                    .setDataLocation(
                        Location.newBuilder()
                            .setClassname(ContractSpec::class.java.name)
                            .setRef(
                                ProvenanceReference.newBuilder()
                                    .setHash(toByteArray().sha256LoBytes().base64String())
                            )
                    )
            )
            .addAllInputs(this.inputSpecsList.map { it.newFact().build() })
            .addAllConditions(this.conditionSpecsList.map { it.newConditionProto().build() })
            .addAllConsiderations(this.functionSpecsList.map { it.newConsiderationProto().build() })
            .addAllRecitals(this.partiesInvolvedList.map { it.newRecital().build() })

    fun ConditionSpec.newConditionProto() =
        ConditionProto.newBuilder()
            .setConditionName(this.funcName)

    fun FunctionSpec.newConsiderationProto() =
        ConsiderationProto.newBuilder()
            .setConsiderationName(this.funcName)

    fun DefinitionSpec.newFact() =
        RecordProto.newBuilder()
            .setName(this.name)

    fun PartyType.newRecital() =
        Contracts.Recital.newBuilder()
            .setSignerRole(this)

    fun findRecital(clazz: KClass<out P8eContract>) = clazz.findAnnotation<Participants>()

    fun dehydrateSpec(
        clazz: KClass<out P8eContract>,
        contractRef: ProvenanceReference,
        protoRef: ProvenanceReference
    ): ContractSpec {

        // Verify that the contract is valid by checking for the appropriate java interface.
        clazz.isSubclassOf(P8eContract::class)
            .orThrowContractDefinition("Contract class ${clazz::class.java.name} is not a subclass of P8eContract")

        val scopeSpecifications = clazz.annotations
            .filter { it is ScopeSpecification }
            .map { it as ScopeSpecification }
            .flatMap { it.names.toList() }
            .takeUnless { it.isEmpty() }
            .orThrowContractDefinition("Class requires a ScopeSpecification annotation")

        val spec = ContractSpec.newBuilder()

        with(ProtoUtil) {
            spec.definition = defSpecBuilderOf(
                clazz.simpleName!!,
                locationBuilderOf(
                    clazz.jvmName,
                    contractRef
                ),
                FACT
            )
                .build()
        }

        clazz.constructors
            .takeIf { it.size == 1 }
            .orThrowContractDefinition("No constructor found, or more than one constructor identified")
            .first()
            .valueParameters
            .forEach { param ->
                val factAnnotation = param.findAnnotation<Record>()
                    .orThrowContractDefinition("Constructor param(${param.name}) is missing @Record annotation")

                with(ProtoUtil) {
                    if (List::class == param.type.classifier) {
                        val erasedType = (param.type.javaType as ParameterizedType)
                            .actualTypeArguments[0]
                            .let { it as Class<*> }
                            .takeIf {
                                Message::class.java.isAssignableFrom(it)
                            }
                            .orThrowContractDefinition("Constructor parameter of type List<T> must have a type T that implements ${Message::class.java.name}")

                        spec.addInputSpecs(
                            defSpecBuilderOf(
                                factAnnotation.name,
                                locationBuilderOf(
                                    erasedType.name,
                                    protoRef
                                ),
                                FACT_LIST,
                                optional = factAnnotation.optional
                            )
                        )
                    } else {
                        spec.addInputSpecs(
                            defSpecBuilderOf(
                                factAnnotation.name,
                                locationBuilderOf(
                                    param.type.javaType.typeName,
                                    protoRef
                                ),
                                FACT,
                                optional = factAnnotation.optional
                            )
                        )
                    }
                }
            }

        // Add the recital to the contract spec.
        clazz.annotations
            .filter { it is Participants }
            .map { it as Participants }
            .flatMap { it.roles.toList() }
            .let(spec::addAllPartiesInvolved)

        clazz.functions
            .filter { it.findAnnotation<Function>() != null }
            .map { func ->
                buildFunctionSpec(protoRef, func)
            }.let {
                spec.addAllFunctionSpecs(it)
            }

        return spec.build()
    }

    private fun buildFunctionSpec(
        protoRef: ProvenanceReference,
        func: KFunction<*>
    ): FunctionSpec {
        val function = FunctionSpec.newBuilder()
        function.funcName = func.name

        val considerationDecorator = func.findAnnotation<Function>()
            .orThrowNotFound("Function Annotation not found on ${func}")
        function.invokerParty = considerationDecorator.invokedBy

        func.valueParameters
            .forEach { param ->
                param.findAnnotation<Record>()
                    ?.also {
                        with(ProtoUtil) {
                            function.addInputSpecs(
                                defSpecBuilderOf(
                                    it.name,
                                    locationBuilderOf(
                                        param.type.javaType.typeName,
                                        protoRef
                                    ),
                                    FACT
                                )
                            )
                        }
                    } ?: param.findAnnotation<Input>()
                    .orThrowNotFound("No @Input or @Record Found for Param ${param}")
                    .also {
                        with(ProtoUtil) {
                            function.addInputSpecs(
                                defSpecBuilderOf(
                                    it.name,
                                    locationBuilderOf(
                                        param.type.javaType.typeName,
                                        protoRef
                                    ),
                                    PROPOSED
                                )
                            )
                        }
                    }
            }

        func.findAnnotation<Record>()
            .orThrowNotFound("No @Record Found for Function ${func}")
            .also { fact ->
                with(ProtoUtil) {
                    function.setOutputSpec(
                        outputSpecBuilderOf(
                            defSpecBuilderOf(
                                fact.name,
                                locationBuilderOf(
                                    func.returnType.javaType.typeName,
                                    protoRef
                                ),
                                PROPOSED
                            )
                        )
                    )
                }
            }

        return function.build()
    }
    fun <T : Any> T?.orThrowNotFound(message: String) = this ?: throw NotFoundException(message)
    fun <T : Any> T?.orThrowContractDefinition(message: String) = this ?: throw ContractDefinitionException(message)
    class NotFoundException(message: String) : RuntimeException(message)
    class ContractDefinitionException(message: String): Exception(message)
}
