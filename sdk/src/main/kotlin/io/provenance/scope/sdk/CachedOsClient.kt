package io.provenance.scope.sdk

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.protobuf.Message
import io.provenance.objectstore.proto.Objects
import io.provenance.scope.encryption.crypto.Pen
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.model.SmartKeyRef
import io.provenance.scope.objectstore.client.OsClient
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.security.KeyPair
import java.security.PublicKey
import java.util.UUID

// TODO add semaphore so that only N objects are queried at a time - add this amount to the config

data class RecordCacheValue(val publicKey: PublicKey, val bytes: ByteArray)

class CachedOsClient(config: ClientConfig, val osClient: OsClient) {

    val recordCache: Cache<String, RecordCacheValue> = CacheBuilder.newBuilder()
        .maximumWeight(config.recordCacheSizeInBytes)
        .weigher { _: String, value: RecordCacheValue ->  value.bytes.size }
        .build()

    // TODO remove content length as nullable if proven jar file size is accurate
    fun putJar(
        inputStream: InputStream,
        affiliate: Affiliate,
        contentLength: Long? = null,
        audience: Set<PublicKey> = setOf(),
        uuid: UUID = UUID.randomUUID(),
    ): Objects.ObjectResponse {
        // TODO rework signerimpl and affiliate to signerimpl interface
        val signer = when (affiliate.signingKeyRef) {
            is DirectKeyRef -> Pen(KeyPair(affiliate.signingKeyRef.publicKey, affiliate.signingKeyRef.privateKey))
            is SmartKeyRef -> throw IllegalStateException("TODO SmartKeyRef is not yet supported!")
        }
        val (stream: InputStream, length: Long) = contentLength?.let { Pair(inputStream, it) }
            ?: with (inputStream.use { it.readAllBytes() }) { Pair(ByteArrayInputStream(this), this.size.toLong()) }

        return osClient.put(stream, affiliate.encryptionKeyRef.publicKey, signer, length, audience, uuid = uuid)
    }

    fun putRecord() {

    }

    // TODO for now just forward all requests but this needs to get cached
    fun getRecord(classname: String, hash: ByteArray, keyRef: KeyRef): Message {
        // TODO add good error like we had previously
        val byteArray = osClient.get(hash, keyRef.publicKey).use { dimeInputStream ->
            // TODO per audience cache used to happen right here
            // dimeInputStream.dime.audienceList
            //     .map { ECUtils.convertBytesToPublicKey(it.publicKey.toStringUtf8().base64Decode()) }

            dimeInputStream.getDecryptedPayload(keyRef).use { signatureInputStream ->
                signatureInputStream.readAllBytes().also {
                    if (!signatureInputStream.verify()) {
                        // TODO throw exception can't verify signature
                    }
                }
            }
        }
        val clazz = Message::class.java
        val instanceToReturn = parseFromLookup(classname).invoke(null, byteArray)

        if (!clazz.isAssignableFrom(instanceToReturn.javaClass)) {
            throw IllegalStateException("Unable to assign instance ${instanceToReturn::class.java.name} to type ${clazz.name}")
        }

        return clazz.cast(instanceToReturn)
    }

    // TODO should be single instance globally once we add caching
    private fun parseFromLookup(classname: String): Method =
        // TODO change to different exception?
        javaClass.classLoader.loadClass(classname).declaredMethods.find {
            it.returnType.name == classname && it.name == "parseFrom" && it.parameterCount == 1 && it.parameters.first().type == ByteArray::class.java
        } ?: throw IllegalStateException("Unable to find \"parseFrom\" method on $classname. $classname should subtype com.google.protobuf.Message")
}
