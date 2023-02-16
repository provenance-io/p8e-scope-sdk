package io.provenance.scope.encryption.model

import com.fortanix.sdkms.v1.api.SignAndVerifyApi
import io.provenance.scope.encryption.crypto.ApiSigner
import io.provenance.scope.encryption.crypto.ApiSignerClient
import io.provenance.scope.encryption.crypto.Pen
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.crypto.SmartKeySigner
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.experimental.extensions.toAgreeKey
import io.provenance.scope.encryption.experimental.extensions.toTransientSecurityObject
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID

/**
 * A reference to a key used as an signing/encryption/decryption provider
 * @property [publicKey] the [Public Key][PublicKey] for this reference
 */
abstract class KeyRef(val publicKey: PublicKey) {
    /** @return a computed secret key for a given payload */
    abstract fun getSecretKey(ephemeralPublicKey: PublicKey): ByteArray
    /** @return a [SignerImpl] object for a given [KeyRef] */
    abstract fun signer(): SignerImpl
}

/**
 * A reference to an externally-held Equinix SmartKey Security Object stored in an HSM
 * @param [publicKey] the [Public Key][PublicKey] for this reference
 * @property [uuid] the Security Object uuid
 * @property [signAndVerifyApi] an Equinix SmartKey api instance to provide communication for signing/encryption/decryption operations
 */
class SmartKeyRef(publicKey: PublicKey, val uuid: UUID, val signAndVerifyApi: SignAndVerifyApi) : KeyRef(publicKey) {
    override fun getSecretKey(ephemeralPublicKey: PublicKey): ByteArray {
        // Create a transient security object out of the ephemeral public key
        val transientEphemeralSObj = ephemeralPublicKey.toTransientSecurityObject()

        // Compute the shared/agree key via SmartKey's API
        val secretKey = uuid.toString().toAgreeKey(transientEphemeralSObj.transientKey)

        return secretKey.value
    }

    override fun signer(): SignerImpl = SmartKeySigner(uuid.toString(), publicKey, signAndVerifyApi)
}

/**
 * A reference to a locally-held key
 * @param [publicKey] the [Public Key][PublicKey] for this reference
 * @property [privateKey] the [Private Key][PrivateKey] for this reference
 */
class DirectKeyRef(publicKey: PublicKey, private val privateKey: PrivateKey) : KeyRef(publicKey) {
    constructor(keyPair: KeyPair) : this(keyPair.public, keyPair.private)

    override fun getSecretKey(ephemeralPublicKey: PublicKey): ByteArray {
        val secretKey = ProvenanceKeyGenerator.computeSharedKey(privateKey, ephemeralPublicKey)

        return ECUtils.convertSharedSecretKeyToBytes(secretKey)
    }

    override fun signer(): SignerImpl = Pen(KeyPair(publicKey, privateKey))
}

class ApiKeyRef(publicKey: PublicKey, private val apiSignerClient: ApiSignerClient): KeyRef(publicKey) {
    override fun getSecretKey(ephemeralPublicKey: PublicKey) =
        apiSignerClient.secretKey(ephemeralPublicKey)

    override fun signer(): SignerImpl = ApiSigner(publicKey, apiSignerClient)
}

/**
 * A single affiliate's keys corresponding to signing and encryption
 */
data class SigningAndEncryptionPublicKeys(val signingPublicKey: PublicKey, val encryptionPublicKey: PublicKey)
