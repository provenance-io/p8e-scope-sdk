package sample

import io.provenance.engine.crypto.getUnsignedBytes
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.objectstore.util.base64Decode
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.kethereum.crypto.CURVE
import org.kethereum.crypto.api.ec.ECDSASignature
import org.kethereum.crypto.impl.ec.EllipticCurveSigner
import java.security.KeyPair

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

data class StdSignature(
    val pub_key: StdPubKey,
    val signature: ByteArray
)

data class StdPubKey(
    val type: String,
    val value: ByteArray? = ByteArray(0)
)

typealias SignerFn = (ByteArray) -> List<StdSignature>
object PbSigner {
//    fun signerFor(keyPair: ECKeyPair): SignerFn = { bytes ->
//        bytes.let {
//            Hash.sha256(it)
//        }.let {
//            StdSignature(
//                pub_key = StdPubKey("tendermint/PubKeySecp256k1", keyPair.getCompressedPublicKey()),
//                signature = EllipticCurveSigner().sign(it, keyPair.privateKey.key, true).encodeAsBTC()
//            )
//        }.let {
//            listOf(it)
//        }
//    }

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

//    fun signerFor(signer: SignerImpl): SignerFn = { bytes ->
//        StdSignature(
//            pub_key = StdPubKey("tendermint/PubKeySecp256k1", (signer.getPublicKey() as BCECPublicKey).q.getEncoded(true)),
//            signature = signer.sign(bytes).signature.base64Decode().toECDSASignature(true).encodeAsBTC()
//        ).let {
//            listOf(it)
//        }
//    }
}

/*
DIRECT COPY
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

/** Cryptographic hash functions.  */
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
