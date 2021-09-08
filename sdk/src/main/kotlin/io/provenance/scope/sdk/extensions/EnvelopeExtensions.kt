package io.provenance.scope.sdk.extensions

import io.provenance.metadata.v1.Scope
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.proto.Envelopes.Envelope
import io.provenance.scope.contract.proto.PublicKeys
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.util.getAddress

/**
 * Determine if an envelope is fully signed by comparing its signatures with recitals.
 */
fun Envelope.isSigned(mainNet: Boolean): Boolean {
    val scope = if (hasScope()) this.scope.unpack(Scope::class.java) else null

    return contract.recitalsList
        .filter { it.hasSigner() }
        .map {
            val keyProto = PublicKeys.PublicKey.parseFrom(it.signer.signingPublicKey.toByteArray())
            ECUtils.convertBytesToPublicKey(keyProto.publicKeyBytes.toByteArray()).getAddress(mainNet)
        }
        .plus(
            scope?.ownersList
                ?.map { it.address } ?: listOf()
        ).toSet().let {
            val signatureAddresses = signaturesList.map {
                val keyProto = PublicKeys.PublicKey.parseFrom(it.signer.signingPublicKey.toByteArray())
                ECUtils.convertBytesToPublicKey(keyProto.publicKeyBytes.toByteArray()).getAddress(mainNet)
            }.toSet()
            it.all { address -> signatureAddresses.contains(address) }
        }
}

fun Envelope.scopeOrNull(): Scope? = scope.takeIf { hasScope() }?.unpack(Scope::class.java)
