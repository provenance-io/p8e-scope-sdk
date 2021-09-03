package io.provenance.scope.sdk.testframework

import io.provenance.scope.contract.spec.P8eContract as SdkContract
import io.p8e.proto.ContractScope
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.sdk.testframework.contracts.*
import io.provenance.scope.contract.spec.P8eScopeSpecification
import io.provenance.scope.encryption.util.toJavaPrivateKey
import io.provenance.scope.encryption.util.toJavaPublicKey
import java.security.KeyPair

//List of Public to private pairs used for samples for sdk contract default keys or for testing
val localKeys = listOf(
    "0A41046C57E9E25101D5E553AE003E2F79025E389B51495607C796B4E95C0A94001FBC24D84CD0780819612529B803E8AD0A397F474C965D957D33DD64E642B756FBC4" to "0A2071E487C3FB00642F1863D57749F32D94F13892FA68D02049F7EA9E8FC58D6E63",
    "0A4104D630032378D56229DD20D08DBCC6D31F44A07D98175966F5D32CD2189FD748831FCB49266124362E56CC1FAF2AA0D3F362BF84CACBC1C0C74945041EB7327D54" to "0A2077170DEDCB6CFEDCE5A19BC8F0CD408A254F1E48A7350FC2E9019F50AE52524F",
    "0A4104CD5F4ACFFE72D323CCCB2D784847089BBD80EC6D4F68608773E55B3FEADC812E4E2D7C4C647C8C30352141D2926130D10DFC28ACA5CA8A33B7BD7A09C77072CE" to "0A203CE1967EF504559302CB027A52CB36E5BF6EDC2D8CAFEFF86CA2AAF2817C929F",
    "0A41045E4B322ED16CD22465433B0427A4366B9695D7E15DD798526F703035848ACC8D2D002C1F25190454C9B61AB7B243E31E83BA2B48B8A4441F922A08AC3D0A3268" to "0A210082B2714718EE8CEBEBC9AE1106175E0905DF0018553F22EF90374D197B278D0C",
    "0A4104A37653602DA20D27936AF541084869B2F751953CB0F0D25D320788EDA54FB4BC9FB96A281BFFD97E64B749D78C85871A8E14AFD48048537E45E16F3D2FDDB44B" to "0A203CEE9F786716409DF70336E8F38D606A53AE24877AD56ED72D3C10E1D0BD3DE0"
).map { (public, private) -> KeyPair(public.toJavaPublicKey(), private.toJavaPrivateKey()) }

//Creates a ByteArray of a given size filled with random data for the value of facts
fun generateRandomBytes(numberOfBytes: Int): ByteArray {
    val randomBytes = ByteArray(numberOfBytes)
    for(num in 0..(numberOfBytes-1)){
        randomBytes[num] = (0..255).random().toByte()
    }
    return randomBytes
}

//Holds the number of facts in the contract, the number of parties, and the scope's class.java for sdk,
// for p8e holds whether or not the contract is multi-step
data class SdkContractInformation(val maxFacts: Int, val numParticipants: Int, val scopeSpec: Class<out P8eScopeSpecification>)

//TODO: Add multi-party and multi-step contracts when those are implemented
//To use your own contracts, just add the class.java and put the needed information in the data class
//Needed information: Number of facts, number of parties, and the scope spec
val SdkContractInformationMap = hashMapOf<Class<out SdkContract>, SdkContractInformation>(
    SdkSinglePartyContractSmall::class.java to
            SdkContractInformation(2, 1, SdkSinglePartyTestScopeSpecification::class.java as Class<out P8eScopeSpecification>),
    SdkSinglePartyContractMedium::class.java to
            SdkContractInformation(8, 1, SdkSinglePartyTestScopeSpecification::class.java as Class<out P8eScopeSpecification>),
    SdkSinglePartyContractLarge::class.java to
            SdkContractInformation(20, 1, SdkSinglePartyTestScopeSpecification::class.java as Class<out P8eScopeSpecification>),
    SdkSinglePartyContractSmallModify::class.java to
            SdkContractInformation(2, 1, SdkSinglePartyTestScopeSpecification::class.java as Class<out P8eScopeSpecification>),
    SdkSinglePartyContractMediumModify::class.java to
            SdkContractInformation(8, 1, SdkSinglePartyTestScopeSpecification::class.java as Class<out P8eScopeSpecification>),
    SdkSinglePartyContractLargeModify::class.java to
            SdkContractInformation(20, 1, SdkSinglePartyTestScopeSpecification::class.java as Class<out P8eScopeSpecification>)
)

//Used in ContractResult to tell if the contract completed or not.
enum class ResultState { SUCCESS, FAILED }

//Data class containing useful information about the contract.
data class SdkContractResult(
    val result: ResultState,
    val indexedResult: Map<String, Any>,
    val scope: ScopeResponse?
)

enum class Facts(val fact: String) {
    FACT1("fact1"),
    FACT2("fact2"),
    FACT3("fact3"),
    FACT4("fact4"),
    FACT7("fact7")
}

//I would erase these, but I think these might have a use later in future revisions
//One of these can be passed into the constructor of the builder(s) to specify what type of contract to use
//  SP is single-party, MP is multi-party, MPMS is multi-party multi-step
//  Small has 2 facts, Medium has 8 facts, Large has 20 facts
//  type is the contract to be used.
//enum class SdkContractType(val type: Class<out SdkContract>){
//    SDKSPSmall(SdkSinglePartyContractSmall::class.java),
//    SDKSPMedium(SdkSinglePartyContractMedium::class.java),
//    SDKSPLarge(SdkSinglePartyContractLarge::class.java),
//    SDKMPSmall(SdkMultiPartyContractSmall::class.java),
//    SDKMPMedium(SdkMultiPartyContractMedium::class.java),
//    SDKMPLarge(SdkMultiPartyContractLarge::class.java),
//    SDKMPMSSmall(SdkMultiPartyMultiStepContractSmall::class.java),
//    SDKMPMSMedium(SdkMultiPartyMultiStepContractMedium::class.java),
//    SDKMPMSLarge(SdkMultiPartyMultiStepContractLarge::class.java),
//
//    //Update Contracts
//    SDKSPSmallM(SdkSinglePartyContractSmallModify::class.java),
//    SDKSPMediumM(SdkSinglePartyContractMediumModify::class.java),
//    SDKSPLargeM(SdkSinglePartyContractLargeModify::class.java)
//}