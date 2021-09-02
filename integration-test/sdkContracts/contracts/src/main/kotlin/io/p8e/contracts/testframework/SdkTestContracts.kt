package io.provenance.scope.sdk.testframework.contracts

import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.Input
import io.provenance.scope.contract.annotations.Participants
import io.provenance.scope.contract.annotations.ScopeSpecification
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition
import io.provenance.scope.contract.proto.Specifications.PartyType.*
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification
import io.provenance.scope.sdk.testframework.proto.RandomBytes.Data



//Single Party Contracts
@Participants(roles = [OWNER])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkSinglePartyTestContract"])
open class SdkSinglePartyContractSmall: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkSinglePartyTestContract"])
open class SdkSinglePartyContractMedium: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact3")             
    open fun fact3(@Input(name = "fact3") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact4")             
    open fun fact4(@Input(name = "fact4") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact5")             
    open fun fact5(@Input(name = "fact5") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact6")             
    open fun fact6(@Input(name = "fact6") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact7")             
    open fun fact7(@Input(name = "fact7") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact8")             
    open fun fact8(@Input(name = "fact8") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkSinglePartyTestContract"])
open class SdkSinglePartyContractLarge: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact3")             
    open fun fact3(@Input(name = "fact3") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact4")             
    open fun fact4(@Input(name = "fact4") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact5")             
    open fun fact5(@Input(name = "fact5") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact6")             
    open fun fact6(@Input(name = "fact6") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact7")             
    open fun fact7(@Input(name = "fact7") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact8")             
    open fun fact8(@Input(name = "fact8") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact9")             
    open fun fact9(@Input(name = "fact9") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact10")             
    open fun fact10(@Input(name = "fact10") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact11")             
    open fun fact11(@Input(name = "fact11") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact12")             
    open fun fact12(@Input(name = "fact12") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact13")             
    open fun fact13(@Input(name = "fact13") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact14")             
    open fun fact14(@Input(name = "fact14") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact15")             
    open fun fact15(@Input(name = "fact15") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact16")             
    open fun fact16(@Input(name = "fact16") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact17")             
    open fun fact17(@Input(name = "fact17") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact18")             
    open fun fact18(@Input(name = "fact18") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact19")             
    open fun fact19(@Input(name = "fact19") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact20")             
    open fun fact20(@Input(name = "fact20") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

}

//Single party modification contracts
@Participants(roles = [OWNER])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkSinglePartyTestContract"])
open class SdkSinglePartyContractSmallModify(@Record(name = "fact1") val currentFact1: Data,
                                                @Record(name = "fact2") val currentFact2: Data): P8eContract() {
    @Function(invokedBy = OWNER)
    @Record(name = "fact1")
    open fun fact1(@Input(name = "fact1") fact: Data) =
        fact.toBuilder()    
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)    
    @Record(name = "fact2")            
    open fun fact2(@Input(name = "fact2") fact: Data) =   
        fact.toBuilder()    
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkSinglePartyTestContract"])
open class SdkSinglePartyContractMediumModify(@Record(name = "fact1") val currentFact1: Data,
                                                @Record(name = "fact2") val currentFact2: Data,
                                                @Record(name = "fact3") val currentFact3: Data,
                                                @Record(name = "fact4") val currentFact4: Data,
                                                @Record(name = "fact5") val currentFact5: Data,
                                                @Record(name = "fact6") val currentFact6: Data,
                                                @Record(name = "fact7") val currentFact7: Data,
                                                @Record(name = "fact8") val currentFact8: Data): P8eContract() {
    @Function(invokedBy = OWNER)
    @Record(name = "fact1")
    open fun fact1(@Input(name = "fact1") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact2")
    open fun fact2(@Input(name = "fact2") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact3")
    open fun fact3(@Input(name = "fact3") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact4")
    open fun fact4(@Input(name = "fact4") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact5")
    open fun fact5(@Input(name = "fact5") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact6")
    open fun fact6(@Input(name = "fact6") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact7")
    open fun fact7(@Input(name = "fact7") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact8")
    open fun fact8(@Input(name = "fact8") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkSinglePartyTestContract"])
open class SdkSinglePartyContractLargeModify(@Record(name = "fact1") val currentFact1: Data,
                                             @Record(name = "fact2") val currentFact2: Data,
                                             @Record(name = "fact3") val currentFact3: Data,
                                             @Record(name = "fact4") val currentFact4: Data,
                                             @Record(name = "fact5") val currentFact5: Data,
                                             @Record(name = "fact6") val currentFact6: Data,
                                             @Record(name = "fact7") val currentFact7: Data,
                                             @Record(name = "fact8") val currentFact8: Data,
                                             @Record(name = "fact9") val currentFact9: Data,
                                             @Record(name = "fact10") val currentFact10: Data,
                                             @Record(name = "fact11") val currentFact11: Data,
                                             @Record(name = "fact12") val currentFact12: Data,
                                             @Record(name = "fact13") val currentFact13: Data,
                                             @Record(name = "fact14") val currentFact14: Data,
                                             @Record(name = "fact15") val currentFact15: Data,
                                             @Record(name = "fact16") val currentFact16: Data,
                                             @Record(name = "fact17") val currentFact17: Data,
                                             @Record(name = "fact18") val currentFact18: Data,
                                             @Record(name = "fact19") val currentFact19: Data,
                                             @Record(name = "fact20") val currentFact20: Data): P8eContract() {
    @Function(invokedBy = OWNER)
    @Record(name = "fact1")
    open fun fact1(@Input(name = "fact1") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact2")
    open fun fact2(@Input(name = "fact2") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact3")
    open fun fact3(@Input(name = "fact3") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact4")
    open fun fact4(@Input(name = "fact4") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact5")
    open fun fact5(@Input(name = "fact5") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact6")
    open fun fact6(@Input(name = "fact6") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact7")
    open fun fact7(@Input(name = "fact7") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact8")
    open fun fact8(@Input(name = "fact8") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact9")
    open fun fact9(@Input(name = "fact9") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact10")
    open fun fact10(@Input(name = "fact10") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact11")
    open fun fact11(@Input(name = "fact11") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact12")
    open fun fact12(@Input(name = "fact12") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact13")
    open fun fact13(@Input(name = "fact13") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact14")
    open fun fact14(@Input(name = "fact14") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact15")
    open fun fact15(@Input(name = "fact15") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact16")
    open fun fact16(@Input(name = "fact16") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact17")
    open fun fact17(@Input(name = "fact17") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact18")
    open fun fact18(@Input(name = "fact18") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact19")
    open fun fact19(@Input(name = "fact19") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)
    @Record(name = "fact20")
    open fun fact20(@Input(name = "fact20") fact: Data) =
        fact.toBuilder()
            .setData(fact.data)
            .build()

}

@ScopeSpecificationDefinition(  //Define information about the scope.  Scope provides general information
    uuid = "2d8b8f61-8306-49b1-a5d3-dcafafb05ed8",
    name = "io.scope.sdk.testframework.contracts.SdkSinglePartyTestContract",
    description = "A scope that contains the TestContract for Sdk library",
    partiesInvolved = [OWNER],
)
open class SdkSinglePartyTestScopeSpecification() : P8eScopeSpecification()










//Multi-Party contracts(using 3 parties)
@Participants(roles = [OWNER,
    CUSTODIAN,
    OMNIBUS])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkMultiPartyTestContract"])
open class SdkMultiPartyContractSmall: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER,
    CUSTODIAN,
    OMNIBUS])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkMultiPartyTestContract"])
open class SdkMultiPartyContractMedium: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact3")             
    open fun fact3(@Input(name = "fact3") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact4")             
    open fun fact4(@Input(name = "fact4") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact5")             
    open fun fact5(@Input(name = "fact5") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact6")             
    open fun fact6(@Input(name = "fact6") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact7")             
    open fun fact7(@Input(name = "fact7") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact8")             
    open fun fact8(@Input(name = "fact8") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER,
    CUSTODIAN,
    OMNIBUS])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkMultiPartyTestContract"])
open class SdkMultiPartyContractLarge: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact3")             
    open fun fact3(@Input(name = "fact3") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact4")             
    open fun fact4(@Input(name = "fact4") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact5")             
    open fun fact5(@Input(name = "fact5") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact6")             
    open fun fact6(@Input(name = "fact6") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact7")             
    open fun fact7(@Input(name = "fact7") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact8")             
    open fun fact8(@Input(name = "fact8") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact9")             
    open fun fact9(@Input(name = "fact9") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact10")             
    open fun fact10(@Input(name = "fact10") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact11")             
    open fun fact11(@Input(name = "fact11") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact12")             
    open fun fact12(@Input(name = "fact12") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact13")             
    open fun fact13(@Input(name = "fact13") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact14")             
    open fun fact14(@Input(name = "fact14") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact15")             
    open fun fact15(@Input(name = "fact15") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact16")             
    open fun fact16(@Input(name = "fact16") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact17")             
    open fun fact17(@Input(name = "fact17") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact18")             
    open fun fact18(@Input(name = "fact18") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact19")             
    open fun fact19(@Input(name = "fact19") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact20")             
    open fun fact20(@Input(name = "fact20") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

}

@ScopeSpecificationDefinition(
    uuid = "7c55b825-2345-4911-bb17-5a799513d60c",
    name = "io.scope.sdk.testframework.contracts.SdkMultiPartyTestContract",
    description = "A scope that contains multi-party SdkTestContracts for 3 participants",
    partiesInvolved = [OWNER,
        CUSTODIAN,
        OMNIBUS]
)
open class SdkMultiPartyTestScopeSpecification: P8eScopeSpecification()








//Multi-party multi-step contracts(using 2 parties)
@Participants(roles = [OWNER,
    CUSTODIAN])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkMultiPartyMultiStepTestContract"])
open class SdkMultiPartyMultiStepContractSmall: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER,
    CUSTODIAN])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkMultiPartyMultiStepTestContract"])
open class SdkMultiPartyMultiStepContractMedium: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact3")             
    open fun fact3(@Input(name = "fact3") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact4")             
    open fun fact4(@Input(name = "fact4") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact5")             
    open fun fact5(@Input(name = "fact5") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact6")             
    open fun fact6(@Input(name = "fact6") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact7")             
    open fun fact7(@Input(name = "fact7") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact8")             
    open fun fact8(@Input(name = "fact8") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()
}

@Participants(roles = [OWNER,
    CUSTODIAN])
@ScopeSpecification(names = ["io.scope.sdk.testframework.contracts.SdkMultiPartyMultiStepTestContract"])
open class SdkMultiPartyMultiStepContractLarge: P8eContract() {
    @Function(invokedBy = OWNER)     
    @Record(name = "fact1")             
    open fun fact1(@Input(name = "fact1") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact2")             
    open fun fact2(@Input(name = "fact2") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact3")             
    open fun fact3(@Input(name = "fact3") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact4")             
    open fun fact4(@Input(name = "fact4") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact5")             
    open fun fact5(@Input(name = "fact5") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact6")             
    open fun fact6(@Input(name = "fact6") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact7")             
    open fun fact7(@Input(name = "fact7") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact8")             
    open fun fact8(@Input(name = "fact8") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact9")             
    open fun fact9(@Input(name = "fact9") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = OWNER)     
    @Record(name = "fact10")             
    open fun fact10(@Input(name = "fact10") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact11")             
    open fun fact11(@Input(name = "fact11") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact12")             
    open fun fact12(@Input(name = "fact12") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact13")             
    open fun fact13(@Input(name = "fact13") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact14")             
    open fun fact14(@Input(name = "fact14") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact15")             
    open fun fact15(@Input(name = "fact15") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact16")             
    open fun fact16(@Input(name = "fact16") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact17")             
    open fun fact17(@Input(name = "fact17") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact18")             
    open fun fact18(@Input(name = "fact18") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact19")             
    open fun fact19(@Input(name = "fact19") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

    @Function(invokedBy = CUSTODIAN)     
    @Record(name = "fact20")             
    open fun fact20(@Input(name = "fact20") fact: Data) =    
        fact.toBuilder()     
            .setData(fact.data)
            .build()

}

@ScopeSpecificationDefinition(
    uuid = "547f826d-2cba-40d5-b319-30e5fdcb94a3",
    name = "io.scope.sdk.testframework.contracts.SdkMultiPartyMultiStepTestContract",
    description = "A scope that contains multi-party multi-step SdkTestContracts for 2 participants",
    partiesInvolved = [OWNER,
        CUSTODIAN]
)
open class SdkMultiPartyMultiStepTestScopeSpecification: P8eScopeSpecification()

