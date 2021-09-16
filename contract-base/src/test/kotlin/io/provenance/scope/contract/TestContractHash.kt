package io.provenance.scope.contract

import io.provenance.scope.contract.contracts.ContractHash

class TestContractHash : ContractHash {

    private val classes = mapOf("io.provenance.scope.contract.TestContract" to true)

    override fun getClasses(): Map<String, Boolean> {
        return classes
    }

    override fun getUuid(): String {
        return "123456789"
    }

    override fun getHash(): String {
        return "Qc2bHdrg+3LxlItTZSbzhJnUn5Btha0LCXaiSk34Hhk="
    }
}
