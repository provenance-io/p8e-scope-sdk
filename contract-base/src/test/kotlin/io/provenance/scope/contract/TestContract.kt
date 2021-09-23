package io.provenance.scope.contract

import io.provenance.scope.contract.annotations.Participants
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.ScopeSpecification
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.contract.proto.TestContractProtos
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification

@Participants([Specifications.PartyType.OWNER])
@ScopeSpecification(names = ["io.provenance.scope.contract.testcontract"])
class TestContract: P8eContract() {
    @Record(name = "testRecord")
    fun testRecord(): TestContractProtos.TestProto = TestContractProtos.TestProto.newBuilder().setValue("testRecordValue").build()
}

@ScopeSpecificationDefinition(
    uuid = "090752e0-4c8f-49d1-9751-3aaf7e5e9c17",
    name = "io.provenance.scope.contract.testcontract",
    description = "A generic scope for tests.",
    partiesInvolved = [Specifications.PartyType.OWNER],
)
open class TestContractScopeSpecificationDefinition() : P8eScopeSpecification()
