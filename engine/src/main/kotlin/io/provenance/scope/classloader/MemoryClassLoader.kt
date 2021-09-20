package io.provenance.scope.classloader

import io.provenance.scope.contract.contracts.ContractHash
import io.provenance.scope.contract.proto.ProtoHash
import org.apache.commons.io.FileUtils
import java.io.*
import java.net.URI
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarEntry
import java.util.jar.JarFile

class MemoryClassLoader(
    hash: String,
    inputStream: InputStream,
    private val readEmbeddedJars: Boolean = true
): URLClassLoader(
    arrayOf()
) {
    companion object {
        private val systemLoadedContracts = ServiceLoader.load(ContractHash::class.java).toList()
            .flatMap {
                it.getClasses().keys.map { it.split('$').first() }
            }.toHashSet()
        private val systemLoadedProtos = ServiceLoader.load(ProtoHash::class.java).toList()
            .flatMap {
                it.getClasses().keys.map { it.split('$').first() }
            }.toHashSet()
    }

    private val parentClassLoader = MemoryClassLoader::class.java.classLoader
    private val system = ClassLoader.getSystemClassLoader()
    private val classCache = ConcurrentHashMap<String, Class<*>>()
    private val jarLoadedCache = ConcurrentHashMap<String, Boolean>()
        .also {
            it[hash] = true
        }

    init {
        addJarEntries(
            inputStream,
            readEmbeddedJars
        )
    }

    override fun getName(): String {
        return "MemoryClassLoader"
    }

    @Synchronized
    override fun loadClass(name: String): Class<*> {
        val alreadyLoaded = findLoadedClass(name)
        if (alreadyLoaded != null) {
            return alreadyLoaded
        }

        if (classCache.containsKey(name)) {
            return classCache[name]!!
        }

        val rootClassName = name.split('$').first()
        val parentFirst = name.startsWith("com.google.protobuf") || (!systemLoadedProtos.contains(rootClassName) && !systemLoadedContracts.contains(rootClassName))

        var loadedFromParent: Boolean? = false
        val clazz = try {
            when {
                parentFirst  -> parentClassLoader.loadClass(name).also { loadedFromParent = true }
                else -> findClass(name).also { loadedFromParent = false }
            }
        } catch (t: Throwable) {
            when (t) {
                is ClassNotFoundException,
                is NoClassDefFoundError -> {
                    try {
                        when {
                            parentFirst -> findClass(name).also { loadedFromParent = false }
                            else -> parentClassLoader.loadClass(name).also { loadedFromParent = true }
                        }
                    } catch (t: Throwable) {
                        when (t) {
                            is ClassNotFoundException,
                            is NoClassDefFoundError -> system.loadClass(name).also { loadedFromParent = null }
                            else -> throw t
                        }
                    }
                }
                else -> throw t
            }
        }
        classCache[name] = clazz
        return clazz
    }

    fun addJar(
        hash: String,
        inputStream: InputStream
    ) {
        if (jarLoadedCache[hash] == true) {
            return
        }

        addJarEntries(
            inputStream,
            readEmbeddedJars
        )

        jarLoadedCache[hash] = true
    }

    private fun addJarEntries(
        inputStream: InputStream,
        readEmbeddedJars: Boolean
    ) {
        if (inputStream.available() == 0)
            return

        val rootJar = File.createTempFile("class-file", ".tmp").apply {
            FileUtils.writeByteArrayToFile(this, inputStream.readAllBytes())
        }.also {
            super.addURL(it.toURI().toURL())
        }.let {
            JarFile(it)
        }

        if (readEmbeddedJars) {
            rootJar
                .stream()
                .filter {
                    it.name.endsWith(".jar")
                }.map {
                    jarEntryAsUri(rootJar, it)
                }.forEach {
                    super.addURL(it!!.toURL())
                }
        }
    }

    private fun jarEntryAsUri(jarFile: JarFile?, jarEntry: JarEntry?): URI? {
        if (jarFile == null || jarEntry == null) throw IOException("Invalid jar file or entry")
        var input: InputStream? = null
        var output: OutputStream? = null
        return try {
            val name: String = jarEntry.getName().replace('/', '_')
            val i = name.lastIndexOf(".")
            val extension = if (i > -1) name.substring(i) else ""
            val file = File.createTempFile(
                name.substring(0, name.length - extension.length) +
                        ".", extension)
            file.deleteOnExit()
            input = jarFile.getInputStream(jarEntry)
            output = FileOutputStream(file)
            var readCount: Int
            val buffer = ByteArray(4096)
            while (input.read(buffer).also { readCount = it } != -1) {
                output.write(buffer, 0, readCount)
            }
            file.toURI()
        } finally {
            input?.close()
            output?.close()
        }
    }
}

