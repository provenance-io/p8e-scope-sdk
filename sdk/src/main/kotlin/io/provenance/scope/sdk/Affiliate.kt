package io.provenance.scope.sdk

import io.provenance.scope.contract.proto.Specifications.PartyType
import io.provenance.scope.encryption.model.KeyRef

// import java.security.PrivateKey
// import java.security.PublicKey
// import java.util.UUID
//
// sealed class KeyRef(val publicKey: PublicKey)
// class SmartKeyRef(publicKey: PublicKey, val uuid: UUID) : KeyRef(publicKey)
// class DirectKeyRef(publicKey: PublicKey, val privateKey: PrivateKey) : KeyRef(publicKey)

data class Affiliate(
    val signingKeyRef: KeyRef,
    val encryptionKeyRef: KeyRef,
    val partyType: PartyType,
)
