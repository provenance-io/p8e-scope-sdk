package io.provenance.scope.util

import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.proto.Envelopes

fun Envelopes.Envelope.scopeOrNull(): ScopeResponse? = scope.takeIf { hasScope() }?.unpack(ScopeResponse::class.java)
