package io.provenance.scope.sdk.mailbox

import io.provenance.scope.contract.proto.Envelopes.EnvelopeError
import io.provenance.scope.contract.proto.Envelopes.Envelope

sealed class MailboxEvent

class ExecutionRequestEvent(val envelope: Envelope): MailboxEvent()
class ExecutionResponseEvent(val envelope: Envelope): MailboxEvent()
class ExecutionErrorEvent(val error: EnvelopeError): MailboxEvent()
