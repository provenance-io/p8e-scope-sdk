package io.provenance.scope.encryption.model

import java.security.PrivateKey
import java.security.PublicKey
import java.util.UUID

sealed class KeyRef(val publicKey: PublicKey)
class SmartKeyRef(publicKey: PublicKey, val uuid: UUID) : KeyRef(publicKey)
class DirectKeyRef(publicKey: PublicKey, val privateKey: PrivateKey) : KeyRef(publicKey)
