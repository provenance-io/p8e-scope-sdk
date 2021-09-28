package io.provenance.scope.sdk.mailbox

import io.provenance.scope.contract.proto.Envelopes.EnvelopeError
import io.provenance.scope.contract.proto.Envelopes.Envelope

/**
 * An incoming event from a P8e affiliate
 */
sealed class MailboxEvent

/**
 * A request for execution (signing off) on a contract
 *
 * @property [envelope] the envelope with all parties, input/output hashes of functions and signature of the invoking party
 */
class ExecutionRequestEvent(val envelope: Envelope): MailboxEvent()

/**
 * A response from another affiliate's envelope execution
 *
 * @property [envelope] the envelope with the other affiliate's signature
 */
class ExecutionResponseEvent(val envelope: Envelope): MailboxEvent()

/**
 * A notification of an error from another affiliate's processing of an envelope
 *
 * @property [error] details about the error encountered
 */
class ExecutionErrorEvent(val error: EnvelopeError): MailboxEvent()
