package io.provenance.scope.objectstore.client

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import io.provenance.scope.encryption.dime.ProvenanceDIME
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.crypto.CertificateUtil
import io.provenance.scope.encryption.domain.inputstream.DIMEInputStream
import io.provenance.objectstore.proto.MailboxServiceGrpc
import io.provenance.objectstore.proto.Mailboxes
import io.provenance.objectstore.proto.ObjectServiceGrpc
import io.provenance.objectstore.proto.Objects
import io.provenance.objectstore.proto.PublicKeyServiceGrpc
import io.provenance.objectstore.proto.PublicKeys
import io.provenance.scope.objectstore.util.toPublicKeyProtoOS
import io.provenance.scope.encryption.proto.Encryption.ContextType.RETRIEVAL
import io.provenance.objectstore.proto.Utils
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.crypto.sign
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val CREATED_BY_HEADER = "x-created-by"
const val DIME_FIELD_NAME = "DIME"
const val HASH_FIELD_NAME = "HASH"
const val SIGNATURE_PUBLIC_KEY_FIELD_NAME = "SIGNATURE_PUBLIC_KEY"
const val SIGNATURE_FIELD_NAME = "SIGNATURE"

open class OsClient(
    uri: URI,
    private val deadlineMs: Long
) {

    private val objectBlockingClient: ObjectServiceGrpc.ObjectServiceBlockingStub
    private val objectAsyncClient: ObjectServiceGrpc.ObjectServiceStub
    private val publicKeyBlockingClient: PublicKeyServiceGrpc.PublicKeyServiceBlockingStub
    private val mailboxBlockingClient: MailboxServiceGrpc.MailboxServiceBlockingStub

    init {
        val channel = ManagedChannelBuilder.forAddress(uri.host, uri.port)
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
            .build()

        objectAsyncClient = ObjectServiceGrpc.newStub(channel)
        objectBlockingClient = ObjectServiceGrpc.newBlockingStub(channel)
        publicKeyBlockingClient = PublicKeyServiceGrpc.newBlockingStub(channel)
        mailboxBlockingClient = MailboxServiceGrpc.newBlockingStub(channel)
    }

    fun ack(uuid: UUID) {
        val request = Mailboxes.AckRequest.newBuilder()
            .setUuid(Utils.UUID.newBuilder().setValue(uuid.toString()).build())
            .build()

        mailboxBlockingClient.ack(request)
    }

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

    fun get(sha512: ByteArray, publicKey: PublicKey, deadlineSeconds: Long = 60L): DIMEInputStream {
        if (sha512.size != 64) {
            throw IllegalArgumentException("Provided SHA-512 must be byte array of size 64, found size: ${sha512.size}")
        }

        val bytes = ByteArrayOutputStream()
        val ecPublicKey = ECUtils.convertPublicKeyToBytes(publicKey)

        val iterator = objectBlockingClient.get(
            Objects.HashRequest.newBuilder()
                .setHash(ByteString.copyFrom(sha512))
                .setPublicKey(ByteString.copyFrom(ecPublicKey))
                .build()
        )

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

    fun put(
        message: Message,
        encryptionPublicKey: PublicKey,
        signer: SignerImpl,
        additionalAudiences: Set<PublicKey> = setOf(),
        metadata: Map<String, String> = mapOf(),
        uuid: UUID = UUID.randomUUID()
    ): Objects.ObjectResponse {
        val bytes = message.toByteArray()

        return put(
            ByteArrayInputStream(bytes),
            encryptionPublicKey,
            signer,
            bytes.size.toLong(),
            additionalAudiences,
            metadata,
            uuid
        )
    }

    fun put(
        inputStream: InputStream,
        encryptionPublicKey: PublicKey,
        signer: SignerImpl,
        contentLength: Long,
        additionalAudiences: Set<PublicKey> = setOf(),
        metadata: Map<String, String> = mapOf(),
        uuid: UUID = UUID.randomUUID(),
        deadlineSeconds: Long = 60L
    ): Objects.ObjectResponse {
        val signerPublicKey = signer.getPublicKey()
        val signatureInputStream = inputStream.sign(signer)
        val signingPublicKey = CertificateUtil.publicKeyToPem(signerPublicKey)

        val dime = ProvenanceDIME.createDIME(
            payload = signatureInputStream,
            ownerEncryptionPublicKey = encryptionPublicKey,
            additionalAudience = mapOf(Pair(RETRIEVAL, additionalAudiences)),
            processingAudienceKeys = listOf()
        )
        val dimeInputStream = DIMEInputStream(
            dime.dime,
            dime.encryptedPayload,
            uuid = uuid,
            metadata = metadata + (SIGNATURE_PUBLIC_KEY_FIELD_NAME to CertificateUtil.publicKeyToPem(signerPublicKey)),
            internalHash = true,
            externalHash = false
        )
        val responseObserver = SingleResponseObserver<Objects.ObjectResponse>()
        val requestObserver = objectAsyncClient.put(responseObserver)
        val header = Objects.MultiStreamHeader.newBuilder()
            .setStreamCount(1)
            .putMetadata(CREATED_BY_HEADER, UUID(0, 0).toString())
        .build()

        dimeInputStream.use {
            try {
                requestObserver.onNext(Objects.ChunkBidi.newBuilder().setMultiStreamHeader(header).build())

                val iterator = InputStreamChunkedIterator(it, DIME_FIELD_NAME, contentLength)
                while (iterator.hasNext()) {
                    requestObserver.onNext(iterator.next())
                }

                requestObserver.onNext(propertyChunkRequest(HASH_FIELD_NAME to dimeInputStream.internalHash()))
                requestObserver.onNext(propertyChunkRequest(SIGNATURE_FIELD_NAME to signatureInputStream.sign()))
                requestObserver.onNext(propertyChunkRequest(SIGNATURE_PUBLIC_KEY_FIELD_NAME to signingPublicKey.toByteArray(Charsets.UTF_8)))

                requestObserver.onCompleted()
            } catch (t: Throwable) {
                requestObserver.onError(t)
                throw t
            }
        }

        if (!responseObserver.finishLatch.await(deadlineSeconds, TimeUnit.SECONDS)) {
            throw TimeoutException("No response received")
        }
        if (responseObserver.error != null) {
            throw responseObserver.error!!
        }

        return responseObserver.get()
    }

    fun createPublicKey(publicKey: PublicKey): PublicKeys.PublicKeyResponse? =
        publicKeyBlockingClient.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .add(
                PublicKeys.PublicKeyRequest.newBuilder()
                    .setPublicKey(publicKey.toPublicKeyProtoOS())
                    .setUrl("http://localhost") // todo: what is this supposed to be?
                    .build()
            )
}

class SingleResponseObserver<T> : StreamObserver<T> {
    val finishLatch: CountDownLatch = CountDownLatch(1)
    var error: Throwable? = null
    private var item: T? = null

    fun get(): T = item ?: throw IllegalStateException("Attempting to get result before it was received")

    override fun onNext(item: T) {
        this.item = item
    }

    override fun onError(t: Throwable) {
        error = t
        finishLatch.countDown()
    }

    override fun onCompleted() {
        finishLatch.countDown()
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
