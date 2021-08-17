package io.provenance.scope.encryption.domain

data class Signature(
    val signature: ByteArray,
    val publicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Signature

        if (!signature.contentEquals(other.signature)) return false
        if (!publicKey.contentEquals(other.publicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signature.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}
