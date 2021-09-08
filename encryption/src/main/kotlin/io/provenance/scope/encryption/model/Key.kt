package io.provenance.scope.encryption.model

import io.provenance.scope.encryption.crypto.Pen
import io.provenance.scope.encryption.crypto.SignerImpl
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID

sealed class KeyRef(val publicKey: PublicKey)
class SmartKeyRef(publicKey: PublicKey, val uuid: UUID) : KeyRef(publicKey)
class DirectKeyRef(publicKey: PublicKey, val privateKey: PrivateKey) : KeyRef(publicKey)

// TODO implement smart key leg
fun KeyRef.signer(): SignerImpl = when (this) {
    is DirectKeyRef -> Pen(KeyPair(this.publicKey, this.privateKey))
    is SmartKeyRef -> throw IllegalStateException("TODO SmartKeyRef is not yet supported!")
}
