package io.provenance.scope.encryption.crypto

import java.security.PublicKey

interface ApiSignerClient {
    fun sign(data: ByteArray): ByteArray
    fun secretKey(ephemeralPublicKey: PublicKey): ByteArray
}