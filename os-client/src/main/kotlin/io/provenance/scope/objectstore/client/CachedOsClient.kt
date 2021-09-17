package io.provenance.scope.objectstore.client

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.google.protobuf.Message
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.model.signer
import io.provenance.scope.objectstore.util.base64EncodeString
import io.provenance.scope.objectstore.util.sha256LoBytes
import io.provenance.scope.util.ThreadPoolFactory
import io.provenance.scope.util.base64String
import io.provenance.scope.util.sha256String
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.Semaphore

data class ObjectHash(val value: String)
data class RecordCacheKey(val publicKey: PublicKey, val hash: String)

fun<T> withFutureSemaphore(semaphore: Semaphore, futureFn: () -> ListenableFuture<T>): ListenableFuture<T> {
    semaphore.acquire()

    return Futures.transform(
        futureFn(),
        { semaphore.release(); it },
        MoreExecutors.directExecutor(),
    )
}

class CachedOsClient(val osClient: OsClient, osDecryptionWorkerThreads: Short, osConcurrencySize: Short, cacheRecordSizeInBytes: Long) {

    private val tracer: Tracer = GlobalTracer.get()

    val decryptionWorkerThreadPool = ThreadPoolFactory.newFixedDaemonThreadPool(
        osDecryptionWorkerThreads.toInt(),
        "p8e-DW-%d",
    )

    val semaphore = Semaphore(osConcurrencySize.toInt(), true)
    val recordCache: Cache<RecordCacheKey, ByteArray> = CacheBuilder.newBuilder()
        .maximumWeight(cacheRecordSizeInBytes)
        .weigher { _: RecordCacheKey, bytes: ByteArray ->  bytes.size }
        .build()

    fun putJar(
        inputStream: InputStream,
        signingKeyRef: KeyRef,
        encryptionKeyRef: KeyRef,
        contentLength: Long,
        audience: Set<PublicKey> = setOf(),
        uuid: UUID = UUID.randomUUID(),
    ): ListenableFuture<ObjectHash> {
        val future = withFutureSemaphore(semaphore) {
            osClient.put(inputStream, encryptionKeyRef.publicKey, signingKeyRef.signer(), contentLength, audience, uuid = uuid)
        }

        return Futures.transform(
            future,
            { _object -> _object?.let { ObjectHash(it.hash.toByteArray().base64String()) }},
            MoreExecutors.directExecutor(),
        )
    }

    fun getJar(
        hash: ByteArray,
        encryptionKeyRef: KeyRef,
    ): ListenableFuture<InputStream> {
        return Futures.transform(
            getRawBytes(hash, encryptionKeyRef),
            { byteArray -> byteArray?.let { ByteArrayInputStream(it) } },
            MoreExecutors.directExecutor(),
        )
    }

    fun putRecord(
        contractSpec: ContractSpec,
        signingKeyRef: KeyRef,
        encryptionKeyRef: KeyRef,
        audience: Set<PublicKey>,
        uuid: UUID = UUID.randomUUID(),
    ): ListenableFuture<ObjectHash> {
        return putRecord(contractSpec, signingKeyRef, encryptionKeyRef, audience, uuid, loHash = true)
    }

    fun putRecord(
        message: Message,
        signingKeyRef: KeyRef,
        encryptionKeyRef: KeyRef,
        audience: Set<PublicKey> = setOf(),
        uuid: UUID = UUID.randomUUID(),
        loHash: Boolean = false,
    ): ListenableFuture<ObjectHash> {
        val span = tracer.buildSpan("PutRecord OS").start()

        val cacheKey = if (loHash) {
            RecordCacheKey(encryptionKeyRef.publicKey, message.toByteArray().sha256LoBytes().base64String())
        } else {
            RecordCacheKey(encryptionKeyRef.publicKey, message.toByteArray().sha256String())
        }

        return if (recordCache.asMap().containsKey(cacheKey)) {
            SettableFuture.create<ObjectHash>().also { it.set(ObjectHash(cacheKey.hash)) }.also {
                span.setTag("Cached-Response", true)
                span.finish() }
        } else {
            val future = withFutureSemaphore(semaphore) {
                osClient.put(message, encryptionKeyRef.publicKey, signingKeyRef.signer(), audience, uuid = uuid, loHash = loHash)
            }

            Futures.transform(
                future,
                {
                    span.setTag("Cached-Response", false)
                    span.finish()
                    if (it != null) {
                        recordCache.put(cacheKey, message.toByteArray())
                        ObjectHash(it.hash.toByteArray().base64EncodeString())
                    } else {
                        // TODO fix
                        throw Exception("placeholder")
                    }
                },
                MoreExecutors.directExecutor(),
            )
        }
    }

    fun getRecord(
        classname: String,
        hash: ByteArray,
        encryptionKeyRef: KeyRef,
    ): ListenableFuture<Message> {
        val span = tracer.buildSpan("GetRecord OS").start()

        val future = getRawBytes(hash, encryptionKeyRef)

        return Futures.transform(
            future,
            { byteArray ->
                span.finish()
                val clazz = Message::class.java
                val instanceToReturn = parseFromLookup(classname).invoke(null, byteArray)

                // TODO what happens when you throw in a transform?
                if (!clazz.isAssignableFrom(instanceToReturn.javaClass)) {
                    throw IllegalStateException("Unable to assign instance ${instanceToReturn::class.java.name} to type ${clazz.name}")
                }

                clazz.cast(instanceToReturn)
            },
            decryptionWorkerThreadPool,
        )
    }

    // TODO cache these as well
    private fun getRawBytes(
        hash: ByteArray,
        encryptionKeyRef: KeyRef,
    ): ListenableFuture<ByteArray> {
        val span = tracer.buildSpan("GetRawBytes OS").start()

        // TODO add good error like we had previously
        val cacheKey = RecordCacheKey(encryptionKeyRef.publicKey, hash.base64String())
        val record = recordCache.asMap()[cacheKey]

        return if (record != null) {
            SettableFuture.create<ByteArray>().also { it.set(record) }.also {
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
                                    // TODO throw exception can't verify signature
                                }
                            }
                        }
                    } ?: throw Exception("placeholder") // TODO fix
                },
                decryptionWorkerThreadPool,
            )
        }
    }

    private fun parseFromLookup(classname: String): Method =
        // TODO change to different exception?
        javaClass.classLoader.loadClass(classname).declaredMethods.find {
            it.returnType.name == classname && it.name == "parseFrom" && it.parameterCount == 1 && it.parameters.first().type == ByteArray::class.java
        } ?: throw IllegalStateException("Unable to find \"parseFrom\" method on $classname. $classname should subtype com.google.protobuf.Message")
}
