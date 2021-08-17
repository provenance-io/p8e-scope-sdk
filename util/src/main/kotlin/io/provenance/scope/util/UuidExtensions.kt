package io.provenance.scope.util

import io.provenance.scope.contract.proto.Utils
import java.util.*
import io.provenance.scope.contract.proto.Utils.UUID
import java.util.UUID.randomUUID


// -------------------------------------------------------------------
// -------------------------- Util.UUID ------------------------------
/**
 * Build random UUID
 */
fun randomProtoUuidProv(): UUID = UUID.newBuilder().setValueProv(randomUUID().toProtoUuidProv()).build()

/**
 * Build Proto UUID to String
 */
fun String.toProtoUuidProv(): UUID = java.util.UUID.fromString(this).toProtoUuidProv()

/**
 * Build UUID from java.util.UUID
 */
fun java.util.UUID.toProtoUuidProv(): UUID = UUID.newBuilder().setValue(this.toString()).build()

/**
 * Store UUID as string
 */
fun UUID.Builder.setValueProv(uuid: UUID): UUID.Builder = setValue(uuid.toString())


/**
 * Returns a UUID or null
 */
fun UUID.toUuidOrNullProv(): java.util.UUID? = if (this.value.isNotEmpty()) this.toUuidProv() else null
