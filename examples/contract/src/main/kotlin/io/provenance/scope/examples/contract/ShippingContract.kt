package io.provenance.scope.examples.contract

import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.Input
import io.provenance.scope.contract.annotations.Participants
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.ScopeSpecification
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition
import io.provenance.scope.contract.proto.Specifications.PartyType.ORIGINATOR
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification
import io.provenance.scope.examples.LoanExample.CreditReport
import io.provenance.scope.examples.LoanExample.DocumentList
import io.provenance.scope.examples.LoanExample.Income
import io.provenance.scope.examples.LoanExample.Lien
import io.provenance.scope.examples.LoanExample.Loan
import io.provenance.scope.examples.LoanExample.Servicing
import io.provenance.scope.examples.LoanExample.UnderwritingPacket

const val shippingScopeNamespace = "io.provenance.examples.Shipping"

@ScopeSpecificationDefinition(
    uuid = "87e4fcc4-3d85-43f7-8fa9-376d5e683ccc",
    name = shippingScopeNamespace,
    description = "Simple shipping example. This is used internally at Figure for examples.",
    partiesInvolved = [ORIGINATOR],
)
class ShippingScopeSpecification : P8eScopeSpecification()

@Participants(roles = [ORIGINATOR])
@ScopeSpecification(names = [shippingScopeNamespace])
open class ShipPackage : P8eContract() {
    @Function(invokedBy = ORIGINATOR)
    @Record(name = "package")
    open fun initiate(@Input(name = "package") shippingPackage: ShippingPackage): ShippingPackage = shippingPackage
}

@Participants(roles = [ORIGINATOR])
@ScopeSpecification(names = [shippingScopeNamespace])
open class AddCheckin(
    @Record(name = "package") val existingPackage: ShippingPackage,
) : P8eContract() {
    @Function(invokedBy = ORIGINATOR)
    @Record(name = "documents")
    open fun addCheckin(@Input(name = "checkIn") checkpoint: Checkpoint): CheckpointList {
        return existingPackage.toBuilder()
            .addAllCheckpoints(existingPackage.checkins)
            .build()
    }
}
