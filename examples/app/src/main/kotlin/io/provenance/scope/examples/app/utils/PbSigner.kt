package io.provenance.scope.examples.app.utils

import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.objectstore.util.base64Decode
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.kethereum.crypto.CURVE
import org.kethereum.crypto.api.ec.ECDSASignature
import org.kethereum.crypto.impl.ec.canonicalise
import java.math.BigInteger
import kotlin.experimental.and

// NOTE:
// This whole namespace would not be needed once the provenance hdwallet is ready.

data class StdSignature(
    val pub_key: StdPubKey,
    val signature: ByteArray,
)

data class StdPubKey(
    val type: String,
    val value: ByteArray,
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

typealias SignerFn = (ByteArray) -> List<StdSignature>
object PbSigner {
    fun signerFor(signer: SignerImpl): SignerFn = { bytes ->
        StdSignature(
            pub_key = StdPubKey("tendermint/PubKeySecp256k1", (signer.getPublicKey() as BCECPublicKey).q.getEncoded(true)),
            signature = signer.sign(bytes).signature.base64Decode().toECDSASignature(true).encodeAsBTC()
        ).let { listOf(it) }
    }

    fun ByteArray.extractRAndS(): Pair<BigInteger, BigInteger> {
        val startR = if (this[1] and 0x80.toByte() != 0.toByte()) 3 else 2
        val lengthR = this[startR + 1].toInt()
        val startS = startR + 2 + lengthR
        val lengthS = this[startS + 1].toInt()

        return BigInteger(this, startR + 2, lengthR) to BigInteger(this, startS + 2, lengthS)
    }

    fun ByteArray.toECDSASignature(canonical: Boolean) = extractRAndS().let { (r, s) ->
        ECDSASignature(r, s)
    }.let { if (canonical) { it.canonicalise() } else { it } }
}
