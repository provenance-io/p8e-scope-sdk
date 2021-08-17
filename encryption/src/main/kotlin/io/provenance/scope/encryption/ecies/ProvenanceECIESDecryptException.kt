package io.provenance.scope.encryption.ecies

import io.provenance.scope.encryption.CryptoException

class ProvenanceECIESDecryptException(message:String, cause:Throwable): CryptoException(message, cause)
