package io.provenance.scope.sdk

import java.net.URI

/**
 * Configuration values to supply to a [SharedClient] instance
 */
data class ClientConfig(
    // caching
    val cacheJarSizeInBytes: Long,
    val cacheSpecSizeInBytes: Long,
    val cacheRecordSizeInBytes: Long,

    // object-store
    val osGrpcUrl: URI,
    val osGrpcDeadlineMs: Long = 30_000L,
    val osConcurrencySize: Short = 4,
    val osDecryptionWorkerThreads: Short = 2,

    // provenance
    val mainNet: Boolean,

    // extra headers to bind to grpc stubs
    val extraHeaders: Map<String, String> = emptyMap()
)
