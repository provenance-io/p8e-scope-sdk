package io.provenance.scope.util

import com.google.protobuf.Timestamp
import com.google.protobuf.TimestampOrBuilder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

// -------------------------------------------------------------------
// -------------------------- Protobuf Timestamp ---------------------
/**
 * Get Timestamp as OffsetDateTime (system time zone)
 */
fun TimestampOrBuilder.toOffsetDateTime(): OffsetDateTime = toOffsetDateTime(ZoneId.systemDefault())

/**
 * Get Timestamp as OffsetDateTime
 */
fun TimestampOrBuilder.toOffsetDateTime(zoneId: ZoneId): OffsetDateTime = OffsetDateTime.ofInstant(toInstant(), zoneId)

/**
 * Get Timestamp as Instant
 */
fun TimestampOrBuilder.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())

/**
 * Quick convert OffsetDateTime to Timestamp
 */
fun OffsetDateTime.toProtoTimestamp(): Timestamp = Timestamp.newBuilder().setValue(this).build()

/**
 * Store OffsetDateTime as Timestamp (UTC)
 */
fun Timestamp.Builder.setValue(odt: OffsetDateTime): Timestamp.Builder = setValue(odt.toInstant())

/**
 * Store Instant as Timestamp (UTC)
 */
fun Timestamp.Builder.setValue(instant: Instant): Timestamp.Builder {
    this.nanos = instant.nano
    this.seconds = instant.epochSecond
    return this
}

/**
 * Right Meow
 */
fun TimestampOrBuilder.now(): Timestamp = Instant.now().let { time -> Timestamp.newBuilder().setSeconds(time.epochSecond).setNanos(time.nano).build() }
