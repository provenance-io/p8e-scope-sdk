package io.provenance.scope.sdk.mailbox

import io.provenance.metadata.v1.Scope
import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.proto.Encryption.Audience
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.orThrow
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.objectstore.util.toPublicKey
import io.provenance.scope.sdk.extensions.scopeOrNull
import io.provenance.scope.toHex
import org.slf4j.LoggerFactory
import java.util.UUID

typealias MailHandlerFn = (MailboxEvent) -> Boolean?

class PollAffiliateMailbox(val osClient: OsClient, val signingKeyRef: KeyRef, val encryptionKeyRef: KeyRef, val maxResults: Int, val mainNet: Boolean, val handler: MailHandlerFn): Runnable {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun run() {
        osClient.mailboxGet(encryptionKeyRef.publicKey, maxResults).forEach { (mailUuid, dimeInputStream) ->
            dimeInputStream.getDecryptedPayload(encryptionKeyRef).use {
                val bytes = it.readAllBytes()

                if (!it.verify()) {
                    log.warn("Mailbox object verification failure [public key: ${encryptionKeyRef.publicKey.toHex()}] [mailbox uuid: $mailUuid]")
                    return
                }

                log.trace("Received mail from poll $mailUuid")

                if (!dimeInputStream.metadata.containsKey(MailboxMeta.KEY)) {
                    osClient.mailboxAck(mailUuid).also { log.warn("Unhandled mailbox meta: {}", dimeInputStream.metadata) }
                    return
                }

                val mailboxKey = dimeInputStream.metadata[MailboxMeta.KEY]

                when (mailboxKey) {
                    MailboxMeta.FRAGMENT_REQUEST, MailboxMeta.FRAGMENT_RESPONSE -> handleEnvelope(mailUuid, mailboxKey, dimeInputStream.dime.owner, bytes)
                    MailboxMeta.ERROR_RESPONSE -> handleEnvelopeError(mailUuid, mailboxKey, dimeInputStream.dime.owner, bytes)
                    else -> throw IllegalStateException("Unhandled mailbox key: $mailboxKey uuid: $mailUuid")
                }
            }
        }
    }

    private fun handleEnvelope(mailUuid: UUID, mailboxKey: String, ownerAudience: Audience, message: ByteArray) {
        val envelope = Envelope.parseFrom(message)
        // todo: packing scope as an any so we don't have to pull in the raw protos just for this... stupid or not?
        val scope = envelope.scopeOrNull()

        // todo: validation on various uuids being present? Do we still have an execution uuid in play?

        val className = envelope.contract.definition.resourceLocation.classname

        val signingAddress = signingKeyRef.publicKey.getAddress(mainNet)

        // verify affiliate is present on contract/scope
        envelope.contract.recitalsList.map { it.signer.signingPublicKey.toPublicKey().getAddress(mainNet) }.plus(scope?.ownersList?.map { it.address } ?: listOf())
            .firstOrNull { it == signingAddress }
            .orThrow { IllegalStateException("Can't find party on contract execution ${envelope.executionUuid.value} with key ${signingKeyRef.publicKey.toHex()}") }

        when (mailboxKey) {
            MailboxMeta.FRAGMENT_REQUEST -> handler.handleSynchronousAck(mailUuid, ExecutionRequestEvent(envelope))
            MailboxMeta.FRAGMENT_RESPONSE -> handler.handleSynchronousAck(mailUuid, ExecutionResponseEvent(envelope))
            else -> throw IllegalStateException("Should not happen, unhandled mailbox key:$mailboxKey") // todo: error handling around the upper when (mailboxKey) cases
        }
    }

    private fun handleEnvelopeError(mailUuid: UUID, mailboxKey: String, ownerAudience: Audience, message: ByteArray) {
        val error = Envelopes.EnvelopeError.parseFrom(message)

        // todo: validation on various uuids being present?

        handler.handleSynchronousAck(mailUuid, ExecutionErrorEvent(error))

        // todo: mail error to other parties??? Function in old MailboxReaper seems a bit overloaded for error cases...
    }

    private fun MailHandlerFn.handleSynchronousAck(mailUuid: UUID, event: MailboxEvent) {
        invoke(event)
            .takeIf { it == true }
            ?.also {
                osClient.mailboxAck(mailUuid)
            }
    }
}
