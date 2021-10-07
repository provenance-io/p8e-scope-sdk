package io.provenance.scope.contract

import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.Input
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
class TestContract(): P8eContract() {

    @Record(name = "testRecord")
    @Function(Specifications.PartyType.OWNER)
    fun testRecord(): TestContractProtos.TestProto = TestContractProtos.TestProto.newBuilder().build()

    @Record(name = "testRecord")
    @Function(Specifications.PartyType.OWNER)
    fun printTest(@Record(name = "testRecord") testPrintValue: String) { println("TestRecordValue for $testPrintValue")}

    @Record(name = "testRecord")
    @Function(Specifications.PartyType.OWNER)
    fun testReturnNull(): Unit? { return null }

    @Record(name = "testRecord")
    @Function(Specifications.PartyType.OWNER)
    fun testThrowError(): Unit? { throw IllegalArgumentException("Test error being thrown") }

    @Record(name = "testRecordTwoInputs")
    @Function(Specifications.PartyType.OWNER)
    fun testRecordTwoInputs(
        @Input(name = "testRecordInputOne") testRecordInputOne: TestContractProtos.TestProto,
        @Input(name = "testRecordInputTwo") testRecordInputTwo: TestContractProtos.TestProto
    ) = TestContractProtos.TestProto.newBuilder()
        .setValue(testRecordInputOne.value + testRecordInputTwo.value)
        .build()

    @Record(name = "testRecordTwoRecords")
    @Function(Specifications.PartyType.OWNER)
    fun testRecordTwoRecords(
        @Record(name = "testRecordOne") testRecordOne: TestContractProtos.TestProto,
        @Record(name = "testRecordTwo") testRecordTwo: TestContractProtos.TestProto,
    ) = TestContractProtos.TestProto.newBuilder()
        .setValue(testRecordOne.value + testRecordTwo.value)
        .build()

    @Record(name = "testRecordOneInputOneRecord")
    @Function(Specifications.PartyType.OWNER)
    fun testRecordOneInputOneRecord(
        @Record(name = "testRecordOne") testRecordOne: TestContractProtos.TestProto,
        @Input(name = "testRecordInputOne") testRecordInputOne: TestContractProtos.TestProto,
    ) = TestContractProtos.TestProto.newBuilder()
        .setValue(testRecordOne.value + testRecordInputOne.value)
        .build()

    @Record(name = "testRecordDoubleAnnotatedArgument")
    @Function(Specifications.PartyType.OWNER)
    fun testRecordDoubleAnnotatedArgument(
        @Record(name = "testRecordOne") @Input(name = "testRecordInputOne") testRecordOneDoubleAnnotation: TestContractProtos.TestProto,
    ) = TestContractProtos.TestProto.newBuilder()
        .setValue(testRecordOneDoubleAnnotation.value + "-modified")
        .build()
}

@Participants([Specifications.PartyType.OWNER])
@ScopeSpecification(names = ["io.provenance.scope.contract.testcontract"])
class BadTestContract(): P8eContract() {
    @Record(name = "testRecordNonAnnotatedArgument")
    @Function(Specifications.PartyType.OWNER)
    fun testRecordNonAnnotatedArgument(
        testRecordOneNoAnnotation: TestContractProtos.TestProto,
    ) = TestContractProtos.TestProto.newBuilder()
        .setValue(testRecordOneNoAnnotation.value + "-modified")
        .build()
}

@ScopeSpecificationDefinition(
    uuid = "090752e0-4c8f-49d1-9751-3aaf7e5e9c17",
    name = "io.provenance.scope.contract.testcontract",
    description = "A generic scope for tests.",
    partiesInvolved = [Specifications.PartyType.OWNER],
)
open class TestContractScopeSpecificationDefinition() : P8eScopeSpecification()
