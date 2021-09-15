package io.provenance.scope.encryption.model

import com.fortanix.sdkms.v1.api.SignAndVerifyApi
import io.provenance.scope.encryption.crypto.Pen
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.crypto.SmartKeySigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID

sealed class KeyRef(val publicKey: PublicKey)
class SmartKeyRef(publicKey: PublicKey, val uuid: UUID, val signAndVerifyApi: SignAndVerifyApi) : KeyRef(publicKey)
class DirectKeyRef(publicKey: PublicKey, val privateKey: PrivateKey) : KeyRef(publicKey)

data class SigningAndEncryptionPublicKeys(val signingPublicKey: PublicKey, val encryptionPublicKey: PublicKey)

fun KeyRef.signer(): SignerImpl = when (this) {
    is DirectKeyRef -> Pen(KeyPair(this.publicKey, this.privateKey))
    is SmartKeyRef -> SmartKeySigner(this.uuid.toString(), this.publicKey, this.signAndVerifyApi)
}
