package io.provenance.scope.util

import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.encryption.ecies.ECUtils
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
