package io.provenance.scope.util

import io.provenance.scope.proto.Util.UUID
import java.util.UUID.randomUUID


// -------------------------------------------------------------------
// -------------------------- Util.UUID ------------------------------
/**
 * Build random UUID
 */
fun randomProtoUuid(): UUID = UUID.newBuilder().setValue(randomUUID().toProtoUuid()).build()

/**
 * Build Proto UUID to String
 */
fun String.toProtoUuid(): UUID = java.util.UUID.fromString(this).toProtoUuid()

/**
 * Build UUID from java.util.UUID
 */
fun java.util.UUID.toProtoUuid(): UUID = UUID.newBuilder().setValue(this.toString()).build()

/**
 * Store UUID as string
 */
fun UUID.Builder.setValue(uuid: UUID): UUID.Builder = setValue(uuid.toString())


/**
 * Returns a UUID or null
 */
fun UUID.toUuidOrNull(): java.util.UUID? = if (this.value.isNotEmpty()) this.toUuid() else null
