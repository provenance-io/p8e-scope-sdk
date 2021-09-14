package io.provenance.scope.sdk.mailbox

import cosmos.tx.signing.v1beta1.Signing
import io.provenance.metadata.v1.Scope
import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.model.SigningAndEncryptionPublicKeys
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.objectstore.util.orThrow
import io.provenance.scope.objectstore.util.toPublicKey
import io.provenance.scope.sdk.AffiliateRepository
import io.provenance.scope.util.scopeOrNull
import io.provenance.scope.util.toUuid
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.util.UUID

class MailboxService(private val osClient: OsClient, private val affiliateRepository: AffiliateRepository) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Fragment an envelope by sending inputs to additional signers.
     *
     * @param [encryptionPublicKey] The encryption public key of the owner of the message
     * @param [signer] The signer for the owner of the message
     * @param [envelope] The envelope to fragment
     */
    fun fragment(encryptionPublicKey: PublicKey, signer: SignerImpl, envelope: Envelope) {
        val invokerPublicKey = envelope.contract.invoker.signingPublicKey.toPublicKey()

        if (invokerPublicKey != signer.getPublicKey()) {
            // todo: log or throw exception on key mismatch (only invoker should be able to fragment)
            return
        }

        val scope = envelope.scopeOrNull()

        val scopeOwners = scope?.scope?.scope?.ownersList
            ?.map { affiliateRepository.getAffiliateKeysByAddress(it.address).encryptionPublicKey }
            ?.toSet() ?: setOf()

        val additionalAudiences = envelope.contract.recitalsList
            .filter { it.hasSigner() }
            // Even though owner is in the recital list, mailbox client will ignore/filter for you
            .map { it.signer.encryptionPublicKey.toPublicKey() }
            .toSet()
            .plus(scopeOwners)

        osClient.put(
            envelope,
            encryptionPublicKey,
            signer,
            additionalAudiences,
            MailboxMeta.MAILBOX_REQUEST
        )
    }

    /**
     * Return the executed fragment back to originator of fragment.
     *
     * @param [encryptionPublicKey] The owner of the message
     * @param [signer] The signer for the owner of the message
     * @param [env] The executed envelope to return
     */
    fun result(encryptionPublicKey: PublicKey, signer: SignerImpl, envelope: Envelope) {
        val additionalAudiences = setOf(envelope.contract.invoker.encryptionPublicKey.toPublicKey())

        osClient.put(
            envelope,
            encryptionPublicKey,
            signer,
            additionalAudiences,
            MailboxMeta.MAILBOX_RESPONSE
        )
    }

    /**
     * Send envelope error to audiences of an envelope.
     *
     * @param [encryptionPublicKey] The owner of the message
     * @param [signer] The signer for the owner of the message
     * @param [error] The envelope error to return
     * @param [audienceSigningPublicKeyFilter] Audiences to filter out by
     */
    fun error(encryptionPublicKey: PublicKey, signer: SignerImpl, error: Envelopes.EnvelopeError, vararg audienceSigningPublicKeyFilter: PublicKey) {
        error.envelope.contract.recitalsList
            // Even though owner is in the recital list, mailbox client will ignore/filter for you
            .map { SigningAndEncryptionPublicKeys(it.signer.signingPublicKey.toPublicKey(), it.signer.encryptionPublicKey.toPublicKey()) }
            .filterNot { audienceSigningPublicKeyFilter.contains(it.signingPublicKey) }
            .toSet()
            .run { error(encryptionPublicKey, signer, this, error) }
    }

    /**
     * Send envelope error to audience(s).
     *
     * @param [encryptionPublicKey] The owner of the message
     * @param [signer] The signer for the owner of the message
     * @param [audiencePublicKeys] Public key(s) to send mail to
     * @param [error] The envelope error to return
     */
    fun error(encryptionPublicKey: PublicKey, signer: SignerImpl, audiencePublicKeys: Collection<SigningAndEncryptionPublicKeys>, error: Envelopes.EnvelopeError) {
        log.info("Sending error result env:{}, error type:{}", error.sessionUuid.toUuid(), error.type.name)

        osClient.put(
            uuid = UUID.randomUUID(),
            message = error,
            encryptionPublicKey = encryptionPublicKey,
            signer = signer,
            additionalAudiences = audiencePublicKeys.map { it.encryptionPublicKey }.toSet(),
            metadata = MailboxMeta.MAILBOX_ERROR
        )
    }
}
