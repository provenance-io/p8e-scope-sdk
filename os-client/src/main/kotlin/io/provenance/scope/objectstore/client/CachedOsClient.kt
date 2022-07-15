package io.provenance.scope.objectstore.client

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.google.protobuf.Message
import io.grpc.StatusRuntimeException
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.util.base64Encode
import io.provenance.scope.objectstore.util.base64EncodeString
import io.provenance.scope.objectstore.util.loBytes
import io.provenance.scope.objectstore.util.sha256
import io.provenance.scope.objectstore.util.sha256LoBytes
import io.provenance.scope.objectstore.util.toHex
import io.provenance.scope.util.NotFoundException
import io.provenance.scope.util.OSException
import io.provenance.scope.util.ProtoParseException
import io.provenance.scope.util.ThreadPoolFactory
import io.provenance.scope.util.base64String
import io.provenance.scope.util.forThread
import io.provenance.scope.util.sha256String
import io.provenance.scope.util.sha512
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.Semaphore

data class ObjectHash(val value: String)
data class RecordCacheKey(val publicKey: PublicKey, val hash: String)

typealias ByteCache = Cache<RecordCacheKey, ByteArray>

fun<T> withFutureSemaphore(semaphore: Semaphore, futureFn: () -> ListenableFuture<T>): ListenableFuture<T> {
    semaphore.acquire()

    return futureFn().also { future ->
        Futures.addCallback(
            future,
            object : FutureCallback<T> {
                override fun onSuccess(result: T?) {
                    semaphore.release()
                }

                override fun onFailure(t: Throwable) {
                    semaphore.release()
                }
            },
            MoreExecutors.directExecutor()
        )
    }
}

/**
 * A client for communication with an Object Store instance, with a caching layer
 * @property [osClient] the underlying non-cached osClient to use for communication with Object Store
 * @param [osDecryptionWorkerThreads] the number of threads to spin up for offloading decryption
 * @param [osConcurrencySize] the maximum allowed number of outstanding concurrent requests to Object Store
 * @param [cacheRecordSizeInBytes] the maximum size of the object cache in bytes
 */
class CachedOsClient(val osClient: OsClient, osDecryptionWorkerThreads: Short, osConcurrencySize: Short, cacheRecordSizeInBytes: Long, cacheJarSizeInBytes: Long) {

    private val tracer: Tracer = GlobalTracer.get()

    val decryptionWorkerThreadPool = ThreadPoolFactory.newFixedDaemonThreadPool(
        osDecryptionWorkerThreads.toInt(),
        "p8e-DW-%d",
    )

    val semaphore = Semaphore(osConcurrencySize.toInt(), true)
    val recordCache: ByteCache = CacheBuilder.newBuilder()
        .maximumWeight(cacheRecordSizeInBytes)
        .weigher { _: RecordCacheKey, bytes: ByteArray ->  bytes.size }
        .build()
    val jarCache: ByteCache = CacheBuilder.newBuilder()
        .maximumWeight(cacheJarSizeInBytes)
        .weigher { _: RecordCacheKey, bytes: ByteArray -> bytes.size }
        .build()

    /**
     * Write a jar from an [InputStream] to Object Store
     * @param [inputStream] the [InputStream] of the jar file
     * @param [signingKeyRef] the [KeyRef] to sign the put request to Object Store with
     * @param [encryptionKeyRef] the [KeyRef] to use for encrypting the jar
     * @param [contentLength] the length of the jar
     * @param [audience] (optional) the other [PublicKey]s whose corresponding [PrivateKey][java.security.PrivateKey]s should be permitted to decrypt the jar
     * @param [uuid] (optional) a uuid to set for this jar in Object Store
     *
     * @return a [ListenableFuture] with the result of the put request, containing an [ObjectHash]
     */
    fun putJar(
        inputStream: InputStream,
        signingKeyRef: KeyRef,
        encryptionKeyRef: KeyRef,
        contentLength: Long,
        audience: Set<PublicKey> = setOf(),
        uuid: UUID = UUID.randomUUID(),
        sha256: Boolean = true,
        loHash: Boolean = false,
    ): ListenableFuture<ObjectHash> {
        val future = withFutureSemaphore(semaphore) {
            osClient.put(inputStream, encryptionKeyRef.publicKey, signingKeyRef.signer(), contentLength, audience, uuid = uuid, sha256 = sha256, loHash = loHash)
        }

        return Futures.transform(
            future,
            { _object -> _object?.let { ObjectHash(it.hash.toByteArray().base64String()) }},
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * Fetch a jar from Object Store by hash
     * @param [hash] the hash of the jar
     * @param [encryptionKeyRef] the [KeyRef] to use to decrypt the jar
     *
     * @return a [ListenableFuture] containing the [InputStream] of the jar
     */
    fun getJar(
        hash: ByteArray,
        encryptionKeyRef: KeyRef,
    ): ListenableFuture<InputStream> {
        return Futures.transform(
            getRawBytes(hash, encryptionKeyRef, jarCache),
            { byteArray -> byteArray?.let { ByteArrayInputStream(it) } },
            MoreExecutors.directExecutor(),
        ).let {
            Futures.catching(it, StatusRuntimeException::class.java, { e ->
                ByteArrayInputStream(byteArrayOf())
            }, decryptionWorkerThreadPool)
        }
    }

    /**
     * Write a [ContractSpec] to Object Store
     * @param [contractSpec] the [ContractSpec] to write to Object Store
     * @param [signingKeyRef] the [KeyRef] to sign the put request to Object Store with
     * @param [encryptionKeyRef] the [KeyRef] to use for encrypting the [ContractSpec]
     * @param [audience] (optional) the other [PublicKey]s whose corresponding [PrivateKey][java.security.PrivateKey]s should be permitted to decrypt the [ContractSpec]
     * @param [uuid] (optional) a uuid to set for this [ContractSpec] in Object Store
     *
     * @return a [ListenableFuture] with the result of the put request, containing an [ObjectHash]
     */
    fun putRecord(
        contractSpec: ContractSpec,
        signingKeyRef: KeyRef,
        encryptionKeyRef: KeyRef,
        audience: Set<PublicKey>,
        uuid: UUID = UUID.randomUUID(),
    ): ListenableFuture<ObjectHash> {
        return putRecord(contractSpec, signingKeyRef, encryptionKeyRef, audience, uuid, sha256 = true, loHash = true)
    }

    /**
     * Write a [Proto Message][Message] to Object Store
     * @param [contractSpec] the [Proto Message][Message] to write to Object Store
     * @param [signingKeyRef] the [KeyRef] to sign the put request to Object Store with
     * @param [encryptionKeyRef] the [KeyRef] to use for encrypting the [Proto Message][Message]
     * @param [audience] (optional) the other [PublicKey]s whose corresponding [PrivateKey][java.security.PrivateKey]s should be permitted to decrypt the [Proto Message][Message]
     * @param [uuid] (optional) a uuid to set for this [Proto Message][Message] in Object Store
     *
     * @return a [ListenableFuture] with the result of the put request, containing an [ObjectHash]
     */
    fun putRecord(
        message: Message,
        signingKeyRef: KeyRef,
        encryptionKeyRef: KeyRef,
        audience: Set<PublicKey> = setOf(),
        uuid: UUID = UUID.randomUUID(),
        sha256: Boolean = true,
        loHash: Boolean = false,
    ): ListenableFuture<ObjectHash> {
        val span = tracer.buildSpan("PutRecord OS").start()

        val messageBytes = message.toByteArray()
        val hash = messageBytes.let {
            when (sha256) {
                true -> it.sha256()
                false -> it.sha512()
            }
        }.let {
            when (loHash) {
                true -> it.loBytes().toByteArray()
                false -> it
            }
        }
        val cacheKey = RecordCacheKey(encryptionKeyRef.publicKey, hash.base64String())

        val future = withFutureSemaphore(semaphore) {
            osClient.put(message, encryptionKeyRef.publicKey, signingKeyRef.signer(), audience, uuid = uuid, sha256 = sha256, loHash = loHash)
        }

        return Futures.transform(
            future,
            {
                span.setTag("Cached-Response", false)
                span.finish()
                if (it != null) {
                    recordCache.put(cacheKey, messageBytes)
                    ObjectHash(it.hash.toByteArray().base64EncodeString())
                } else {
                    throw OSException("Received null response when storing object to Object Store")
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * Fetch a [Proto Message][Message] from Object Store by hash
     * @param [classname] the type of [Proto Message][Message] stored in Object Store to cast the result to
     * @param [hash] the hash of the [Proto Message][Message]
     * @param [encryptionKeyRef] the [KeyRef] to use to decrypt the [Proto Message][Message]
     *
     * @return a [ListenableFuture] containing the [InputStream] of the jar
     *
     * @throws [ProtoParseException] if the fetched [Proto Message][Message] cannot be cast to the provided [classname]
     * @throws [IllegalArgumentException] if the length of the [hash] is < 16
     * @throws [NotFoundException] if the object was fetched, but the signature was not able to be verified
     */
    fun getRecord(
        classname: String,
        hash: ByteArray,
        encryptionKeyRef: KeyRef,
    ): ListenableFuture<Message> {
        val span = tracer.buildSpan("GetRecord OS").start()

        val classLoader = Thread.currentThread().contextClassLoader
        val future = getRawBytes(hash, encryptionKeyRef, recordCache)

        return Futures.transform(
            future,
            { byteArray ->
                span.finish()
                classLoader.forThread {
                    val clazz = Message::class.java
                    val instanceToReturn = parseFromLookup(classname).invoke(null, byteArray)

                    // TODO what happens when you throw in a transform?
                    if (!clazz.isAssignableFrom(instanceToReturn.javaClass)) {
                        throw ProtoParseException("Unable to assign instance ${instanceToReturn::class.java.name} to type ${clazz.name}")
                    }

                    clazz.cast(instanceToReturn)
                }
            },
            decryptionWorkerThreadPool,
        ).let {
            Futures.catching(it, StatusRuntimeException::class.java, { e ->
                classLoader.forThread {
                    val instanceToReturn = parseFromLookup(classname).invoke(null, byteArrayOf())
                    val clazz = Message::class.java

                    if (!clazz.isAssignableFrom(instanceToReturn.javaClass)) {
                        throw ProtoParseException("Unable to assign instance ${instanceToReturn::class.java.name} to type ${clazz.name}")
                    }

                    clazz.cast(instanceToReturn)
                }
            }, decryptionWorkerThreadPool)
        }
    }

    // TODO cache these as well
    private fun getRawBytes(
        hash: ByteArray,
        encryptionKeyRef: KeyRef,
        cache: ByteCache
    ): ListenableFuture<ByteArray> {
        val span = tracer.buildSpan("GetRawBytes OS").start()

        // TODO add good error like we had previously
        val cacheKey = RecordCacheKey(encryptionKeyRef.publicKey, hash.base64String())
        val record = cache.getIfPresent(cacheKey)

        return if (record != null) {
            Futures.immediateFuture(record).also {
                span.setTag("Cached-Response", true)
                span.finish()
            }
        } else {
            val dimeFuture = withFutureSemaphore(semaphore) { osClient.get(hash, encryptionKeyRef.publicKey) }

            Futures.transform(
                dimeFuture,
                { dime ->
                    span.setTag("Cached-Response", false)
                    span.finish()
                    dime?.use { dimeInputStream ->
                        // TODO per audience cache used to happen right here
                        // dimeInputStream.dime.audienceList
                        //     .map { ECUtils.convertBytesToPublicKey(it.publicKey.toByteArray().base64String()) }

                        dimeInputStream.getDecryptedPayload(encryptionKeyRef).use { signatureInputStream ->
                            signatureInputStream.readAllBytes().also {
                                if (!signatureInputStream.verify()) {
                                    throw NotFoundException(
                                        """
                                            Object was fetched but we're unable to verify item signature
                                            [encryption public key: ${encryptionKeyRef.publicKey.toHex()}]
                                            [hash: ${hash.base64EncodeString()}]
                                        """.trimIndent()
                                    )
                                }
                            }.also {
                                cache.put(cacheKey, it)
                            }
                        }
                    } ?: throw IllegalStateException("Future transform received null DIMEInputStream, this should not be possible")
                },
                decryptionWorkerThreadPool,
            )
        }
    }

    private fun parseFromLookup(classname: String): Method =
        Thread.currentThread().contextClassLoader.loadClass(classname).declaredMethods.find {
            it.returnType.name == classname && it.name == "parseFrom" && it.parameterCount == 1 && it.parameters.first().type == ByteArray::class.java
        } ?: throw ProtoParseException("Unable to find \"parseFrom\" method on $classname. $classname should subtype com.google.protobuf.Message")
}
