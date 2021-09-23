package io.provenance.scope.objectstore.util

import com.google.common.hash.Hashing
import com.google.protobuf.ByteString
import io.provenance.objectstore.proto.PublicKeys
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.objectstore.proto.Utils
import io.provenance.scope.proto.PK
import io.provenance.scope.util.toHexString
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.Base64
import java.util.UUID

fun <T : Any> T?.orThrowNotFound(message: String) = this ?: throw NotFoundException(message)

class NotFoundException(message: String) : RuntimeException(message)

class FileExistsException: RuntimeException()

fun <T: Any, X: Throwable> T?.orThrow(supplier: () -> X) = this ?: throw supplier()

fun <T: Any?> T?.orGet(supplier: () -> T) = this ?: supplier()

fun ByteArray.base64Encode(): ByteArray = Base64.getEncoder().encode(this)
fun ByteArray.base64EncodeString(): String = String(this.base64Encode())
fun String.base64Decode(): ByteArray = Base64.getDecoder().decode(this)

fun ByteArray.sha256() = Hashing.sha256().hashBytes(this).asBytes()
fun ByteArray.loBytes() = slice(0 until 16)
fun ByteArray.sha256LoBytes(): ByteArray {
    return Hashing.sha256().hashBytes(this)
        .asBytes()
        .loBytes()
        .toByteArray()
}

// TODO add test to go to byte array and back to uuid
fun ByteArray.toUuid(): UUID {
    require(size == 16) { "ByteArray must be size 16" }

    val buffer = ByteBuffer.wrap(this)

    val first = buffer.long
    val second = buffer.long

    return UUID(first, second)
}
fun UUID.toByteArray(): ByteArray {
    val buffer = ByteBuffer.wrap(ByteArray(16))

    buffer.putLong(this.leastSignificantBits)
    buffer.putLong(this.mostSignificantBits)

    return buffer.array()
}

fun PublicKey.toPublicKeyProtoOS(): Utils.PublicKey =
    with (ECUtils.convertPublicKeyToBytes(this)) {
        Utils.PublicKey.newBuilder()
            .setSecp256K1(ByteString.copyFrom(this))
            .build()
    }

fun PublicKey.toHex() = toPublicKeyProtoOS().toByteArray().toHexString()

fun PK.PublicKey.toPublicKey(): PublicKey =
    this.let {
        require(it.curve == PK.KeyCurve.SECP256K1) {"Unsupported Key Curve"}
        ECUtils.convertBytesToPublicKey(it.publicKeyBytes.toByteArray())
    }
