package io.provenance.scope.sdk

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.AffiliateRepository
import java.security.PublicKey

class AffiliateRepositoryTest : WordSpec() {
    lateinit var affiliateRepository: AffiliateRepository
    lateinit var signingPublicKey: PublicKey
    lateinit var signingAddress: String
    lateinit var encryptionPublicKey: PublicKey
    lateinit var encryptionAddress: String

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        affiliateRepository = AffiliateRepository(false)
        signingPublicKey = ProvenanceKeyGenerator.generateKeyPair().public
        signingAddress = signingPublicKey.getAddress(false)
        encryptionPublicKey = ProvenanceKeyGenerator.generateKeyPair().public
        encryptionAddress = encryptionPublicKey.getAddress(false)
    }

    init {
        "AffiliateRepository" should {
            "look up an affiliate by signing public key" {
                affiliateRepository.addAffiliate(signingPublicKey, encryptionPublicKey)

                val affiliateKeys = affiliateRepository.getAffiliateKeysByAddress(signingAddress)

                affiliateKeys.signingPublicKey shouldBe signingPublicKey
                affiliateKeys.encryptionPublicKey shouldBe encryptionPublicKey
            }
            "look up an affiliate by encryption public key" {
                affiliateRepository.addAffiliate(signingPublicKey, encryptionPublicKey)

                val affiliateKeys = affiliateRepository.getAffiliateKeysByAddress(encryptionAddress)

                affiliateKeys.signingPublicKey shouldBe signingPublicKey
                affiliateKeys.encryptionPublicKey shouldBe encryptionPublicKey
            }
        }
    }
}
