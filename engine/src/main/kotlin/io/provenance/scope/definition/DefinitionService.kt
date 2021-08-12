package io.provenance.scope.definition

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.protobuf.Message
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.provenance.scope.classloader.MemoryClassLoader
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.contract.proto.Commons.Location
import io.provenance.scope.contract.proto.Contracts.Record
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.proto.Encryption
import io.provenance.scope.encryption.util.orThrow
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.objectstore.util.NotFoundException
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.objectstore.util.toPublicKeyProtoOS
import io.provenance.scope.util.base64Sha512
import io.provenance.scope.util.or
import io.provenance.scope.util.sha256
import io.provenance.scope.util.toHexString
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringWriter
import java.lang.reflect.Method
import java.security.PublicKey
import kotlin.concurrent.thread

// TODO move somewhere else
fun PublicKey.toHex() = toPublicKeyProtoOS().toByteArray().toHexString()

class DefinitionService(
    private val osClient: OsClient,
    private val memoryClassLoader: MemoryClassLoader = MemoryClassLoader("", ByteArrayInputStream(ByteArray(0)))
) {

    companion object {
        private val byteCache: Cache<ByteCacheKey, ByteArray> = CacheBuilder.newBuilder()
            .maximumWeight(10000000) // todo: set via config, maybe need to move Config.kt to some shared project
            .weigher { _: ByteCacheKey, value: ByteArray -> value.size }
            .build()
        private val putCache: Cache<PutCacheKey, Boolean> = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build()

        data class ByteCacheKey(
            val publicKey: PublicKey,
            val hash: String
        )

        data class PutCacheKey(
            val keys: Set<PublicKey>,
            val hash: String
        )
    }

    private val parseFromCache: Cache<String, Method> = CacheBuilder.newBuilder()
        .maximumSize(1000) // todo: make configurable, is there a way to do this by weight/size? Not sure how to quanitfy the 'size' of a class
        .build()

    fun addJar(
        encryptionKeyRef: KeyRef,
        definition: DefinitionSpec,
        signer: SignerImpl,
        signaturePublicKey: PublicKey? = null
    ) {
        return get(
            encryptionKeyRef,
            definition.resourceLocation.ref.hash,
            definition.resourceLocation.classname,
            signer,
            signaturePublicKey
        ).let { memoryClassLoader.addJar(definition.resourceLocation.ref.hash, it) }
    }

    fun addJar(
        hash: String,
        inputStream: InputStream
    ) = memoryClassLoader.addJar(hash, inputStream)

    fun loadClass(
        encryptionKeyRef: KeyRef,
        definition: DefinitionSpec,
        signer: SignerImpl,
        signaturePublicKey: PublicKey? = null
    ): Class<*> {
        return get(
            encryptionKeyRef,
            definition.resourceLocation.ref.hash,
            definition.resourceLocation.classname,
            signer,
            signaturePublicKey
        ).let { memoryClassLoader.addJar(definition.resourceLocation.ref.hash, it) }
            .let {
                memoryClassLoader.loadClass(definition.resourceLocation.classname)
            }
    }

    fun loadClass(
        definition: DefinitionSpec
    ): Class<*> {
        return memoryClassLoader.loadClass(definition.resourceLocation.classname)
    }

    fun loadProto(
        encryptionKeyRef: KeyRef,
        location: Location,
        signer: SignerImpl,
        signaturePublicKey: PublicKey? = null
    ): Message {
        return loadProto(
            encryptionKeyRef,
            location.ref.hash,
            location.classname,
            signer,
            signaturePublicKey
        )
    }

    fun loadProto(
        encryptionKeyRef: KeyRef,
        definition: DefinitionSpec,
        signer: SignerImpl,
        signaturePublicKey: PublicKey? = null
    ): Message {
        return loadProto(
            encryptionKeyRef,
            definition.resourceLocation.ref.hash,
            definition.resourceLocation.classname,
            signer,
            signaturePublicKey
        )
    }

    fun loadProto(
        encryptionKeyRef: KeyRef,
        fact: Record,
        signer: SignerImpl,
        signaturePublicKey: PublicKey? = null
    ): Message {
        return loadProto(
            encryptionKeyRef,
            fact.dataLocation.ref.hash,
            fact.dataLocation.classname,
            signer,
            signaturePublicKey
        )
    }

    fun loadProto(
        encryptionKeyRef: KeyRef,
        hash: String,
        classname: String,
        signer: SignerImpl,
        signaturePublicKey: PublicKey? = null
    ): Message {
        return loadProto(
            get(
                encryptionKeyRef,
                hash,
                classname,
                signer,
                signaturePublicKey
            ),
            classname
        )
    }

    private fun loadProto(
        inputStream: InputStream,
        classname: String
    ): Message {
        val clazz = Message::class.java
        val instanceOfThing = inputStream.let { inputStream ->
            parseFromCache.get(classname) {
                Thread.currentThread().contextClassLoader.loadClass(classname)
                    .declaredMethods.find {
                        it.returnType.name == classname && it.name == "parseFrom" && it.parameterCount == 1 && it.parameters[0].type == InputStream::class.java
                    }.orThrow { IllegalStateException("Unable to find parseFrom method on $classname") }
            }.invoke(
                null,
                inputStream
            )
        }

        if (!clazz.isAssignableFrom(instanceOfThing.javaClass)) {
            throw IllegalStateException("Unable to assign instance ${instanceOfThing::class.java.name} to type ${clazz.name}")
        }
        return clazz.cast(instanceOfThing)
    }

    fun get(
        encryptionKeyRef: KeyRef,
        hash: String,
        classname: String,
        signer: SignerImpl,
        signaturePublicKey: PublicKey? = null
    ): InputStream {
        return byteCache.get(ByteCacheKey(encryptionKeyRef.publicKey, hash)) {
            val item = try {
                osClient.get(hash.base64Decode(), encryptionKeyRef.publicKey).get()
            } catch (e: StatusRuntimeException) {
                if (e.status.code == Status.Code.NOT_FOUND) {
                    throw NotFoundException(
                        """
                            Unable to find object
                            [classname: $classname]
                            [public key: ${encryptionKeyRef.publicKey.toPublicKeyProtoOS().toByteArray().toHexString()}]
                            [hash: $hash]
                        """.trimIndent()
                    )
                }
                throw e
            }

            val bytes = item.use { dimeInputStream ->
                dimeInputStream.dime.audienceList
                    .map { ECUtils.convertBytesToPublicKey(it.publicKey.toString(Charsets.UTF_8).base64Decode()) }.toSet()
                    .let { putCache.put(PutCacheKey(it, hash), true) }

                signaturePublicKey
                    ?.takeIf { publicKey ->
                        dimeInputStream.signatures
                            .map { it.publicKey.toString(Charsets.UTF_8) }
                            .contains(publicKeyToPem(publicKey))
                    }?.let { publicKey ->
                        dimeInputStream.getDecryptedPayload(encryptionKeyRef, publicKey)
                    }.or {
                        dimeInputStream.getDecryptedPayload(encryptionKeyRef)
                    }.use { signatureInputStream ->
                        signatureInputStream.readAllBytes()
                            .also {
                                if (!signatureInputStream.verify()) {
                                    throw NotFoundException(
                                        """
                                            Object was fetched but we're unable to verify item signature
                                            [classname: $classname]
                                            [encryption public key: ${encryptionKeyRef.publicKey.toHex()}]
                                            [signing public key: ${signaturePublicKey?.toHex()}]
                                            [hash: $hash]
                                        """.trimIndent()
                                    )
                                }
                            }
                    }
            }

            // Drop the setting of additional audiences in cache to a thread to avoid recursive update
            thread {
                updateCache(
                    encryptionKeyRef.publicKey,
                    item.dime,
                    hash,
                    bytes
                )
            }

            bytes
        }.let(::ByteArrayInputStream)
            .orThrow {
                NotFoundException(
                    """
                        Unable to find contract definition
                        [classname: $classname]
                        [public key: ${encryptionKeyRef.publicKey.toHex()}]
                        [hash: $hash]
                    """.trimIndent()
                )
            }
    }

    fun save(
        encryptionPublicKey: PublicKey,
        msg: ByteArray,
        signer: SignerImpl,
        audience: Set<PublicKey> = setOf()
    ): ByteArray {
        val putCacheKey = PutCacheKey(audience.toMutableSet().plus(encryptionPublicKey), msg.base64Sha512())
        if (putCache.getIfPresent(putCacheKey) == true) {
            return msg.sha256()
        }
        return osClient.put(
            ByteArrayInputStream(msg),
            encryptionPublicKey,
            signer,
            msg.size.toLong(),
            audience
        ).also {
            putCache.put(PutCacheKey(audience.toMutableSet().plus(encryptionPublicKey), msg.base64Sha512()), true)
        }.get().hash.toByteArray()
    }

    fun <T : Message> save(
        encryptionPublicKey: PublicKey,
        msg: T,
        signer: SignerImpl,
        audience: Set<PublicKey> = setOf()
    ): ByteArray {
        val putCacheKey = PutCacheKey(audience.toMutableSet().plus(encryptionPublicKey), msg.toByteArray().base64Sha512())
        if (putCache.getIfPresent(putCacheKey) == true) {
            return msg.toByteArray().sha256()
        }
        return osClient.put(
            msg,
            encryptionPublicKey,
            signer,
            additionalAudiences = audience
        ).also {
            putCache.put(putCacheKey, true)
        }.get().hash.toByteArray()
    }

    fun <T> forThread(fn: () -> T): T {
        return memoryClassLoader.forThread(fn)
    }

    private fun updateCache(
        publicKey: PublicKey,
        dime: Encryption.DIME,
        hash: String,
        bytes: ByteArray
    ) {
        dime.audienceList
            .distinctBy { it.publicKey.toStringUtf8() }
            .map { ECUtils.convertBytesToPublicKey(it.publicKey.toStringUtf8().base64Decode()) }
            .filter { it != publicKey }
            .forEach {
                byteCache.get(ByteCacheKey(it, hash)) {
                    bytes
                }
            }
    }

    private fun publicKeyToPem(publicKey: PublicKey): String {
        val pemStrWriter = StringWriter()
        val pemWriter = JcaPEMWriter(pemStrWriter)
        pemWriter.writeObject(publicKey)
        pemWriter.close()
        return pemStrWriter.toString()
    }
}
