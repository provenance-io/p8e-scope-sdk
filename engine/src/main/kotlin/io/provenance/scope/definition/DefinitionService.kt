package io.provenance.scope.definition

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.provenance.scope.classloader.MemoryClassLoader
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.util.forThread
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.security.PublicKey

class DefinitionService(
    private val osClient: CachedOsClient,
    private val memoryClassLoader: MemoryClassLoader = MemoryClassLoader("", ByteArrayInputStream(ByteArray(0)))
) {

    companion object {
        private val byteCache: Cache<ByteCacheKey, ByteArray> = CacheBuilder.newBuilder()
            .maximumWeight(10000000) // todo: set via config, maybe need to move Config.kt to some shared project
            .weigher { _: ByteCacheKey, value: ByteArray -> value.size }
            .build()

        data class ByteCacheKey(
            val publicKey: PublicKey,
            val hash: String
        )
    }

    private val parseFromCache: Cache<String, Method> = CacheBuilder.newBuilder()
        .maximumSize(1000) // todo: make configurable, is there a way to do this by weight/size? Not sure how to quanitfy the 'size' of a class
        .build()

    fun addJar(
        encryptionKeyRef: KeyRef,
        definition: DefinitionSpec,
        signaturePublicKey: PublicKey? = null
    ) {
        return osClient.getJar(
            definition.resourceLocation.ref.hash.base64Decode(),
            encryptionKeyRef,
//            signaturePublicKey // todo: determine if old get method signature public key thing was necessary
        ).get()
        .let { memoryClassLoader.addJar(definition.resourceLocation.ref.hash, it) }
    }

    fun addJar(
        hash: String,
        inputStream: InputStream
    ) = memoryClassLoader.addJar(hash, inputStream)

    fun loadClass(
        encryptionKeyRef: KeyRef,
        definition: DefinitionSpec,
        signaturePublicKey: PublicKey? = null
    ): Class<*> {
        return osClient.getJar(
            definition.resourceLocation.ref.hash.base64Decode(),
            encryptionKeyRef,
//            signaturePublicKey // todo: determine if old get method signature public key thing was necessary
        ).get()
        .let { memoryClassLoader.addJar(definition.resourceLocation.ref.hash, it) }
        .let {
            memoryClassLoader.loadClass(definition.resourceLocation.classname)
        }
    }

    fun loadClass(
        definition: DefinitionSpec
    ): Class<*> {
        return memoryClassLoader.loadClass(definition.resourceLocation.classname)
    }

    fun <T> forThread(fn: () -> T): T {
        return memoryClassLoader.forThread(fn)
    }
}
