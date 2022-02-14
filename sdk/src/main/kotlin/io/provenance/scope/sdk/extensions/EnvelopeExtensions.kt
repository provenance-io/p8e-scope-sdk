package io.provenance.scope.sdk.extensions

import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.Envelopes.EnvelopeState
import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.proto.PK
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.toPublicKey
import io.provenance.scope.sdk.ExecutionResult
import io.provenance.scope.sdk.FragmentResult
import io.provenance.scope.sdk.SignedResult
import io.provenance.scope.util.ValueOwnerException
import io.provenance.scope.util.scopeOrNull

/**
 * Determine if an envelope is fully signed by comparing its signatures with recitals.
 *
 * @return whether the [Envelope] is fully signed by all participants
 */
fun Envelope.isSigned(): Boolean {
    val scopeResponse = scopeOrNull()

    return contract.recitalsList
        .filter { it.hasSigner() }
        .map {
            val keyProto = PK.PublicKey.parseFrom(it.signer.signingPublicKey.toByteArray())
            ECUtils.convertBytesToPublicKey(keyProto.publicKeyBytes.toByteArray()).getAddress(mainNet)
        }
        .plus(
            scopeResponse?.scope?.scope?.ownersList
                ?.map { it.address } ?: listOf()
        ).toSet().let {
            val signatureAddresses = signaturesList.map {
                val keyProto = PK.PublicKey.parseFrom(it.signer.signingPublicKey.toByteArray())
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
 *
 * @param [envelopeState] the existing [EnvelopeState] to merge this [Envelope] into
 *
 * @return a new [EnvelopeState] with the [Envelope]'s results merged in
 */
fun Envelope.mergeInto(envelopeState: EnvelopeState): ExecutionResult {
    val merged = envelopeState.addSignature(this)

    return if (merged.result.isSigned()) {
        SignedResult(merged)
    } else {
        FragmentResult(merged)
    }
}

/**
 * Determine the appropriate default value owner for a scope
 *
 * Logic taken from https://github.com/provenance-io/provenance/blob/4d5445da43cdd04f024137d7b5c61e26f1274727/x/metadata/types/p8e.go#L475
 *
 * @param [mainNet] whether on Provenance mainNet or not
 *
 * @return the address to use as the default value owner for a scope
 */
fun Envelope.getDefaultValueOwner(mainNet: Boolean): String {
    if (!contract.invoker.signingPublicKey.publicKeyBytes.isEmpty) {
        contract.recitalsList.find {
            it.signer.signingPublicKey.publicKeyBytes == contract.invoker.signingPublicKey.publicKeyBytes
        }?.let {
            return it.signer.signingPublicKey.toPublicKey().getAddress(mainNet)
        }
    }

    val roleSearchOrder = listOf(PartyType.OWNER, PartyType.ORIGINATOR)
    roleSearchOrder.forEach { partyType ->
        contract.recitalsList.find {
            it.signerRole == partyType
        }?.let {
            return it.signer.signingPublicKey.toPublicKey().getAddress(mainNet)
        }
    }

    if (contract.recitalsList.isNotEmpty()) {
        return contract.recitalsList.first().signer.signingPublicKey.toPublicKey().getAddress(mainNet)
    }

    throw ValueOwnerException("no suitable party found to be value owner")
}
