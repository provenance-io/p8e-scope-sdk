package io.provenance.scope.sdk

import java.net.URI

data class ClientConfig(
    // caching
    val jarCacheSizeInBytes: Long,
    val specCacheSizeInBytes: Long,
    val recordCacheSizeInBytes: Long,

    // object-store
    val osGrpcUrl: URI,
    val osGrpcDeadlineMs: Long,
)
