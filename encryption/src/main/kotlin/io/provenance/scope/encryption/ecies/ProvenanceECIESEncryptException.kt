package io.provenance.scope.encryption.ecies

import io.provenance.scope.encryption.CryptoException

class ProvenanceECIESEncryptException(message:String, cause:Throwable): CryptoException(message, cause)
