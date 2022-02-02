package io.provenance.scope.sdk

import io.provenance.scope.encryption.model.SigningAndEncryptionPublicKeys
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.encryption.util.orThrow
import io.provenance.scope.util.AffiliateNotFoundException
import java.security.PublicKey

/**
 * A registry of affiliates to facilitate looking up signing/encryption keys based on Provenance account address
 */
class AffiliateRepository(private val mainNet: Boolean) {
    private val affiliateAddressToPublicKeys = HashMap<String, SigningAndEncryptionPublicKeys>()

    /**
     * Add an affiliate to the repository
     *
     * @param [signingPublicKey] the signing public key of the affiliate
     * @param [encryptionPublicKey] the encryption public key of the affiliate
     */
    fun addAffiliate(signingPublicKey: PublicKey, encryptionPublicKey: PublicKey) {
        val keys = SigningAndEncryptionPublicKeys(signingPublicKey, encryptionPublicKey)
        affiliateAddressToPublicKeys.put(signingPublicKey.getAddress(mainNet), keys)
        affiliateAddressToPublicKeys.put(encryptionPublicKey.getAddress(mainNet), keys)
    }

    /**
     * Add multiple affiliates to the repository
     *
     * @param [affiliateKeys] a list of signing/encryption public keys for affiliates to add
     */
    fun addAllAffiliates(affiliateKeys: List<SigningAndEncryptionPublicKeys>) {
        affiliateAddressToPublicKeys.putAll(affiliateKeys.map { it.signingPublicKey.getAddress(mainNet) to it })
        affiliateAddressToPublicKeys.putAll(affiliateKeys.map { it.encryptionPublicKey.getAddress(mainNet) to it })
    }

    /**
     * Look up an affiliate's keys by address
     *
     * @param [affiliateAddress] the address of the affiliate. Note: this may be the address of either their signing or encryption public key
     *
     * @return the [signing and encryption public keys][SigningAndEncryptionPublicKeys] of the affiliate
     */
    fun tryGetAffiliateKeysByAddress(affiliateAddress: String): SigningAndEncryptionPublicKeys? = affiliateAddressToPublicKeys.get(affiliateAddress)

    /**
     * Look up an affiliate's keys by address
     *
     * @param [affiliateAddress] the address of the affiliate. Note: this may be the address of either their signing or encryption public key
     *
     * @return the [signing and encryption public keys][SigningAndEncryptionPublicKeys] of the affiliate
     */
    fun getAffiliateKeysByAddress(affiliateAddress: String): SigningAndEncryptionPublicKeys = tryGetAffiliateKeysByAddress(affiliateAddress).orThrow { AffiliateNotFoundException("Affiliate with address $affiliateAddress not found") }

    /**
     * Look up the corresponding signing/encryption key address for an affiliate given a supplied signing/encryption address
     *
     * @param [affiliateAddress] the address of the affiliate. Note: this may be the address of either their signing or encryption public key
     *
     * @return the other address for this affiliate, if it exists
     */
    fun tryGetCorrespondingAffiliateAddress(affiliateAddress: String): String? = tryGetAffiliateKeysByAddress(affiliateAddress)?.let {
        listOf(
            it.signingPublicKey.getAddress(mainNet),
            it.encryptionPublicKey.getAddress(mainNet)
        )
    }?.find { it != affiliateAddress }
}
