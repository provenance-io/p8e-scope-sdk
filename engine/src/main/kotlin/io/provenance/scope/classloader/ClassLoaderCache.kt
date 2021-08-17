package io.provenance.scope.classloader

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.provenance.scope.definition.DefinitionService
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object ClassLoaderCache {
    private val log = LoggerFactory.getLogger(this::class.java)

    // todo: verify class loader cache behavior as classes unload
    val classLoaderCache: Cache<String, MemoryClassLoader> = CacheBuilder.newBuilder()
        .maximumSize(100) // todo: set via config, is there a way to 'weigh' the size of a classLoader?
        .removalListener<String, MemoryClassLoader> {
            log.info("Removing from class loader cache with key ${it.key}")
        }
        .build()
}
