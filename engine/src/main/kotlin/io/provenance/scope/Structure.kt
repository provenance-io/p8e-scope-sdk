package io.provenance.scope

enum class RecordType {
    Proposed,
    Existing
}

data class P8eFunctionParameter(
    val recordType: RecordType,
    val name: String,
    val optional: Boolean,
    val type: String,
)

data class P8eFunction(
    val name: String,
    val methodName: String,
    val invokedBy: String,
    val parameters: List<P8eFunctionParameter>,
    val returnType: String,
)

data class P8eContractDetails(
    val ScopeSpecificationUuids: List<String>,
    val participants: List<String>,
    val name: String,
    val description: String,
    val websiteUrl: String,
    val iconUrl: String,
    val functions: List<P8eFunction>,
)
