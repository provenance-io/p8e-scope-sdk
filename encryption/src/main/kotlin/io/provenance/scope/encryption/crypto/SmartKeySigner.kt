package io.provenance.scope.encryption.crypto

import com.fortanix.sdkms.v1.api.SignAndVerifyApi
import com.fortanix.sdkms.v1.model.DigestAlgorithm
import com.fortanix.sdkms.v1.model.SignRequest
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import io.provenance.scope.encryption.crypto.SignerImpl.Companion.PROVIDER
import io.provenance.scope.encryption.crypto.SignerImpl.Companion.SIGN_ALGO
import io.provenance.scope.encryption.proto.Common
import io.provenance.scope.encryption.proto.PK
import io.provenance.scope.encryption.ecies.ECUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.IllegalStateException
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.util.Base64

/**
 * About SmartKey
 *
 * SmartKey™ powered by Fortanix is the world’s first cloud service secured with Intel® SGX. With SmartKey, you can
 * securely generate, store, and use cryptographic keys and certificates, as well as other secrets such as passwords,
 * API keys, tokens, or any blob of data. Your business-critical applications and containers can integrate with
 * SmartKey using legacy cryptographic interfaces or using the native SmartKey RESTful interface.
 *
 * SmartKey uses built-in cryptography in Intel® Xeon® CPUs to help protect the customer’s keys and data from all
 * external agents, reducing the system complexity greatly by removing reliance on characteristics of the physical
 * boxes. Intel® SGX enclaves prevent access to customer’s keys or data by Equinix, Fortanix or any other cloud service
 * provider.
 *
 * Unlike many hardware security technologies, Intel® SGX is designed to help protect arbitrary x86 program code.
 * SmartKey uses Intel® SGX not only to help protect the keys and data but also all the application logic including
 * role based access control, account set up, and password recovery. The result is significantly improved security
 * for a key management service that offers the elasticity of modern cloud software and the hardware-based security
 * of an HSM appliance, all while drastically reducing initial and ongoing costs.
 *
 * SmartKey is designed to enable businesses to serve key management needs for all their applications, whether they are
 * operating in a public, private, or hybrid cloud.
 */
class SmartKeySigner(
    private val keyUuid: String,
    private val publicKey: PublicKey,
    private val signAndVerifyApi: SignAndVerifyApi
): SignerImpl {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private var signature: Signature? = null
    private var signatureRequest: SignRequest? = null

    private var verifying: Boolean = false

    /**
     * Using SmartKey to sign data.
     */
    override fun initSign() {
        signatureRequest = SignRequest()
            .hashAlg(DigestAlgorithm.SHA512)
            .deterministicSignature(true)
            .data(byteArrayOf())
    }

    override fun update(data: ByteArray, off: Int, res: Int) {
        signatureRequest?.data = data.copyOfRange(off, res)
    }


    override fun sign(): ByteArray = signAndVerifyApi.sign(keyUuid, signatureRequest).signature

    override fun sign(data: String): Common.Signature = sign(data.toByteArray())

    override fun sign(data: Message): Common.Signature = sign(data.toByteArray())

    override fun sign(data: ByteArray): Common.Signature {
        val signatureRequest = SignRequest()
            .hashAlg(DigestAlgorithm.SHA512)
            .data(data)
            .deterministicSignature(true)

        val signatureResponse = signAndVerifyApi.sign(keyUuid, signatureRequest)

        return Common.Signature.newBuilder()
            .setAlgo(SIGN_ALGO)
            .setProvider(PROVIDER)
            .setSignature(String(Base64.getEncoder().encode(signatureResponse.signature)))
            .setSigner(signer())
            .build()
            .takeIf { verify(publicKey, data, it) }
            .orThrow { IllegalStateException("can't verify signature - public cert may not match private key.") }
    }

    fun <T : Any, X : Throwable> T?.orThrow(supplier: () -> X) = this?.let { it } ?: throw supplier()

    override fun signer(): PK.SigningAndEncryptionPublicKeys =
        PK.SigningAndEncryptionPublicKeys.newBuilder()
            .setSigningPublicKey(
                PK.PublicKey.newBuilder()
                    .setCurve(PK.KeyCurve.SECP256K1)
                    .setType(PK.KeyType.ELLIPTIC)
                    .setPublicKeyBytes(
                        ByteString.copyFrom(
                            ECUtils.convertPublicKeyToBytes(getPublicKey())
                        )
                    )
                    .setCompressed(false)
                    .build()
            ).build()

    override fun update(data: ByteArray) {
        signatureRequest?.data(data)
    }

    override fun update(data: Byte) {
        signatureRequest?.data(mutableListOf(data).toByteArray())
    }

    override fun verify(publicKey: PublicKey, data: ByteArray, signature: Common.Signature): Boolean {
        val s = Signature.getInstance(signature.algo, signature.provider)
        s.initVerify(publicKey)
        s.update(data)
        return s.verify(Base64.getDecoder().decode(signature.signature))
    }

    /**
     * Return the public used to setup the signer.
     */
    override fun getPublicKey(): PublicKey = publicKey
}