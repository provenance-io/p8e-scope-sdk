package io.provenance.scope.sdk.mailbox

import io.provenance.scope.contract.proto.Envelopes.EnvelopeError
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.sdk.proxy.Contract

/**
 * An incoming event from a P8e affiliate
 */
sealed class MailboxEvent

/**
 * A request for execution (signing off) on a contract
 *
 * @property [contract] the contract with all parties, input/output hashes of functions and signature of the invoking party
 */
class ExecutionRequestEvent(val contract: Contract): MailboxEvent()

/**
 * A response from another affiliate's envelope execution
 *
 * @property [contract] the contract with the other affiliate's signature
 */
class ExecutionResponseEvent(val contract: Contract): MailboxEvent()

/**
 * A notification of an error from another affiliate's processing of an envelope
 *
 * @property [contract] details about the contract, including the error encountered
 */
class ExecutionErrorEvent(val contract: Contract): MailboxEvent()
