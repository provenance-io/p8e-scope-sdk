package io.provenance.scope.sdk

import java.net.URI

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
)
