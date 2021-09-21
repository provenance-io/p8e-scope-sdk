package io.provenance.scope.sdk

import io.provenance.scope.encryption.model.SigningAndEncryptionPublicKeys
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.orThrow
import io.provenance.scope.util.AffiliateNotFoundException
import java.security.PublicKey

class AffiliateRepository(private val mainNet: Boolean) {
    private val affiliateAddressToPublicKeys = HashMap<String, SigningAndEncryptionPublicKeys>()

    fun addAffiliate(signingPublicKey: PublicKey, encryptionPublicKey: PublicKey) {
        val keys = SigningAndEncryptionPublicKeys(signingPublicKey, encryptionPublicKey)
        affiliateAddressToPublicKeys.put(signingPublicKey.getAddress(mainNet), keys)
        affiliateAddressToPublicKeys.put(encryptionPublicKey.getAddress(mainNet), keys)
    }

    fun addAllAffiliates(affiliateKeys: List<SigningAndEncryptionPublicKeys>) {
        affiliateAddressToPublicKeys.putAll(affiliateKeys.map { it.signingPublicKey.getAddress(mainNet) to it })
        affiliateAddressToPublicKeys.putAll(affiliateKeys.map { it.encryptionPublicKey.getAddress(mainNet) to it })
    }

    fun getAffiliateKeysByAddress(affiliateAddress: String) = affiliateAddressToPublicKeys.get(affiliateAddress).orThrow { AffiliateNotFoundException("Affiliate with address $affiliateAddress not found") }
}
