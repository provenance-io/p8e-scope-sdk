package io.provenance.scope.util

import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import io.provenance.scope.contract.proto.Utils
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Base64
import java.util.UUID

fun ByteArray.toHexString() = BaseEncoding.base16().encode(this)

fun <T: Any> T?.or(supplier: () -> T) = this?.let { it } ?: supplier()

fun ByteArray.sha512(): ByteArray = Hashing.sha512().hashBytes(this).asBytes()

fun ByteArray.base64Sha512() = this.sha512().base64String()

fun ByteArray.base64String() = String(Base64.getEncoder().encode(this))

fun Utils.UUIDOrBuilder.toUuidProv(): UUID = UUID.fromString(value)

fun String.toUuidProv(): UUID = UUID.fromString(this)

/**
 * Stack trace from exception for logging purposes
 */
fun <T: Throwable> T.toMessageWithStackTrace(): String {
    val writer = StringWriter()
    val printWriter = PrintWriter(writer)
    printWriter.write("$message\n\n")
    printStackTrace(printWriter)
    return writer.toString()
}
