
package io.provenance.scope.examples.contract

import io.provenance.scope.contract.contracts.ContractHash

class ContractHash1630526268279 : ContractHash {

    private val classes = mapOf("io.provenance.scope.examples.contract.SimpleExampeContract" to true)
    
    override fun getClasses(): Map<String, Boolean> {
        return classes
    }
    
    override fun getUuid(): String {
        return "1630526268279"
    }

    override fun getHash(): String {
        return "uVrFYsYLXZ0uU9mqKLSlx19lFG0L8FyhktBHWhKZzkk="
    }
}
        