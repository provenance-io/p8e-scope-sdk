package io.provenance.scope.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Data involved with a Bech32 address */
data class Bech32Data(val hrp: String, val data: ByteArray) {

    /**
     * The encapsulated data returned as a Hexadecimal string
     */
    val hexData = this.data.joinToString("") { "%02x".format(it) }

    /**
     * The Bech32 encoded value of the data prefixed with the human readable portion and
     * protected by an appended checksum.
     */
    val address = Bech32.encode(hrp, data)

    /**
     * The Bech32 Address toString prints state information for debugging purposes.
     * @see address() for the bech32 encoded address string output.
     */
    override fun toString(): String {
        return "bech32 : ${this.address}\nhuman: ${this.hrp} \nbytes: ${this.hexData}"
        /*
        bech32 : provenance1gx58vp8pryh3jkvxnkvzmd0hqmqqnyqxrtvheq
        human: provenance
        bytes: 41A87604E1192F1959869D982DB5F706C0099006
         */
    }

    /** equals implementation for a Bech32Data object. */
    override fun equals(other: kotlin.Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Bech32Data
        return this.hrp == other.hrp &&
            this.data.contentEquals(other.data)
    }

    /** equals implementation for a Bech32Data object. */
    override fun hashCode(): Int {
        var result = hrp.hashCode()
        result = 31 * result + this.data.contentHashCode()
        return result
    }
}

class Bech32 {
    companion object {
        private const val CHECKSUM_SIZE = 6
        private const val MIN_VALID_LENGTH = 8
        private const val MAX_VALID_LENGTH = 90
        private const val MIN_VALID_CODEPOINT = 33
        private const val MAX_VALID_CODEPOINT = 126

        private const val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        private val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

        /** Decodes a Bech32 String */
        fun decode(bech32: String): Bech32Data {
            require(bech32.length in MIN_VALID_LENGTH..MAX_VALID_LENGTH) { "invalid bech32 string length" }
            require(bech32.toCharArray().none { c -> c.toInt() < MIN_VALID_CODEPOINT || c.toInt() > MAX_VALID_CODEPOINT })
            { "invalid character in bech32: ${bech32.toCharArray().map { c -> c.toInt() }
                .filter { c -> c < MIN_VALID_CODEPOINT || c > MAX_VALID_CODEPOINT }}" }

            require(bech32 == bech32.toLowerCase() || bech32 == bech32.toUpperCase())
            { "bech32 must be either all upper or lower case" }
            require(bech32.substring(1).dropLast(CHECKSUM_SIZE).contains('1')) { "invalid index of '1'" }

            val hrp = bech32.substringBeforeLast('1').toLowerCase()
            val dataString = bech32.substringAfterLast('1').toLowerCase()

            require(dataString.toCharArray().all { c -> charset.contains(c) }) { "invalid data encoding character in bech32"}

            val dataBytes = dataString.map { c -> charset.indexOf(c).toByte() }.toByteArray()
            val checkBytes = dataString.takeLast(CHECKSUM_SIZE).map { c -> charset.indexOf(c).toByte() }.toByteArray()

            val actualSum = checksum(hrp, dataBytes.dropLast(CHECKSUM_SIZE).toTypedArray())
            require(1 == polymod(expandHrp(hrp).plus(dataBytes.map { d -> d.toInt() }))) { "checksum failed: $checkBytes != $actualSum" }

            return Bech32Data(hrp, convertBits(dataBytes.dropLast(CHECKSUM_SIZE).toByteArray(), 5, 8, false))
        }

        /**
         * Encodes the provided hrp and data to a Bech32 address string.
         * @param hrp the human readable portion (prefix) to use.
         * @param eightBitData an array of 8-bit encoded bytes.
         */
        fun encode(hrp: String, eightBitData: ByteArray) =
            encodeFiveBitData(hrp, convertBits(eightBitData, 8, 5, true))

        /** Encodes 5-bit bytes (fiveBitData) with a given human readable portion (hrp) into a bech32 string. */
        private fun encodeFiveBitData(hrp: String, fiveBitData: ByteArray): String {
            return (fiveBitData.plus(checksum(hrp, fiveBitData.toTypedArray()))
                .map { b -> charset[b.toInt()] }).joinToString("", hrp + "1")
        }

        /**
         * ConvertBits regroups bytes with toBits set based on reading groups of bits as a continuous stream group by fromBits.
         * This process is used to convert from base64 (from 8) to base32 (to 5) or the inverse.
         */
        private fun convertBits(data: ByteArray, fromBits: Int, toBits: Int, pad: Boolean): ByteArray {
            require (fromBits in 1..8 && toBits in 1..8) { "only bit groups between 1 and 8 are supported"}

            var acc = 0
            var bits = 0
            val out = ByteArrayOutputStream(64)
            val maxv = (1 shl toBits) - 1
            val maxAcc = (1 shl (fromBits + toBits - 1)) - 1

            for (b in data) {
                val value = b.toInt() and 0xff
                if ((value ushr fromBits) != 0) {
                    throw IllegalArgumentException(String.format("Input value '%X' exceeds '%d' bit size", value, fromBits))
                }
                acc = ((acc shl fromBits) or value) and maxAcc
                bits += fromBits
                while (bits >= toBits) {
                    bits -= toBits
                    out.write((acc ushr bits) and maxv)
                }
            }
            if (pad) {
                if (bits > 0) {
                    out.write((acc shl (toBits - bits)) and maxv)
                }
            } else if (bits >= fromBits || ((acc shl (toBits - bits)) and maxv) != 0) {
                throw IllegalArgumentException("Could not convert bits, invalid padding")
            }
            return out.toByteArray()
        }

        /** Calculates a bech32 checksum based on BIP 173 specification */
        private fun checksum(hrp: String, data: Array<Byte>): ByteArray {
            val values = expandHrp(hrp)
                .plus(data.map { d -> d.toInt() })
                .plus(Array(6){ 0 }.toIntArray())

            val poly = polymod(values) xor 1

            return (0..5).map {
                ((poly shr (5 * (5-it))) and 31).toByte()
            }.toByteArray()
        }

        /** Expands the human readable prefix per BIP173 for Checksum encoding */
        private fun expandHrp(hrp: String) =
            hrp.map { c -> c.toInt() shr 5 }
                .plus(0)
                .plus(hrp.map { c -> c.toInt() and 31 })
                .toIntArray()

        /** Polynomial division function for checksum calculation.  For details see BIP173 */
        private fun polymod(values: IntArray): Int {
            var chk = 1
            return values.map { v ->
                val b = chk shr 25
                chk = ((chk and 0x1ffffff) shl 5) xor v
                (0..4).map {
                    if (((b shr it) and 1) == 1) {
                        chk = chk xor gen[it]
                    }
                }
            }.let { chk }
        }
    }
}

const val PREFIX_SCOPE = "scope"
const val PREFIX_SESSION = "session"
const val PREFIX_RECORD = "record"
const val PREFIX_SCOPE_SPECIFICATION = "scopespec"
const val PREFIX_CONTRACT_SPECIFICATION = "contractspec"
const val PREFIX_RECORD_SPECIFICATION = "recspec"

const val KEY_SCOPE: Byte = 0x00
const val KEY_SESSION: Byte = 0x01
const val KEY_RECORD: Byte = 0x02
const val KEY_SCOPE_SPECIFICATION: Byte = 0x04 // Note that this is not in numerical order.
const val KEY_CONTRACT_SPECIFICATION: Byte = 0x03
const val KEY_RECORD_SPECIFICATION: Byte = 0x05

data class MetadataAddress internal constructor(val bytes: ByteArray) {
    companion object {
        /** Create a MetadataAddress for a Scope. */
        fun forScope(scopeUuid: UUID) =
            MetadataAddress(byteArrayOf(KEY_SCOPE).plus(uuidAsByteArray(scopeUuid)))

        /** Create a MetadataAddress for a Session. */
        fun forSession(scopeUuid: UUID, sessionUuid: UUID) =
            MetadataAddress(byteArrayOf(KEY_SESSION).plus(uuidAsByteArray(scopeUuid)).plus(uuidAsByteArray(sessionUuid)))

        /** Create a MetadataAddress for a Record. */
        fun forRecord(scopeUuid: UUID, recordName: String): MetadataAddress {
            if (recordName.isBlank()) {
                throw IllegalArgumentException("Invalid recordName: cannot be empty or blank.")
            }
            return MetadataAddress(byteArrayOf(KEY_RECORD).plus(uuidAsByteArray(scopeUuid)).plus(asHashedBytes(recordName)))
        }

        /** Create a MetadataAddress for a Scope Specification. */
        fun forScopeSpecification(scopeSpecUuid: UUID) =
            MetadataAddress(byteArrayOf(KEY_SCOPE_SPECIFICATION).plus(uuidAsByteArray(scopeSpecUuid)))

        /** Create a MetadataAddress for a Contract Specification. */
        fun forContractSpecification(contractSpecUuid: UUID) =
            MetadataAddress(byteArrayOf(KEY_CONTRACT_SPECIFICATION).plus(uuidAsByteArray(contractSpecUuid)))

        /** Create a MetadataAddress for a Record Specification. */
        fun forRecordSpecification(contractSpecUuid: UUID, recordSpecName: String): MetadataAddress {
            if (recordSpecName.isBlank()) {
                throw IllegalArgumentException("Invalid recordSpecName: cannot be empty or blank.")
            }
            return MetadataAddress(byteArrayOf(KEY_RECORD_SPECIFICATION).plus(uuidAsByteArray(contractSpecUuid)).plus(asHashedBytes(recordSpecName)))
        }

        /** Create a MetadataAddress object from a bech32 address representation of a MetadataAddress. */
        fun fromBech32(bech32Value: String): MetadataAddress {
            val (hrp, data) = Bech32.decode(bech32Value)
            validateBytes(data)
            val prefix = getPrefixFromKey(data[0])
            if (hrp != prefix) {
                throw IllegalArgumentException("Incorrect HRP: Expected ${prefix}, Actual: ${hrp}.")
            }
            return MetadataAddress(data)
        }

        /** Create a MetadataAddress from a ByteArray. */
        fun fromBytes(bytes: ByteArray): MetadataAddress {
            validateBytes(bytes)
            return MetadataAddress(bytes)
        }

        /** Get the prefix that corresponds to the provided key Byte. */
        private fun getPrefixFromKey(key: Byte) =
            when (key) {
                KEY_SCOPE -> PREFIX_SCOPE
                KEY_SESSION -> PREFIX_SESSION
                KEY_RECORD -> PREFIX_RECORD
                KEY_SCOPE_SPECIFICATION -> PREFIX_SCOPE_SPECIFICATION
                KEY_CONTRACT_SPECIFICATION -> PREFIX_CONTRACT_SPECIFICATION
                KEY_RECORD_SPECIFICATION -> PREFIX_RECORD_SPECIFICATION
                else -> {
                    throw IllegalArgumentException("Invalid key: $key")
                }
            }

        /** Checks that the data has a correct key and length. Throws IllegalArgumentException if not. */
        private fun validateBytes(bytes: ByteArray) {
            val expectedLength = when (bytes[0]) {
                KEY_SCOPE -> 17
                KEY_SESSION -> 33
                KEY_RECORD -> 33
                KEY_SCOPE_SPECIFICATION -> 17
                KEY_CONTRACT_SPECIFICATION -> 17
                KEY_RECORD_SPECIFICATION -> 33
                else -> {
                    throw IllegalArgumentException("Invalid key: ${bytes[0]}")
                }
            }
            if (expectedLength != bytes.size) {
                throw IllegalArgumentException("Incorrect data length for type ${getPrefixFromKey(bytes[0])}: Expected ${expectedLength}, Actual: ${bytes.size}.")
            }
        }

        /** Converts a UUID to a ByteArray. */
        private fun uuidAsByteArray(uuid: UUID): ByteArray {
            val b = ByteBuffer.wrap(ByteArray(16))
            b.putLong(uuid.mostSignificantBits)
            b.putLong(uuid.leastSignificantBits)
            return b.array()
        }

        /** Converts a ByteArray to a UUID. */
        private fun byteArrayAsUuid(data: ByteArray): UUID {
            val uuidBytes = ByteArray(16)
            if (data.size >= 16) {
                data.copyInto(uuidBytes, 0, 0, 16)
            } else if (data.isNotEmpty()) {
                data.copyInto(uuidBytes, 0, 0, data.size)
            }
            val bb = ByteBuffer.wrap(uuidBytes)
            val mostSig = bb.long
            val leastSig = bb.long
            return UUID(mostSig, leastSig)
        }

        /** Hashes a string and gets the bytes desired for a MetadataAddress. */
        private fun asHashedBytes(str: String) =
            MessageDigest.getInstance("SHA-256").digest(str.trim().toLowerCase().toByteArray()).copyOfRange(0, 16)
    }

    /** Gets the key byte for this MetadataAddress. */
    fun getKey() = this.bytes[0]

    /** Gets the prefix string for this MetadataAddress, e.g. "scope". */
    fun getPrefix() = getPrefixFromKey(this.bytes[0])

    /** Gets the set of bytes for the primary uuid part of this MetadataAddress as a UUID. */
    fun getPrimaryUuid() = byteArrayAsUuid(this.bytes.copyOfRange(1,17))

    /** Gets the set of bytes for the secondary part of this MetadataAddress. */
    fun getSecondaryBytes() = if (this.bytes.size <= 17) byteArrayOf() else bytes.copyOfRange(17, this.bytes.size)

    /** returns this MetadataAddress as a bech32 address string, e.g. "scope1qzge0zaztu65tx5x5llv5xc9ztsqxlkwel" */
    override fun toString() = Bech32.encode(getPrefixFromKey(this.bytes[0]), this.bytes)

    /** hashCode implementation for a MetadataAddress. */
    override fun hashCode() = this.bytes.contentHashCode()

    override fun equals(other: kotlin.Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is MetadataAddress) {
            return false
        }
        return this.bytes.contentEquals(other.bytes)
    }
}
