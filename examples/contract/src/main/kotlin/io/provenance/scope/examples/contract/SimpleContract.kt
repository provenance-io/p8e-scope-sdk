package io.provenance.scope.examples.contract

import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.Input
import io.provenance.scope.contract.annotations.Participants
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.ScopeSpecification
import io.provenance.scope.contract.annotations.ScopeSpecificationDefinition
import io.provenance.scope.contract.proto.Specifications.PartyType.OWNER
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.contract.spec.P8eScopeSpecification
import io.provenance.scope.examples.SimpleExample.ExampleName

const val scopeNamespace = "io.provenance.examples.Simple"

@ScopeSpecificationDefinition(
    uuid = "92225c03-6b99-402a-9337-93735f18af70",
    name = scopeNamespace,
    description = "Simple example scope. This is used internally at Figure for examples.",
    partiesInvolved = [OWNER],
)
class SimpleExampleScopeSpecification() : P8eScopeSpecification()

@Participants(roles = [OWNER])
@ScopeSpecification(names = [scopeNamespace])
open class SimpleExampeContract(): P8eContract() {
    @Function(invokedBy = OWNER)
    @Record(name = "name")
    open fun name(@Input(name = "name") name: ExampleName): ExampleName =
        name.toBuilder()
            .setFirstName(name.firstName.plus("-simple"))
            .setLastName(name.lastName.plus("-example"))
            .build()
}
