package io.provenance.scope

import io.provenance.scope.contract.proto.Contracts.Contract
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.util.ContractValidationException

interface ContractValidator {
    @Throws(ContractValidationException::class)
    fun validate(contract: Contract, spec: ContractSpec): Unit
}

object ValidateRecitalMatchesSpec: ContractValidator {
    override fun validate(contract: Contract, spec: ContractSpec) {
        val contractParties = contract.recitalsList.map { it.signerRole }.sortedBy { it.name }
        val specParties = spec.partiesInvolvedList.sortedBy { it.name }
        if (contractParties != specParties) {
            throw ContractValidationException(
                "Provided signers and their roles do not match the contract spec [required parties: $specParties] [specified parties: $contractParties]"
            )
        }
    }
}

object ValidateAllFactsAreSupplied: ContractValidator {
    override fun validate(contract: Contract, spec: ContractSpec) {
        val specFacts = spec.inputSpecsList.map { it.name }.sorted()
        val contractFacts = contract.inputsList.filter { it.dataLocation.ref.hash.isNotEmpty() }.map { it.name }.intersect(specFacts).sorted()

        if (contractFacts != specFacts) {
            throw ContractValidationException("Provided facts do not match the contract spec, the constructor for the contract contains a fact that is not in the current scope record on chain. [required facts: $specFacts] [specified facts: $contractFacts]")
        }
    }
}

object ContractValidators {
    private val validators = listOf(
        ValidateRecitalMatchesSpec,
        ValidateAllFactsAreSupplied
    )

    @Throws(ContractValidationException::class)
    fun validateAll(contract: Contract, spec: ContractSpec) = validators.forEach {
        it.validate(contract, spec)
    }
}

@Throws(ContractValidationException::class)
fun Contract.validateAgainst(spec: ContractSpec) = ContractValidators.validateAll(this, spec)
