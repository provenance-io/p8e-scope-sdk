package io.provenance.scope.util

fun <T : Any> T?.orThrowNotFound(message: String) = this ?: throw NotFoundException(message)
fun <T : Any> T?.orThrowContractDefinition(message: String) = this ?: throw ContractDefinitionException(message)

class ContractDefinitionException(message: String): Exception(message)
class ContractValidationException(message: String) : RuntimeException(message)
class ProtoParseException(message: String): RuntimeException(message)
class ContractBootstrapException(message: String): RuntimeException(message)
open class NotFoundException(message: String) : RuntimeException(message)
class AffiliateNotFoundException(message: String) : NotFoundException(message)
