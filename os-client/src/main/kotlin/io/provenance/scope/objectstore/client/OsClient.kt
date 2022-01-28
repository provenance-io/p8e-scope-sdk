package io.provenance.scope.objectstore.client

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.ByteString
import com.google.protobuf.Message
import io.grpc.ManagedChannel
import org.slf4j.LoggerFactory
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.provenance.scope.encryption.dime.ProvenanceDIME
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.crypto.CertificateUtil
import io.provenance.scope.encryption.domain.inputstream.DIMEInputStream
import io.provenance.objectstore.proto.MailboxServiceGrpc
import io.provenance.objectstore.proto.Mailboxes
import io.provenance.objectstore.proto.ObjectServiceGrpc
import io.provenance.objectstore.proto.Objects
import io.provenance.objectstore.proto.Objects.ChunkBidi
import io.provenance.objectstore.proto.PublicKeyServiceGrpc
import io.provenance.objectstore.proto.PublicKeys
import io.provenance.scope.objectstore.util.toPublicKeyProtoOS
import io.provenance.scope.encryption.proto.Encryption.ContextType.RETRIEVAL
import io.provenance.objectstore.proto.Utils
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.crypto.sign
import io.provenance.scope.objectstore.util.base64EncodeString
import io.provenance.scope.objectstore.util.loBytes
import io.provenance.scope.util.toHexString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.URI
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

const val CREATED_BY_HEADER = "x-created-by"
const val DIME_FIELD_NAME = "DIME"
const val HASH_FIELD_NAME = "HASH"
const val SIGNATURE_PUBLIC_KEY_FIELD_NAME = "SIGNATURE_PUBLIC_KEY"
const val SIGNATURE_FIELD_NAME = "SIGNATURE"

typealias ChannelCustomizeFn = (ManagedChannelBuilder<*>) -> ManagedChannelBuilder<*>

/**
 * A client for communication with an Object Store instance
 * @param [uri] the [URI] of the Object Store instance
 * @param [deadlineMs] the timeout value in milliseconds for requests to Object Store
 */
open class OsClient(
    uri: URI,
    private val deadlineMs: Long,
    customizeChannel: ChannelCustomizeFn = { it },
    private val extraHeaders: Map<String, String> = emptyMap()
) : Closeable {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val objectAsyncClient: ObjectServiceGrpc.ObjectServiceStub
    private val objectFutureClient: ObjectServiceGrpc.ObjectServiceFutureStub
    private val publicKeyBlockingClient: PublicKeyServiceGrpc.PublicKeyServiceBlockingStub
    private val mailboxBlockingClient: MailboxServiceGrpc.MailboxServiceBlockingStub
    private val channel: ManagedChannel

    init {
        channel = ManagedChannelBuilder.forAddress(uri.host, uri.port)
            .also {
                if (uri.scheme == "grpcs") {
                    it.useTransportSecurity()
                } else {
                    it.usePlaintext()
                }
            }
            .idleTimeout(60, TimeUnit.SECONDS)
            .keepAliveTime(10, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .let(customizeChannel)
            .build()

        val headers = Metadata()
            .also { metdata ->
                extraHeaders.forEach { name, value ->
                    metdata.put(
                        Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER),
                        value
                    )
                }
            }

        objectAsyncClient = MetadataUtils.attachHeaders(ObjectServiceGrpc.newStub(channel), headers)
        objectFutureClient = MetadataUtils.attachHeaders(ObjectServiceGrpc.newFutureStub(channel), headers)
        publicKeyBlockingClient = MetadataUtils.attachHeaders(PublicKeyServiceGrpc.newBlockingStub(channel), headers)
        mailboxBlockingClient = MetadataUtils.attachHeaders(MailboxServiceGrpc.newBlockingStub(channel), headers)
    }

    /**
     * Acknowledge the processing of a piece of mail from Object Store
     * @param [uuid] the [UUID] of the mail to ack
     */
    fun mailboxAck(uuid: UUID) {
        val request = Mailboxes.AckRequest.newBuilder()
            .setUuid(Utils.UUID.newBuilder().setValue(uuid.toString()).build())
            .build()

        mailboxBlockingClient.ack(request)
    }

    /**
     * Fetch mail designated for a specific [PublicKey]
     * @param [publicKey] the [PublicKey] to fetch mail for
     * @param [maxResults] the maximum number of mail items to return
     *
     * @return a list of mail items (may be empty if there is no mail)
     */
    fun mailboxGet(publicKey: PublicKey, maxResults: Int): Sequence<Pair<UUID, DIMEInputStream>> {
        val ecPublicKey = ECUtils.convertPublicKeyToBytes(publicKey)
        val response = mailboxBlockingClient.get(
            Mailboxes.GetRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(ecPublicKey))
                .setMaxResults(maxResults)
                .build()
        )

        return response.asSequence()
            .map {
                val dime = DIMEInputStream.parse(ByteArrayInputStream(it.data.toByteArray()))
                Pair(UUID.fromString(it.uuid.value), dime)
            }
    }

    private fun getInner(iterator: Iterator<ChunkBidi>): DIMEInputStream {
        val bytes = ByteArrayOutputStream()

        if (!iterator.hasNext()) {
            throw MalformedStreamException("MultiStream has no header")
        }

        val multiStreamHeader = iterator.next()
        if (!multiStreamHeader.hasMultiStreamHeader() || multiStreamHeader.multiStreamHeader.streamCount != 1) {
            throw MalformedStreamException("MultiStream must start with header and have only one stream")
        }

        if (!iterator.hasNext()) {
            throw MalformedStreamException("MultiStream has no streams")
        }

        while (iterator.hasNext()) {
            val packet = iterator.next()
            if (!packet.hasChunk()) {
                throw MalformedStreamException("Data stream must be all chunks")
            }

            val chunk = packet.chunk
            if (!chunk.hasHeader() && bytes.size() == 0) {
                throw MalformedStreamException("First stream chunk must contain header")
            }

            when (chunk.implCase) {
                Objects.Chunk.ImplCase.DATA -> bytes.write(chunk.data.toByteArray())
                Objects.Chunk.ImplCase.VALUE -> throw MalformedStreamException("VALUE chunk types are not valid on the receive end")
                Objects.Chunk.ImplCase.END -> {
                    if (iterator.hasNext()) {
                        throw MalformedStreamException("END chunk must be the last chunk of the stream")
                    } else {
                        // no op since we expect the END chunk to be the last chunk of the stream
                    }
                }
                Objects.Chunk.ImplCase.IMPL_NOT_SET, null -> throw IllegalStateException("No chunk impl set")
            } as Unit
        }

        return DIMEInputStream.parse(ByteArrayInputStream(bytes.toByteArray()))
    }

    /**
     * Fetch an object from Object Store by hash
     * @param [hash] the hash of the object
     * @param [publicKey] the [PublicKey] associated with this object, whose corresponding [PrivateKey][java.security.PrivateKey]
     * should be able to decrypt the object
     *
     * @return a [ListenableFuture] containing the [DIMEInputStream] (if found)
     *
     * @throws [IllegalArgumentException] if the length of the [hash] is < 16
     */
    fun get(hash: ByteArray, publicKey: PublicKey): ListenableFuture<DIMEInputStream> {
        if (hash.size < 16) {
            throw IllegalArgumentException("Provided hash must be byte array of at least size 16, found size: ${hash.size}")
        }

        val ecPublicKey = ECUtils.convertPublicKeyToBytes(publicKey)
        val responseObserver = BufferedResponseFutureObserver<ChunkBidi>()
        // TODO wrap this call in try and return previous error on 404
        val headers = AtomicReference<Metadata>()
        val trailers = AtomicReference<Metadata>()
        MetadataUtils.captureMetadata(objectAsyncClient, headers, trailers)
        println("headers: ${headers.get().toString()}; trailers: ${trailers.get().toString()}")
        objectAsyncClient.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).get(
            Objects.HashRequest.newBuilder()
                .setHash(ByteString.copyFrom(hash))
                .setPublicKey(ByteString.copyFrom(ecPublicKey))
                .build(),
            responseObserver,
        )

        return Futures.transform(
            responseObserver.future,
            { iterator -> iterator?.let { this.getInner(it) }}, // TODO figure out what happens when this is null?
            // TODO use the threadpool that exists
            MoreExecutors.directExecutor(),
        )
    }

    /**
     * Write an object to Object Store
     *
     * @param [message] the [Proto Message][Message] to write
     * @param [encryptionPublicKey] the encryption [PublicKey] to use for encrypting the [Proto Message][Message]
     * @param [signer] the [SignerImpl] to use for signing the request to Object Store
     * @param [additionalAudiences] (optional) any additional [PublicKey]s to allow to decrypt the [Proto Message][Message]
     * @param [metadata] (optional) any additional meatdata to set on the DIME
     * @param [uuid] (optional) a uuid to set for this [Proto Message][Message] in Object Store
     * @param [loHash] whether to use the first 16 bytes of this object's hash as the hash in Object Store
     *
     * @return a [ListenableFuture] containing the response from Object Store
     */
    fun put(
        message: Message,
        encryptionPublicKey: PublicKey,
        signer: SignerImpl,
        additionalAudiences: Set<PublicKey> = setOf(),
        metadata: Map<String, String> = mapOf(),
        uuid: UUID = UUID.randomUUID(),
        loHash: Boolean = false,
    ): ListenableFuture<Objects.ObjectResponse> {
        val bytes = message.toByteArray()

        return put(
            ByteArrayInputStream(bytes),
            encryptionPublicKey,
            signer,
            bytes.size.toLong(),
            additionalAudiences,
            metadata,
            uuid,
            loHash,
        )
    }

    /**
     * Write a stream of bytes to Object Store
     *
     * @param [inputStream] the stream of bytes to write
     * @param [encryptionPublicKey] the encryption [PublicKey] to use for encrypting the [Proto Message][Message]
     * @param [signer] the [SignerImpl] to use for signing the request to Object Store
     * @param [contentLength] the length of the [inputStream]
     * @param [additionalAudiences] (optional) any additional [PublicKey]s to allow to decrypt the [Proto Message][Message]
     * @param [metadata] (optional) any additional meatdata to set on the DIME
     * @param [uuid] (optional) a uuid to set for this [Proto Message][Message] in Object Store
     * @param [loHash] whether to use the first 16 bytes of this object's hash as the hash in Object Store
     *
     * @return a [ListenableFuture] containing the response from Object Store
     */
    fun put(
        inputStream: InputStream,
        encryptionPublicKey: PublicKey,
        signer: SignerImpl,
        contentLength: Long,
        additionalAudiences: Set<PublicKey> = setOf(),
        metadata: Map<String, String> = mapOf(),
        uuid: UUID = UUID.randomUUID(),
        loHash: Boolean = false,
    ): ListenableFuture<Objects.ObjectResponse> {
        val signerPublicKey = signer.getPublicKey()
        val signatureInputStream = inputStream.sign(signer)
        val signingPublicKey = CertificateUtil.publicKeyToPem(signerPublicKey)

        // TODO should this be performed in the thread pool in the background?
        val dime = ProvenanceDIME.createDIME(
            payload = signatureInputStream,
            ownerEncryptionPublicKey = encryptionPublicKey,
            additionalAudience = mapOf(Pair(RETRIEVAL, additionalAudiences)),
            processingAudienceKeys = listOf(),
            sha256 = true
        )
        val dimeInputStream = DIMEInputStream(
            dime.dime,
            dime.encryptedPayload,
            uuid = uuid,
            metadata = metadata + (SIGNATURE_PUBLIC_KEY_FIELD_NAME to CertificateUtil.publicKeyToPem(signerPublicKey)),
            internalHash = true,
            externalHash = false
        )
        val responseObserver = SingleResponseFutureObserver<Objects.ObjectResponse>()
        // TODO test that deadline works on async requests like this
        val requestObserver = objectAsyncClient.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).put(responseObserver)
        val header = Objects.MultiStreamHeader.newBuilder()
            .setStreamCount(1)
            .putMetadata(CREATED_BY_HEADER, UUID(0, 0).toString())
        .build()
        log.trace("Persisting Hash to Object Store:")
        dimeInputStream.use {
            try {
                requestObserver.onNext(ChunkBidi.newBuilder().setMultiStreamHeader(header).build())

                val iterator = InputStreamChunkedIterator(it, DIME_FIELD_NAME, contentLength)
                while (iterator.hasNext()) {
                    requestObserver.onNext(iterator.next())
                }

                val hash = if (loHash) {
                    dimeInputStream.internalHash().loBytes().toByteArray()

                } else {
                    dimeInputStream.internalHash()
                }
                log.trace("Hash: ${hash.base64EncodeString()}\nAudience Public Keys: ${additionalAudiences.map { it.toPublicKeyProtoOS().toByteArray().toHexString().toString() }}")
                requestObserver.onNext(propertyChunkRequest(HASH_FIELD_NAME to hash))
                requestObserver.onNext(propertyChunkRequest(SIGNATURE_FIELD_NAME to signatureInputStream.sign()))
                requestObserver.onNext(propertyChunkRequest(SIGNATURE_PUBLIC_KEY_FIELD_NAME to signingPublicKey.toByteArray(Charsets.UTF_8)))

                requestObserver.onCompleted()
            } catch (t: Throwable) {
                requestObserver.onError(t)
                throw t
            }
        }

        // TODO change this up if deadline functions correctly
        // if (!responseObserver.finishLatch.await(deadlineMs, TimeUnit.MILLISECONDS)) {
        //     throw TimeoutException("No response received")
        // }
        // if (responseObserver.error != null) {
        //     throw responseObserver.error!!
        // }

        return responseObserver.future
    }

    /**
     * Register a local or remote affiliate key with Object Store
     * @param [publicKey] the [PublicKey] of the affiliate to register
     * @param [objectStoreUrl] the url of the affiliate's Object Store instance (leave null for an affiliate sharing the same Object Store)
     *
     * @return the response from Object Store
     */
    fun createPublicKey(publicKey: PublicKey, objectStoreUrl: String? = null): PublicKeys.PublicKeyResponse? =
        publicKeyBlockingClient.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .add(
                PublicKeys.PublicKeyRequest.newBuilder()
                    .setPublicKey(publicKey.toPublicKeyProtoOS())
                    .apply {
                        if (objectStoreUrl != null) {
                            url = objectStoreUrl
                        }
                    }
                    .build()
            )

    override fun close() {
        channel.shutdown()
    }

    /**
     * Wait for the underlying communication channel to shut down after calling [close]
     * @param [timeout] the amount of time (in units of [unit]) to wait for shutdown before giving up
     * @param [unit] the units represented by [timeout]
     */
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        return channel.awaitTermination(timeout, unit)
    }
}

fun propertyChunkRequest(pair: Pair<String, ByteArray>): Objects.ChunkBidi =
    Objects.ChunkBidi.newBuilder()
        .setChunk(
            Objects.Chunk.newBuilder()
                .setHeader(
                    Objects.StreamHeader.newBuilder()
                        .setName(pair.first)
                        .build()
                )
                .setValue(ByteString.copyFrom(pair.second))
                .build()
        )
        .build()
