package io.provenance.scope.encryption.crypto

import com.fortanix.sdkms.v1.model.DigestAlgorithm
import com.fortanix.sdkms.v1.model.SignRequest
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import io.provenance.scope.encryption.crypto.SignerImpl.Companion.DEFAULT_HASH
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.orThrow
import io.provenance.scope.proto.Common
import io.provenance.scope.proto.PK
import java.lang.IllegalStateException
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider

class ApiSigner(
    private val publicKey: PublicKey,
    private val apiClient: ApiSignerClient,
) : SignerImpl {
    
    init {
        Security.addProvider(BouncyCastleProvider())
    }
    
    override var deterministic = true
    
    override var hashType: SignerImpl.Companion.HashType = DEFAULT_HASH
        set(value) {
            field = value
            
            // Reset digest
            messageDigest = MessageDigest.getInstance(hashType.value)
        }
    
    private var messageDigest = MessageDigest.getInstance(hashType.value)
    
    override fun sign(data: String) = sign(data.toByteArray())

    override fun sign(data: Message) = sign(data.toByteArray())

    override fun sign(data: ByteArray): Common.Signature {
        val signature = apiClient.sign(data)
        
        return Common.Signature.newBuilder()
            .setAlgo(signAlgorithm)
            .setProvider(SignerImpl.PROVIDER)
            .setSignature(String(Base64.getEncoder().encode(signature)))
            .setSigner(signer())
            .build()
            .takeIf { verify(getPublicKey(), data, it) }
            .orThrow { IllegalStateException("can't verify signature - public cert may not match private key.") }
    }

    override fun sign(): ByteArray =
        apiClient.sign(messageDigest.digest())

    override fun update(data: Byte) =
        messageDigest.update(data)

    override fun update(data: ByteArray) =
        messageDigest.update(data)
    
    override fun update(data: ByteArray, off: Int, len: Int) =
        messageDigest.update(data, off, len)
    
    override fun initSign() {
        // Not needed
    }

    override fun signer(): PK.SigningAndEncryptionPublicKeys =
        PK.SigningAndEncryptionPublicKeys.newBuilder()
            .setSigningPublicKey(PK.PublicKey.newBuilder()
                .setCurve(PK.KeyCurve.SECP256K1)
                .setType(PK.KeyType.ELLIPTIC)
                .setPublicKeyBytes(
                    ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(getPublicKey())))
                .setCompressed(false)
                .build())
            .build()
    

    override fun verify(publicKey: PublicKey, data: ByteArray, signature: Common.Signature): Boolean {
        val s = Signature.getInstance(signature.algo, signature.provider)
        s.initVerify(publicKey)
        s.update(data)
        return s.verify(Base64.getDecoder().decode(signature.signature))
    }

    override fun getPublicKey() = publicKey
}