package io.provenance.scope.sdk.extensions

import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.util.getAddress
import io.provenance.scope.util.toEncryptionPublicKey
import io.provenance.scope.util.toHex
import io.provenance.scope.util.toPublicKey

/**
 * Determine if an envelope is fully signed by comparing its signatures with recitals.
 */
fun Envelope.isSigned(scope: ScopeResponse?, mainNet: Boolean): Boolean {
    return contract.recitalsList
        .filter { it.hasSigner() }
        .map { it.signer.signingPublicKey.toEncryptionPublicKey().toPublicKey().getAddress(mainNet) }
        .toSet()
        .plus(
            scope?.scope?.scope?.ownersList
                ?.map { it.address }
                ?.toSet()
        ).let {
            val signatureAddresses = signaturesList.map { it.signer.signingPublicKey.toEncryptionPublicKey().toPublicKey().getAddress(mainNet) }.toSet()
            it.all { address -> signatureAddresses.contains(address) }
        }
}
