package io.provenance.scope.encryption.kdf

import io.provenance.scope.encryption.CryptoException

class ProvenanceHKDFCryptoException (message:String,cause:Throwable): CryptoException(message, cause)
