package io.provenance.scope.sdk

import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.contract.proto.Envelopes.Envelope

sealed class ExecutionResult
class SignedResult(val envelope: Envelope): ExecutionResult()
class FragmentResult(val input: Envelope, val result: Envelope)
