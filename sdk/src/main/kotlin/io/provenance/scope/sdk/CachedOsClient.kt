package io.provenance.scope.sdk

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.provenance.scope.objectstore.client.OsClient
import java.security.PublicKey

data class RecordCacheValue(val publicKey: PublicKey, val bytes: ByteArray)

class CachedOsClient(config: ClientConfig, osClient: OsClient) {

    val recordCache: Cache<String, RecordCacheValue> = CacheBuilder.newBuilder()
        .maximumWeight(config.recordCacheSizeInBytes)
        .weigher { _: String, value: RecordCacheValue ->  value.bytes.size }
        .build()
}
