package io.provenance.scope.encryption.crypto

import com.fortanix.sdkms.v1.api.SignAndVerifyApi
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.model.SmartKeyRef
import java.security.KeyPair
import java.security.PublicKey

class SignerFactory(
//    private val signAndVerifyApi: SignAndVerifyApi? = null
) {
    fun getSigner(keyRef: KeyRef): SignerImpl = when (keyRef) {
        is DirectKeyRef -> Pen(KeyPair(keyRef.publicKey, keyRef.privateKey))
//        is SmartKeyRef -> when (signAndVerifyApi) {
//            null -> throw IllegalStateException("SignerFactory requires a SignAndVerifyApi instance when using SmartKeyRef")
//            else -> SmartKeySigner(keyRef.uuid.toString(), keyRef.publicKey, signAndVerifyApi)
//        }
        is SmartKeyRef -> throw NotImplementedError("SmartKey support not implemented")
    }
}
