package io.provenance.scope.sdk

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.provenance.scope.encryption.domain.inputstream.DIMEInputStream
import io.provenance.scope.objectstore.client.OsClient
import java.security.PublicKey

// TODO add semaphore so that only N objects are queried at a time - add this amount to the config

data class RecordCacheValue(val publicKey: PublicKey, val bytes: ByteArray)

class CachedOsClient(config: ClientConfig, val osClient: OsClient) {

    val recordCache: Cache<String, RecordCacheValue> = CacheBuilder.newBuilder()
        .maximumWeight(config.recordCacheSizeInBytes)
        .weigher { _: String, value: RecordCacheValue ->  value.bytes.size }
        .build()

    // TODO for now just forward all requests but this needs to get cached
    fun getRecord(hash: ByteArray, publicKey: PublicKey): DIMEInputStream {
        return osClient.get(hash, publicKey)
    }
}
