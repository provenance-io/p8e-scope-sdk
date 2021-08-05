package io.provenance.scope.util

import io.provenance.engine.crypto.Bech32
import io.provenance.engine.crypto.toBech32Data
import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.encryption.crypto.Hash
import io.provenance.scope.encryption.ecies.ECUtils
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import java.security.PublicKey

fun PublicKey.toPublicKeyProto(): PublicKeys.PublicKey =
    PublicKeys.PublicKey.newBuilder()
        .setCurve(PublicKeys.KeyCurve.SECP256K1)
        .setType(PublicKeys.KeyType.ELLIPTIC)
        .setPublicKeyBytes(ECUtils.convertPublicKeyToBytes(this).toByteString())
        .setCompressed(false)
        .build()

fun PublicKeys.PublicKey.toHex() = this.toByteArray().toHexString()

fun PublicKey.toHex() = toPublicKeyProto().toHex()

fun PublicKey.getAddress(mainNet: Boolean): String =
    (this as BCECPublicKey).q.getEncoded(true)
    .let {
        Hash.sha256hash160(it)
    }.let {
        val prefix = if (mainNet) Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX
        it.toBech32Data(prefix).address
    }
