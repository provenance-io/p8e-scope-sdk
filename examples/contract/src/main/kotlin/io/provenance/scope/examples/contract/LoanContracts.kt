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

const val loanScopeNamespace = "io.provenance.examples.Loan"

@ScopeSpecificationDefinition(
    uuid = "87e4fcc4-3d85-43f7-8fa9-376d5e683cba",
    name = loanScopeNamespace,
    description = "Simple loan example. This is used internally at Figure for examples.",
    partiesInvolved = [ORIGINATOR],
)
class LoanScopeSpecification : P8eScopeSpecification()

@Participants(roles = [ORIGINATOR])
@ScopeSpecification(names = [loanScopeNamespace])
open class LoanOnboard : P8eContract() {
    @Function(invokedBy = ORIGINATOR)
    @Record(name = "credit_report")
    open fun creditReport(@Input(name = "credit_report") creditReport: CreditReport): CreditReport = creditReport

    @Function(invokedBy = ORIGINATOR)
    @Record(name = "documents")
    open fun initialDocuments(@Input(name = "documents") documents: DocumentList): DocumentList = documents

    @Function(invokedBy = ORIGINATOR)
    @Record(name = "income")
    open fun income(@Input(name = "income") income: Income): Income = income

    @Function(invokedBy = ORIGINATOR)
    @Record(name = "loan")
    open fun loan(@Input(name = "loan") loan: Loan): Loan = loan

    @Function(invokedBy = ORIGINATOR)
    @Record(name = "underwriting_packet")
    open fun underwritingPacket(@Input(name = "underwriting_packet") underwritingPacket: UnderwritingPacket): UnderwritingPacket = underwritingPacket

    @Function(invokedBy = ORIGINATOR)
    @Record(name = "lien")
    open fun lien(@Input(name = "lien") lien: Lien): Lien = lien
}

@Participants(roles = [ORIGINATOR])
@ScopeSpecification(names = [loanScopeNamespace])
open class AddLoanServicing : P8eContract() {
    @Function(invokedBy = ORIGINATOR)
    @Record(name = "servicing")
    open fun servicing(@Input(name = "servicing") servicing: Servicing): Servicing = servicing
}

@Participants(roles = [ORIGINATOR])
@ScopeSpecification(names = [loanScopeNamespace])
open class AddLoanDocument(
    @Record(name = "documents") val existingDocuments: DocumentList,
) : P8eContract() {
    @Function(invokedBy = ORIGINATOR)
    @Record(name = "documents")
    open fun addDocument(@Input(name = "documents") documents: DocumentList): DocumentList {
        return existingDocuments.toBuilder()
            .addAllDocuments(documents.documentsList)
            .build()
    }
}
