package io.provenance.scope.contract

import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.Participants
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.ScopeSpecification
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition
import io.provenance.scope.contract.proto.HelloWorldExample
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.contract.proto.TestContractProtos
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification

@Participants([Specifications.PartyType.OWNER])
@ScopeSpecification(names = ["io.provenance.scope.contract.testcontract"])
class TestContract(@Record(name = "testRecord") val testRecordValue: List<HelloWorldExample.ExampleName> = mutableListOf(
    HelloWorldExample.ExampleName.newBuilder().setFirstName("testRecordValue").build())): P8eContract() {

    @Record(name = "testRecord")
    fun testRecord(): TestContractProtos.TestProto = TestContractProtos.TestProto.newBuilder().setValue(testRecordValue[0].firstName).build()

    @Record(name = "testRecord")
    @Function(Specifications.PartyType.OWNER)
    fun printTest(@Record(name = "testRecord") testPrintValue: String) { println("TestRecordValue for $testPrintValue")}
}

@ScopeSpecificationDefinition(
    uuid = "090752e0-4c8f-49d1-9751-3aaf7e5e9c17",
    name = "io.provenance.scope.contract.testcontract",
    description = "A generic scope for tests.",
    partiesInvolved = [Specifications.PartyType.OWNER],
)
open class TestContractScopeSpecificationDefinition() : P8eScopeSpecification()
