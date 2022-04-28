package io.provenance.scope.util

import io.provenance.scope.contract.proto.Envelopes

/**
 * Create an error
 */
fun Envelopes.Envelope.error(message: String, type: Envelopes.EnvelopeError.Type): Envelopes.EnvelopeError =
    Envelopes.EnvelopeError.newBuilder()
        .setUuid(randomProtoUuid())
        .setEnvelope(this)
        .setType(type)
        .setMessage(message)
        .build()
