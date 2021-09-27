package io.provenance.scope.sdk

import io.provenance.scope.contract.annotations.Record as AnRecord
import io.provenance.metadata.v1.*
import io.provenance.scope.contract.annotations.*
import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.ScopeSpecification
import io.provenance.scope.contract.proto.Commons
import io.provenance.scope.contract.proto.HelloWorldExample
import io.provenance.scope.proto.PK
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.encryption.util.toJavaPublicKey
import io.provenance.scope.util.MetadataAddress
import io.provenance.scope.util.toByteString
import io.provenance.scope.util.toUuid
import java.net.URI
import java.security.KeyPair
import java.util.*


val localKeys = listOf(
    "0A41046C57E9E25101D5E553AE003E2F79025E389B51495607C796B4E95C0A94001FBC24D84CD0780819612529B803E8AD0A397F474C965D957D33DD64E642B756FBC4" to "0A2071E487C3FB00642F1863D57749F32D94F13892FA68D02049F7EA9E8FC58D6E63",
    "0A4104D630032378D56229DD20D08DBCC6D31F44A07D98175966F5D32CD2189FD748831FCB49266124362E56CC1FAF2AA0D3F362BF84CACBC1C0C74945041EB7327D54" to "0A2077170DEDCB6CFEDCE5A19BC8F0CD408A254F1E48A7350FC2E9019F50AE52524F",
    "0A4104CD5F4ACFFE72D323CCCB2D784847089BBD80EC6D4F68608773E55B3FEADC812E4E2D7C4C647C8C30352141D2926130D10DFC28ACA5CA8A33B7BD7A09C77072CE" to "0A203CE1967EF504559302CB027A52CB36E5BF6EDC2D8CAFEFF86CA2AAF2817C929F",
    "0A41045E4B322ED16CD22465433B0427A4366B9695D7E15DD798526F703035848ACC8D2D002C1F25190454C9B61AB7B243E31E83BA2B48B8A4441F922A08AC3D0A3268" to "0A210082B2714718EE8CEBEBC9AE1106175E0905DF0018553F22EF90374D197B278D0C",
    "0A4104A37653602DA20D27936AF541084869B2F751953CB0F0D25D320788EDA54FB4BC9FB96A281BFFD97E64B749D78C85871A8E14AFD48048537E45E16F3D2FDDB44B" to "0A203CEE9F786716409DF70336E8F38D606A53AE24877AD56ED72D3C10E1D0BD3DE0",
).map { (public, private) -> KeyPair(public.toJavaPublicKey(), private.toJavaPrivateKey()) }
val jarCacheSize = 20000L
val osGrpcDeadlineMs = 20000L
val specCacheSizeInBytes = 20000L
val recordCacheSizeInBytes = 20000L
val osGrpcUri = URI.create("https://localhost:5000")

fun createClientDummy(localKeyIndex: Int): Client {
    val encryptionKeyRef = DirectKeyRef(localKeys[localKeyIndex].public, localKeys[localKeyIndex].private)
    val signingKeyRef = DirectKeyRef(localKeys[localKeyIndex + 1].public, localKeys[localKeyIndex + 1].private)
    val partyType = Specifications.PartyType.OWNER
    val affiliate = io.provenance.scope.sdk.Affiliate(signingKeyRef, encryptionKeyRef, partyType)

    val clientConfig = ClientConfig(jarCacheSize, specCacheSizeInBytes, recordCacheSizeInBytes, osGrpcUri, osGrpcDeadlineMs, mainNet = false)
    return Client(SharedClient(clientConfig), affiliate)
}

fun createSessionBuilderNoRecords(osClient: Client, existingScope: ScopeResponse? = null): Session.Builder{
    val defSpec = Commons.DefinitionSpec.newBuilder()
        .setType(Commons.DefinitionSpec.Type.PROPOSED)
        .setResourceLocation(Commons.Location.newBuilder().setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"))
        .setName("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
    val conditionSpec = Specifications.ConditionSpec.newBuilder()
        .addInputSpecs(defSpec)
        .setFuncName("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
        .build()
    val participants = HashMap<Specifications.PartyType, PK.PublicKey>()
    participants[Specifications.PartyType.OWNER] = PK.PublicKey.newBuilder().build()
    val spec = Specifications.ContractSpec.newBuilder()
        .setDefinition(defSpec)
        .addConditionSpecs(conditionSpec)
        .addFunctionSpecs(Specifications.FunctionSpec.newBuilder().setFuncName("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName").addInputSpecs(defSpec).setInvokerParty(Specifications.PartyType.OWNER))
    if(existingScope != ScopeResponse.getDefaultInstance()) {
        spec.addInputSpecs(defSpec)
    }
    val provenanceReference = Commons.ProvenanceReference.newBuilder().build()
    var scopeSpecUuid: UUID
    if(!existingScope?.scope?.scopeSpecIdInfo?.scopeSpecUuid.isNullOrEmpty()) {
        scopeSpecUuid = existingScope?.scope?.scopeSpecIdInfo?.scopeSpecUuid!!.toUuid()
    } else {
        scopeSpecUuid = UUID.randomUUID()
    }
    return Session.Builder(scopeSpecUuid)
        .setContractSpec(spec.build())
        .setProvenanceReference(provenanceReference)
        .setClient(osClient)
        .setSessionUuid(UUID.randomUUID())
        .apply {
            if (existingScope != null) {
                setScope(existingScope)
                addDataAccessKey(localKeys[3].public)
            }
        }
}

fun createExistingScope(): ScopeResponse.Builder {
    val scopeUuid = UUID.randomUUID()
    val sessionUUID = UUID.randomUUID()
    val specificationUUID = UUID.randomUUID()
    val scopeRecord = Record.newBuilder()
        .addInputs(
            RecordInput.newBuilder()
            .setName("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
            .setHash("M8PWxG2TFfO0YzL3sDW/l9kX325y+3v+5liGcjZoi4Q=")
            .setStatus(RecordInputStatus.RECORD_INPUT_STATUS_PROPOSED))
        .addOutputs(RecordOutput.newBuilder().setHash("M8PWxG2TFfO0YzL3sDW/l9kX325y+3v+5liGcjZoi4Q=").build())
        .setProcess(Process.newBuilder().setName("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName").build())
        .setSessionId(MetadataAddress.forSession(scopeUuid, sessionUUID).bytes.toByteString())
        .setName("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
    val recordWrapper = RecordWrapper.newBuilder().setRecord(scopeRecord).build()
    val scope = Scope.newBuilder()
        .addDataAccess(localKeys[3].public.getAddress(false))
        .addOwners(Party.newBuilder().setRole(PartyType.PARTY_TYPE_OWNER))
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

data class HelloWorldData(@AnRecord(name = "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName") val name: HelloWorldExample.ExampleName) {}

