package io.provenance.scope.contract

import io.provenance.scope.contract.annotations.Participants
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.contract.spec.P8eContract

@Participants([Specifications.PartyType.OWNER])
class TestContract: P8eContract() {
    @Record(name = "testRecord")
    fun testRecord(): PublicKeys.PublicKey = PublicKeys.PublicKey.getDefaultInstance()
}
