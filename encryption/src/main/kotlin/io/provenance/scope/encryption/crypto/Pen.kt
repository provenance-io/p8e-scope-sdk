package io.provenance.scope.encryption.crypto

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import io.provenance.scope.encryption.crypto.SignerImpl.Companion.PROVIDER
import io.provenance.scope.encryption.crypto.SignerImpl.Companion.SIGN_ALGO
import io.provenance.scope.encryption.proto.Common
import io.provenance.scope.encryption.proto.PK
import io.provenance.scope.encryption.ecies.ECUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.util.*

class Pen(
    private val keyPair: KeyPair
): SignerImpl {

    val privateKey: PrivateKey = keyPair.private

    val signature: Signature = Signature.getInstance(
        SIGN_ALGO,
        PROVIDER
    )

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    /**
     * Return the signing public key.
     */
    override fun getPublicKey(): PublicKey = keyPair.public

    /**
     * Sign protobuf data.
     */
    override fun sign(data: Message) = sign(data.toByteArray())

    /**
     * Sign string data.
     */
    override fun sign(data: String) = sign(data.toByteArray())

    /**
     * Sign byte array.
     */
    override fun sign(data: ByteArray): Common.Signature {
        signature.initSign(privateKey)
        signature.update(data)

        return Common.Signature.newBuilder()
            .setAlgo(SIGN_ALGO)
            .setProvider(PROVIDER)
            .setSignature(String(Base64.getEncoder().encode(signature.sign())))
            .setSigner(signer())
            .build()
            .takeIf { verify(keyPair.public, data, it) }
            .orThrow { IllegalStateException("can't verify signature - public cert may not match private key.") }
    }
    fun <T : Any, X : Throwable> T?.orThrow(supplier: () -> X) = this ?: throw supplier()

    override fun sign(): ByteArray = signature.sign()

    override fun update(data: ByteArray) = signature.update(data)

    override fun update(data: ByteArray, off: Int, res: Int) {
        signature.update(data, off, res)
    }

    override fun update(data: Byte) { signature.update(data) }

    override fun initSign() {
        signature.initSign(keyPair.private)
    }

    override fun verify(publicKey: PublicKey, data: ByteArray, signature: Common.Signature): Boolean =
        Signature.getInstance(signature.algo, signature.provider)
            .apply {
                initVerify(publicKey)
                update(data)
            }.verify(Base64.getDecoder().decode(signature.signature))

    override fun signer(): PK.SigningAndEncryptionPublicKeys =
        PK.SigningAndEncryptionPublicKeys.newBuilder()
            .setSigningPublicKey(PK.PublicKey.newBuilder()
                .setCurve(PK.KeyCurve.SECP256K1)
                .setType(PK.KeyType.ELLIPTIC)
                .setPublicKeyBytes(
                    ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(keyPair.public)))
                .setCompressed(false)
                .build())
            .build()
}
