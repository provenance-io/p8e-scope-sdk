package io.provenance.scope.sdk

import io.provenance.scope.contract.annotations.Record as AnnotationRecord
import io.provenance.metadata.v1.*
import io.provenance.scope.contract.annotations.*
import io.provenance.scope.contract.proto.Commons
import io.provenance.scope.contract.proto.HelloWorldExample
import io.provenance.scope.proto.PK
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.contract.proto.TestContractProtos
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.encryption.util.toKeyPair
import io.provenance.scope.util.MetadataAddress
import io.provenance.scope.util.toByteString
import io.provenance.scope.util.toUuid
import java.net.URI
import java.util.*


val localKeys = listOf(
    "0A2100EF4A9391903BFE252CB240DA6695BC5F680A74A8E16BEBA003833DFE9B18C147",
    "0A2100CBEDAA6241122CB6B5BD2A3E1FFDD8694C0AEC16E80A0CC72B6256C56090F6FA",
    "0A21009B6CFD2525DD7EA500A3F2665047319A3D2A3F1D62177D686DF98713D8E52BDB",
).map { privateHex -> privateHex.toJavaPrivateKey().toKeyPair() }
val jarCacheSize = 20000L
val osGrpcDeadlineMs = 20000L
val specCacheSizeInBytes = 20000L
val recordCacheSizeInBytes = 20000L
val osGrpcUri = URI.create("https://localhost:5000")

fun createClientDummy(localKeyIndex: Int): Client {
    val encryptionKeyRef = DirectKeyRef(localKeys[localKeyIndex])
    val signingKeyRef = DirectKeyRef(localKeys[localKeyIndex + 1])
    val partyType = Specifications.PartyType.OWNER
    val affiliate = Affiliate(signingKeyRef, encryptionKeyRef, partyType)

    val clientConfig = ClientConfig(
        jarCacheSize,
        specCacheSizeInBytes,
        recordCacheSizeInBytes,
        osGrpcUri,
        osGrpcDeadlineMs,
        mainNet = false
    )
    return Client(SharedClient(clientConfig), affiliate)
}

fun createSessionBuilderNoRecords(client: Client, existingScope: ScopeResponse? = null): Session.Builder {
    val defSpec = Commons.DefinitionSpec.newBuilder()
        .setType(Commons.DefinitionSpec.Type.PROPOSED)
        .setResourceLocation(
            Commons.Location.newBuilder()
                .setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
        )
        .setName("record2")
    val conditionSpec = Specifications.ConditionSpec.newBuilder()
        .addInputSpecs(defSpec)
        .setFuncName("record2")
        .build()
    val participants = HashMap<Specifications.PartyType, PK.PublicKey>()
    participants[Specifications.PartyType.OWNER] = PK.PublicKey.newBuilder().build()
    val spec = Specifications.ContractSpec.newBuilder()
        .setDefinition(defSpec)
        .addConditionSpecs(conditionSpec)
        .addFunctionSpecs(
            Specifications.FunctionSpec.newBuilder()
                .setFuncName("record2")
                .addInputSpecs(defSpec)
                .setOutputSpec(Commons.OutputSpec.newBuilder().setSpec(defSpec))
                .setInvokerParty(Specifications.PartyType.OWNER)
        )
    if (existingScope != ScopeResponse.getDefaultInstance()) {
        spec.addInputSpecs(defSpec)
    }
    val provenanceReference = Commons.ProvenanceReference.newBuilder().build()
    var scopeSpecUuid: UUID
    if (!existingScope?.scope?.scopeSpecIdInfo?.scopeSpecUuid.isNullOrEmpty()) {
        scopeSpecUuid = existingScope?.scope?.scopeSpecIdInfo?.scopeSpecUuid!!.toUuid()
    } else {
        scopeSpecUuid = UUID.randomUUID()
    }
    return Session.Builder(scopeSpecUuid, client.inner.affiliateRepository)
        .setContractSpec(spec.build())
        .setProvenanceReference(provenanceReference)
        .setClient(client)
        .setSessionUuid(UUID.randomUUID())
        .apply {
            if (existingScope != null) {
                setScope(existingScope)
                addDataAccessKey(localKeys[1].public)
            }
        }
}

fun createExistingScope(affiliateRepository: AffiliateRepository = AffiliateRepository(false), ownerAddress: String? = null): ScopeResponse.Builder {
    val scopeUuid = UUID.randomUUID()
    val sessionUUID = UUID.randomUUID()
    val specificationUUID = UUID.randomUUID()
    val scopeRecord = Record.newBuilder()
        .addInputs(
            RecordInput.newBuilder()
                .setName("record2")
                .setHash("M8PWxG2TFfO0YzL3sDW/l9kX325y+3v+5liGcjZoi4Q=")
                .setStatus(RecordInputStatus.RECORD_INPUT_STATUS_PROPOSED)
        )
        .addOutputs(RecordOutput.newBuilder().setHash("M8PWxG2TFfO0YzL3sDW/l9kX325y+3v+5liGcjZoi4Q=").build())
        .setProcess(
            Process.newBuilder().setName("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName").build()
        )
        .setSessionId(MetadataAddress.forSession(scopeUuid, sessionUUID).bytes.toByteString())
        .setName("record2")
    val recordWrapper = RecordWrapper.newBuilder().setRecord(scopeRecord).build()
    val ownerKey = ProvenanceKeyGenerator.generateKeyPair()
    affiliateRepository.addAffiliate(ownerKey.public, ownerKey.public)
    val scope = Scope.newBuilder()
        .addDataAccess(localKeys[1].public.getAddress(false))
        .addOwners(
            Party.newBuilder()
            .setRole(PartyType.PARTY_TYPE_OWNER)
            .apply {
                ownerAddress?.let { setAddress(it) }
                    ?: setAddress(ownerKey.public.getAddress(false))
            }
        )
        .setScopeId(MetadataAddress.forScope(scopeUuid).bytes.toByteString())
        .setValueOwnerAddress("ownerAddress")
        .setSpecificationId(MetadataAddress.forScopeSpecification(specificationUUID).bytes.toByteString())
        .build()
    val scopeWrapper = ScopeWrapper.newBuilder()
        .setScope(scope)
        .build()
    val scopeResponseBuilder = ScopeResponse.newBuilder()
        .setScope(scopeWrapper)
        .addRecords(recordWrapper)
        .apply {
            scopeBuilder.scopeIdInfoBuilder.setScopeUuid(scopeUuid.toString())
            scopeBuilder.scopeSpecIdInfoBuilder.setScopeSpecUuid(specificationUUID.toString())

            requestBuilder
                .setIncludeRecords(true)
                .setIncludeSessions(true)
        }
    return scopeResponseBuilder

}

data class HelloWorldData(@AnnotationRecord(name = "record2") val name: HelloWorldExample.ExampleName) {}
data class HelloWorldDataNullable(
    @AnnotationRecord(name = "record2") val name: HelloWorldExample.ExampleName,
    @AnnotationRecord(name = "nullableRecord") val nullableRecord: TestContractProtos.TestProto?
) {}


