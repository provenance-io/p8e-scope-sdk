package io.provenance.p8e.testframework.io.provenance.scope.sdk.mailbox

import com.google.protobuf.Message
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.provenance.objectstore.proto.PublicKeys
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.proto.PK
import io.provenance.scope.encryption.crypto.SignatureInputStream
import io.provenance.scope.encryption.crypto.verify
import io.provenance.scope.encryption.domain.inputstream.DIMEInputStream
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.model.SigningAndEncryptionPublicKeys
import io.provenance.scope.encryption.proto.Encryption
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.sdk.mailbox.ExecutionErrorEvent
import io.provenance.scope.sdk.mailbox.ExecutionRequestEvent
import io.provenance.scope.sdk.mailbox.ExecutionResponseEvent
import io.provenance.scope.sdk.mailbox.MailHandlerFn
import io.provenance.scope.sdk.mailbox.MailboxEvent
import io.provenance.scope.sdk.mailbox.MailboxMeta
import io.provenance.scope.sdk.mailbox.MailboxService
import io.provenance.scope.sdk.mailbox.PollAffiliateMailbox
import io.provenance.scope.sdk.toPublicKeyProto
import io.provenance.scope.util.error
import io.provenance.scope.util.toProtoUuid
import java.io.InputStream
import java.util.UUID

class PollAffiliateMailboxTest: WordSpec() {
    lateinit var osClient: OsClient
    lateinit var mailboxService: MailboxService
    val signingKeyRef = ProvenanceKeyGenerator.generateKeyPair().let { DirectKeyRef(it) }
    val encryptionKeyRef = ProvenanceKeyGenerator.generateKeyPair().let { DirectKeyRef(it) }

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        osClient = mockk<OsClient>()
        mailboxService = mockk<MailboxService>()
    }

    fun buildEnvelope(recitals: List<SigningAndEncryptionPublicKeys>) = Envelopes.Envelope.newBuilder()
        .apply {
            contractBuilder.addAllRecitals(recitals.map {
                Contracts.Recital.newBuilder()
                    .setSigner(
                        PK.SigningAndEncryptionPublicKeys.newBuilder()
                        .setSigningPublicKey(it.signingPublicKey.toPublicKeyProto())
                        .setEncryptionPublicKey(it.encryptionPublicKey.toPublicKeyProto())
                    ).build()
            })

            refBuilder
                .setSessionUuid(UUID.randomUUID().toProtoUuid())
        }.setExecutionUuid(UUID.randomUUID().toProtoUuid())
        .build()

    fun buildInputStreamOf(message: Message) = mockk<SignatureInputStream>().apply {
        every { readAllBytes() } returns message.toByteArray()
        every { verify() } returns true
        every { close() } returns Unit
    }

    fun buildDimeInputStreamOf(inputStream: SignatureInputStream, metadata: Map<String, String>) = mockk<DIMEInputStream>().apply {
        val dime = Encryption.DIME.getDefaultInstance()
        every { getDecryptedPayload(any()) } returns inputStream
        every { this@apply.metadata } returns metadata
        every { this@apply.dime } returns dime
    }

    init {
        "PollAffiliateMailbox" should {
            "handle incoming execution request" {
                val request = buildEnvelope(listOf(SigningAndEncryptionPublicKeys(signingKeyRef.publicKey, encryptionKeyRef.publicKey)))
                val inputStream = buildInputStreamOf(request)
                val dimeInputStream = buildDimeInputStreamOf(inputStream, MailboxMeta.MAILBOX_REQUEST)
                every { osClient.mailboxGet(any(), any()) } returns listOf(UUID.randomUUID() to dimeInputStream).asSequence()
                every { osClient.mailboxAck(any()) } returns Unit

                var handlerCount = 0
                PollAffiliateMailbox(osClient, mailboxService, signingKeyRef, encryptionKeyRef, 100, false) { event ->
                    handlerCount++
                    event::class shouldBe ExecutionRequestEvent::class
                    (event as ExecutionRequestEvent).envelope shouldBe request
                    true
                }.run()

                handlerCount shouldBe 1
            }
            "handle incoming execution response" {
                val request = buildEnvelope(listOf(SigningAndEncryptionPublicKeys(signingKeyRef.publicKey, encryptionKeyRef.publicKey)))
                val inputStream = buildInputStreamOf(request)
                val dimeInputStream = buildDimeInputStreamOf(inputStream, MailboxMeta.MAILBOX_RESPONSE)
                every { osClient.mailboxGet(any(), any()) } returns listOf(UUID.randomUUID() to dimeInputStream).asSequence()
                every { osClient.mailboxAck(any()) } returns Unit

                var handlerCount = 0
                PollAffiliateMailbox(osClient, mailboxService, signingKeyRef, encryptionKeyRef, 100, false) { event ->
                    handlerCount++
                    event::class shouldBe ExecutionResponseEvent::class
                    (event as ExecutionResponseEvent).envelope shouldBe request
                    true
                }.run()

                handlerCount shouldBe 1
            }
            "handle incoming execution error" {
                val envelope = buildEnvelope(listOf(SigningAndEncryptionPublicKeys(signingKeyRef.publicKey, encryptionKeyRef.publicKey)))
                val error = envelope.error("failure", Envelopes.EnvelopeError.Type.CONTRACT_INVOCATION)
                val inputStream = buildInputStreamOf(error)
                val dimeInputStream = buildDimeInputStreamOf(inputStream, MailboxMeta.MAILBOX_ERROR)
                every { osClient.mailboxGet(any(), any()) } returns listOf(UUID.randomUUID() to dimeInputStream).asSequence()
                every { osClient.mailboxAck(any()) } returns Unit

                var handlerCount = 0
                PollAffiliateMailbox(osClient, mailboxService, signingKeyRef, encryptionKeyRef, 100, false) { event ->
                    handlerCount++
                    event::class shouldBe ExecutionErrorEvent::class
                    (event as ExecutionErrorEvent).error shouldBe error
                    true
                }.run()

                handlerCount shouldBe 1
            }
            "prevent exceptions thrown in handler from preventing other mail from being processed" {
                val requests = listOf(
                    buildEnvelope(listOf(SigningAndEncryptionPublicKeys(signingKeyRef.publicKey, encryptionKeyRef.publicKey))),
                    buildEnvelope(listOf(SigningAndEncryptionPublicKeys(signingKeyRef.publicKey, encryptionKeyRef.publicKey))),
                )
                val inputStreams = requests.map { buildInputStreamOf(it) }
                val dimeInputStreams = inputStreams.map { buildDimeInputStreamOf(it, MailboxMeta.MAILBOX_REQUEST) }
                every { osClient.mailboxGet(any(), any()) } returns dimeInputStreams.map { UUID.randomUUID() to it }.asSequence()
                every { osClient.mailboxAck(any()) } returns Unit

                var handlerCount = 0
                PollAffiliateMailbox(osClient, mailboxService, signingKeyRef, encryptionKeyRef, 100, false) { event ->
                    handlerCount++
                    event::class shouldBe ExecutionRequestEvent::class
                    (event as ExecutionRequestEvent).envelope shouldBe requests[handlerCount - 1]
                    throw Exception("failure")
                }.run()

                handlerCount shouldBe 2
            }
        }
    }
}
