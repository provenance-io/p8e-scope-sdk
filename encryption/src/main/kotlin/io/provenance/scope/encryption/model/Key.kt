package io.provenance.scope.encryption.model

import com.fortanix.sdkms.v1.api.SignAndVerifyApi
import io.provenance.scope.encryption.crypto.Pen
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.crypto.SmartKeySigner
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID

/**
 * A reference to a key used as an signing/encryption/decryption provider
 * @property [publicKey] the [Public Key][PublicKey] for this reference
 */
sealed class KeyRef(val publicKey: PublicKey)

/**
 * A reference to an externally-held Equinix SmartKey Security Object stored in an HSM
 * @param [publicKey] the [Public Key][PublicKey] for this reference
 * @property [uuid] the Security Object uuid
 * @property [signAndVerifyApi] an Equinix SmartKey api instance to provide communication for signing/encryption/decryption operations
 */
class SmartKeyRef(publicKey: PublicKey, val uuid: UUID, val signAndVerifyApi: SignAndVerifyApi) : KeyRef(publicKey)

/**
 * A reference to a locally-held key
 * @param [publicKey] the [Public Key][PublicKey] for this reference
 * @property [privateKey] the [Private Key][PrivateKey] for this reference
 */
class DirectKeyRef(publicKey: PublicKey, val privateKey: PrivateKey) : KeyRef(publicKey)

/**
 * A single affiliate's keys corresponding to signing and encryption
 */
data class SigningAndEncryptionPublicKeys(val signingPublicKey: PublicKey, val encryptionPublicKey: PublicKey)

/**
 * @return a [SignerImpl] object for a given [KeyRef]
 */
fun KeyRef.signer(): SignerImpl = when (this) {
    is DirectKeyRef -> Pen(KeyPair(this.publicKey, this.privateKey))
    is SmartKeyRef -> SmartKeySigner(this.uuid.toString(), this.publicKey, this.signAndVerifyApi)
}
