package io.provenance.scope.sdk

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.google.protobuf.Message
import io.provenance.scope.encryption.crypto.Pen
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.model.SmartKeyRef
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.util.ThreadPoolFactory
import io.provenance.scope.util.base64String
import io.provenance.scope.util.sha256String
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.security.KeyPair
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

class CachedOsClient(config: ClientConfig, val osClient: OsClient) {

    val decryptionWorkerThreadPool = ThreadPoolFactory.newFixedDaemonThreadPool(
        config.osDecryptionWorkerThreads.toInt(),
        "p8e-DW-%d",
    )

    val semaphore = Semaphore(config.osConcurrencySize.toInt(), true)
    val recordCache: Cache<RecordCacheKey, ByteArray> = CacheBuilder.newBuilder()
        .maximumWeight(config.cacheRecordSizeInBytes)
        .weigher { _: RecordCacheKey, bytes: ByteArray ->  bytes.size }
        .build()

    // TODO remove content length as nullable if proven jar file size is accurate
    fun putJar(
        inputStream: InputStream,
        affiliate: Affiliate,
        contentLength: Long,
        audience: Set<PublicKey> = setOf(),
        uuid: UUID = UUID.randomUUID(),
    ): ListenableFuture<ObjectHash> {
        // TODO rework signerimpl and affiliate to signerimpl interface
        val signer = when (affiliate.signingKeyRef) {
            is DirectKeyRef -> Pen(KeyPair(affiliate.signingKeyRef.publicKey, affiliate.signingKeyRef.privateKey))
            is SmartKeyRef -> throw IllegalStateException("TODO SmartKeyRef is not yet supported!")
        }
        val future = withFutureSemaphore(semaphore) {
            osClient.put(inputStream, affiliate.encryptionKeyRef.publicKey, signer, contentLength, audience, uuid = uuid)
        }

        return Futures.transform(
            future,
            { _object -> _object?.let { ObjectHash(it.hash.toByteArray().base64String()) }},
            MoreExecutors.directExecutor(),
        )
    }

    fun getJar(
        hash: ByteArray,
        affiliate: Affiliate,
    ): ListenableFuture<InputStream> {
        return Futures.transform(
            getRawBytes(hash, affiliate),
            { byteArray -> byteArray?.let { ByteArrayInputStream(it) } },
            MoreExecutors.directExecutor(),
        )
    }

    // TODO add optional field that specifies hash length
    fun putRecord(
        message: Message,
        affiliate: Affiliate,
        audience: Set<PublicKey> = setOf(),
        uuid: UUID = UUID.randomUUID(),
    ): ListenableFuture<ObjectHash> {
        // TODO rework signerimpl and affiliate to signerimpl interface
        val signer = when (affiliate.signingKeyRef) {
            is DirectKeyRef -> Pen(KeyPair(affiliate.signingKeyRef.publicKey, affiliate.signingKeyRef.privateKey))
            is SmartKeyRef -> throw IllegalStateException("TODO SmartKeyRef is not yet supported!")
        }
        val cacheKey = RecordCacheKey(affiliate.encryptionKeyRef.publicKey, message.toByteArray().sha256String())

        return if (recordCache.asMap().containsKey(cacheKey)) {
            SettableFuture.create<ObjectHash>().also { it.set(ObjectHash(cacheKey.hash)) }
        } else {
            val future = withFutureSemaphore(semaphore) {
                osClient.put(message, affiliate.encryptionKeyRef.publicKey, signer, audience, uuid = uuid)
            }

            Futures.transform(
                future,
                {
                    if (it != null) {
                        recordCache.put(cacheKey, message.toByteArray())
                        ObjectHash(it.hash.toByteArray().sha256String())
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
        affiliate: Affiliate,
    ): ListenableFuture<Message> {
        val future = getRawBytes(hash, affiliate)

        return Futures.transform(
            future,
            { byteArray ->
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

    private fun getRawBytes(
        hash: ByteArray,
        affiliate: Affiliate,
    ): ListenableFuture<ByteArray> {
        // TODO add good error like we had previously
        val cacheKey = RecordCacheKey(affiliate.encryptionKeyRef.publicKey, hash.base64String())
        val record = recordCache.asMap()[cacheKey]

        return if (record != null) {
            SettableFuture.create<ByteArray>().also { it.set(record) }
        } else {
            val dimeFuture = withFutureSemaphore(semaphore) { osClient.get(hash, affiliate.encryptionKeyRef.publicKey) }

            Futures.transform(
                dimeFuture,
                { dime ->
                    dime?.use { dimeInputStream ->
                        // TODO per audience cache used to happen right here
                        // dimeInputStream.dime.audienceList
                        //     .map { ECUtils.convertBytesToPublicKey(it.publicKey.toByteArray().base64String()) }

                        dimeInputStream.getDecryptedPayload(affiliate.encryptionKeyRef).use { signatureInputStream ->
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
