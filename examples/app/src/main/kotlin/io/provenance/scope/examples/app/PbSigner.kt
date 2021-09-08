package io.provenance.scope.examples.app

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.kethereum.crypto.CURVE
import org.kethereum.crypto.api.ec.ECDSASignature
import org.kethereum.crypto.impl.ec.EllipticCurveSigner
import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

// NOTE:
// This whole namespace would not be needed once the provenance hdwallet is ready.

data class StdSignature(
    val pub_key: StdPubKey,
    val signature: ByteArray
)

data class StdPubKey(
    val type: String,
    val value: ByteArray? = ByteArray(0)
)

fun BigInteger.getUnsignedBytes(): ByteArray {
    val bytes = this.toByteArray()

    if (bytes[0] == 0x0.toByte()) {
        return bytes.drop(1).toByteArray()
    }

    return bytes
}

/**
 * encodeAsBTC returns the ECDSA signature as a ByteArray of r || s,
 * where both r and s are encoded into 32 byte big endian integers.
 */
fun ECDSASignature.encodeAsBTC(): ByteArray {
    // Canonicalize - In order to remove malleability,
    // we set s = curve_order - s, if s is greater than curve.Order() / 2.
    var sigS = this.s
    val HALF_CURVE_ORDER = CURVE.n.shiftRight(1)
    if (sigS > HALF_CURVE_ORDER) {
        sigS = CURVE.n.subtract(sigS)
    }

    val sBytes = sigS.getUnsignedBytes()
    val rBytes = this.r.getUnsignedBytes()

    require(rBytes.size <= 32) { "cannot encode r into BTC Format, size overflow (${rBytes.size} > 32)" }
    require(sBytes.size <= 32) { "cannot encode s into BTC Format, size overflow (${sBytes.size} > 32)" }

    val signature = ByteArray(64)
    // 0 pad the byte arrays from the left if they aren't big enough.
    System.arraycopy(rBytes, 0, signature, 32 - rBytes.size, rBytes.size)
    System.arraycopy(sBytes, 0, signature, 64 - sBytes.size, sBytes.size)

    return signature
}

object Hash {
    /**
     * Generates SHA-256 digest for the given `input`.
     *
     * @param input The input to digest
     * @return The hash value for the given input
     * @throws RuntimeException If we couldn't find any SHA-256 provider
     */
    fun sha256(input: ByteArray?): ByteArray {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(input)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Couldn't find a SHA-256 provider", e)
        }
    }
}

typealias SignerFn = (ByteArray) -> List<StdSignature>
object PbSigner {
    fun signerFor(keyPair: KeyPair): SignerFn = { bytes ->
        bytes.let {
            Hash.sha256(it)
        }.let {
            val privateKey = (keyPair.private as BCECPrivateKey).s
            StdSignature(
                pub_key = StdPubKey("tendermint/PubKeySecp256k1", (keyPair.public as BCECPublicKey).q.getEncoded(true)) ,
                signature = EllipticCurveSigner().sign(it, privateKey, true).encodeAsBTC()
            )
        }.let {
            listOf(it)
        }
    }
}
