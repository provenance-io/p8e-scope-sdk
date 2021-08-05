package io.provenance.scope.classloader

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.provenance.scope.definition.DefinitionService
import java.util.concurrent.ConcurrentHashMap

object ClassLoaderCache {
    val classLoaderCache: Cache<String, MemoryClassLoader> = CacheBuilder.newBuilder()
        .maximumSize(100) // todo: set via config, is there a way to 'weigh' the size of a classLoader?
        .build()
}
