package io.provenance.scope.sdk

import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.orThrow
import java.security.PublicKey

class AffiliateRepository(private val mainNet: Boolean) {
    private val affiliateAddressToPublicKeys = HashMap<String, SigningAndEncryptionJavaPublicKeys>()

    data class SigningAndEncryptionJavaPublicKeys(val signingPublicKey: PublicKey, val encryptionPublicKey: PublicKey)
    class AffiliateNotFoundException(message: String) : Exception(message)

    fun addAffiliate(signingPublicKey: PublicKey, encryptionPublicKey: PublicKey) {
        affiliateAddressToPublicKeys.put(signingPublicKey.getAddress(mainNet), SigningAndEncryptionJavaPublicKeys(signingPublicKey, encryptionPublicKey))
    }

    fun addAllAffiliates(affiliateKeys: List<SigningAndEncryptionJavaPublicKeys>) {
        affiliateAddressToPublicKeys.putAll(affiliateKeys.map { it.signingPublicKey.getAddress(mainNet) to it })
    }

    fun getAffiliateKeysByAddress(affiliateAddress: String) = affiliateAddressToPublicKeys.get(affiliateAddress).orThrow { AffiliateNotFoundException("Affiliate with address $affiliateAddress not found") }
}
