package io.provenance.scope.sdk.extensions

import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.Envelopes.EnvelopeState
import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.ExecutionResult
import io.provenance.scope.sdk.FragmentResult
import io.provenance.scope.sdk.SignedResult
import io.provenance.scope.util.scopeOrNull

/**
 * Determine if an envelope is fully signed by comparing its signatures with recitals.
 */
fun Envelope.isSigned(): Boolean {
    val scopeResponse = scopeOrNull()

    return contract.recitalsList
        .filter { it.hasSigner() }
        .map {
            val keyProto = PublicKeys.PublicKey.parseFrom(it.signer.signingPublicKey.toByteArray())
            ECUtils.convertBytesToPublicKey(keyProto.publicKeyBytes.toByteArray()).getAddress(mainNet)
        }
        .plus(
            scopeResponse?.scope?.scope?.ownersList
                ?.map { it.address } ?: listOf()
        ).toSet().let {
            val signatureAddresses = signaturesList.map {
                val keyProto = PublicKeys.PublicKey.parseFrom(it.signer.signingPublicKey.toByteArray())
                ECUtils.convertBytesToPublicKey(keyProto.publicKeyBytes.toByteArray()).getAddress(mainNet)
            }.toSet()
            it.all { address -> signatureAddresses.contains(address) }
        }
}

fun EnvelopeState.addSignature(envelope: Envelope): EnvelopeState {
    require(envelope.signaturesCount == 1) { "Executed contract must have a signature" }

    return toBuilder().apply {
        resultBuilder.addSignatures(envelope.signaturesList.first())
    }.build()
}

/**
 * Merge an envelope's signature into an existing executed envelope
 */
fun Envelope.mergeInto(envelopeState: EnvelopeState): ExecutionResult {
    val merged = envelopeState.addSignature(this)

    return if (merged.result.isSigned()) {
        SignedResult(merged)
    } else {
        FragmentResult(merged)
    }
}
