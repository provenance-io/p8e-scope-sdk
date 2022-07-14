package io.provenance.scope.sdk.mailbox

import io.provenance.metadata.v1.Scope
import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.executionUuid
import io.provenance.scope.contract.proto.sessionUuid
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.proto.Encryption.Audience
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.orThrow
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.objectstore.util.toHex
import io.provenance.scope.objectstore.util.toPublicKey
import io.provenance.scope.sdk.proxy.Contract
import io.provenance.scope.util.error
import io.provenance.scope.util.randomProtoUuid
import io.provenance.scope.util.scopeOrNull
import io.provenance.scope.util.toMessageWithStackTrace
import io.provenance.scope.util.toUuidOrNull
import org.slf4j.LoggerFactory
import java.nio.file.Files.readAllBytes
import java.util.UUID
import java.util.function.Function

typealias MailHandlerFn = Function<MailboxEvent, Boolean>

class PollAffiliateMailbox(val osClient: CachedOsClient, val mailboxService: MailboxService, val signingKeyRef: KeyRef, val encryptionKeyRef: KeyRef, val maxResults: Int, val mainNet: Boolean, val handler: MailHandlerFn): Runnable {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun run() {
        osClient.osClient.mailboxGet(encryptionKeyRef.publicKey, maxResults).forEach { (mailUuid, dimeInputStream) ->
            try {
                dimeInputStream.getDecryptedPayload(encryptionKeyRef).use {
                    val bytes = it.readAllBytes()

                    if (!it.verify()) {
                        log.warn("Mailbox object verification failure [public key: ${encryptionKeyRef.publicKey.toHex()}] [mailbox uuid: $mailUuid]")
                        return
                    }

                    log.trace("Received mail from poll $mailUuid")

                    if (!dimeInputStream.metadata.containsKey(MailboxMeta.KEY)) {
                        osClient.osClient.mailboxAck(mailUuid).also { log.warn("Unhandled mailbox meta: {}", dimeInputStream.metadata) }
                        return
                    }

                    val mailboxKey = dimeInputStream.metadata[MailboxMeta.KEY]

                    when (mailboxKey) {
                        MailboxMeta.FRAGMENT_REQUEST, MailboxMeta.FRAGMENT_RESPONSE -> handleEnvelope(mailUuid, mailboxKey, dimeInputStream.dime.owner, bytes)
                        MailboxMeta.ERROR_RESPONSE -> handleEnvelopeError(mailUuid, mailboxKey, dimeInputStream.dime.owner, bytes)
                        else -> throw IllegalStateException("Unhandled mailbox key: $mailboxKey uuid: $mailUuid")
                    }
                }
            } catch (t: Throwable) {
                log.error("Error processing incoming mail", t)
            }
        }
    }

    private fun handleEnvelope(mailUuid: UUID, mailboxKey: String, ownerAudience: Audience, message: ByteArray) {
        val envelope = Envelope.parseFrom(message)

        val scope = envelope.scopeOrNull()

        require(envelope.ref.sessionUuid.toUuidOrNull() != null) { "Session uuid is required" }
        require(envelope.executionUuid.toUuidOrNull() != null) { "Execution uuid is required" }

        val className = envelope.contract.definition.resourceLocation.classname

        val signingAddress = signingKeyRef.publicKey.getAddress(mainNet)

        // verify affiliate is present on contract/scope
        envelope.contract.recitalsList.map { it.signer.signingPublicKey.toPublicKey().getAddress(mainNet) }.plus(scope?.scope?.scope?.ownersList?.map { it.address } ?: listOf())
            .firstOrNull { it == signingAddress }
            .orThrow { IllegalStateException("Can't find party on contract execution ${envelope.executionUuid.value} with key ${signingKeyRef.publicKey.toHex()}") }

        when (mailboxKey) {
            MailboxMeta.FRAGMENT_REQUEST -> handler.handleSynchronousAck(mailUuid, ExecutionRequestEvent(Contract(envelope, osClient, encryptionKeyRef)))
            MailboxMeta.FRAGMENT_RESPONSE -> handler.handleSynchronousAck(mailUuid, ExecutionResponseEvent(Contract(envelope, osClient, encryptionKeyRef)))
            else -> throw IllegalStateException("Should not happen, unhandled mailbox key:$mailboxKey")
        }
    }

    private fun handleEnvelopeError(mailUuid: UUID, mailboxKey: String, ownerAudience: Audience, message: ByteArray) {
        val error = Envelopes.EnvelopeError.parseFrom(message)

        require(error.sessionUuid.toUuidOrNull() != null) { "Session uuid is required" }
        require(error.executionUuid.toUuidOrNull() != null) { "Execution uuid is required" }

        handler.handleSynchronousAck(mailUuid, ExecutionErrorEvent(Contract(error, osClient, encryptionKeyRef)))
    }

    private fun MailHandlerFn.handleSynchronousAck(mailUuid: UUID, event: MailboxEvent) {
        if (apply(event)) {
            osClient.osClient.mailboxAck(mailUuid)
        }
    }
}
