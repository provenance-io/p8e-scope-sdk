package io.provenance.scope.encryption.crypto

import com.google.protobuf.Message
import io.provenance.scope.encryption.proto.Common
import io.provenance.scope.encryption.proto.PK
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.PublicKey

interface SignerImpl {

    companion object{
        // Algo must match Provenance-object-store
        val SIGN_ALGO = "SHA512withECDSA"
        val PROVIDER = BouncyCastleProvider.PROVIDER_NAME
    }

    /**
     * signer function implementation will be done by specific signers.
     */
    fun sign(data: String): Common.Signature

    fun sign(data: Message): Common.Signature

    fun sign(data: ByteArray): Common.Signature

    fun sign(): ByteArray

    fun update(data: Byte)

    fun update(data: ByteArray)

    fun update(data: ByteArray, off: Int, len: Int)

    fun initSign()

    fun signer(): PK.SigningAndEncryptionPublicKeys

    fun verify(publicKey: PublicKey, data: ByteArray, signature: Common.Signature): Boolean

    fun getPublicKey(): PublicKey
}
