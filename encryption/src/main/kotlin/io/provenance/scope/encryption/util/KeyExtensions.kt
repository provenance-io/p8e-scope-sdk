package io.provenance.scope.encryption.util

// import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.proto.PK
import io.provenance.scope.util.crypto.Bech32
import io.provenance.scope.util.crypto.Hash
import io.provenance.scope.util.crypto.toBech32Data
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.util.encoders.Hex
import java.security.PrivateKey
import java.security.PublicKey

// todo: why do we have two copies of this PublicKey proto?
// fun PK.PublicKey.toContractPublicKey() = PublicKeys.PublicKey.parseFrom(toByteArray())
// fun PublicKeys.PublicKey.toEncryptionPublicKey() = PK.PublicKey.parseFrom(toByteArray())

// fun PublicKey.toPublicKeyProto(): PublicKeys.PublicKey =
//     PublicKeys.PublicKey.newBuilder()
//         .setCurve(PublicKeys.KeyCurve.SECP256K1)
//         .setType(PublicKeys.KeyType.ELLIPTIC)
//         .setPublicKeyBytes(ECUtils.convertPublicKeyToBytes(this).toByteString())
//         .setCompressed(false)
//         .build()
//
// fun PublicKeys.PublicKey.toHex() = this.toByteArray().toHexString()
//
// fun PublicKey.toPublicKeyProtoOS(): PublicKeys.PublicKey =
//     PublicKeys.PublicKey.newBuilder()
//         .setPublicKeyBytes(ECUtils.convertPublicKeyToBytes(this).toByteString())
//         .build()

//fun PK.PublicKey.toPublicKeyProto(): PublicKeys.PublicKey =
//    PublicKeys.PublicKey.newBuilder()
//        .setCurve(PublicKeys.KeyCurve.SECP256K1)
//        .setType(PublicKeys.KeyType.ELLIPTIC)
//        .setPublicKeyBytes(ECUtils.convertPublicKeyToBytes(this).toByteString())
//        .setCompressed(false)
//        .build()
//
//fun PublicKeys.PublicKey.toHex() = this.toByteArray().toHexString()
//
//fun PK.PublicKey.toHex() = toPublicKeyProto().toHex()

fun PK.PrivateKey.toPrivateKey(): PrivateKey =
    this.let {
        require(it.curve == PK.KeyCurve.SECP256K1) { "Unsupported Key Curve" }
        ECUtils.convertBytesToPrivateKey(it.keyBytes.toByteArray())
    }

fun PK.PublicKey.toPublicKey(): PublicKey =
    this.let {
        require(it.curve == PK.KeyCurve.SECP256K1) {"Unsupported Key Curve"}
        ECUtils.convertBytesToPublicKey(it.publicKeyBytes.toByteArray())
    }

fun String.toPublicKeyProto(): PK.PublicKey = PK.PublicKey.parseFrom(Hex.decode(this))

fun String.toPrivateKeyProto(): PK.PrivateKey = PK.PrivateKey.parseFrom(Hex.decode(this))

fun String.toJavaPublicKey() = toPublicKeyProto().toPublicKey()
fun String.toJavaPrivateKey() = toPrivateKeyProto().toPrivateKey()

fun PublicKey.getAddress(mainNet: Boolean): String =
    (this as BCECPublicKey).q.getEncoded(true)
        .let {
            Hash.sha256hash160(it)
        }.let {
            val prefix = if (mainNet) Bech32.PROVENANCE_MAINNET_ACCOUNT_PREFIX else Bech32.PROVENANCE_TESTNET_ACCOUNT_PREFIX
            it.toBech32Data(prefix).address
        }

