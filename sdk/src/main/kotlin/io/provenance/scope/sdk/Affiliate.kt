package io.provenance.scope.sdk

import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.model.KeyRef

data class Affiliate(
    val signingKeyRef: KeyRef,
    val encryptionKeyRef: KeyRef,
    val partyType: PartyType,
)
