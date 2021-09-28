package io.provenance.p8e.testframework.io.provenance.scope.sdk

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContain
import io.provenance.p8e.testframework.createExistingScope
import io.provenance.scope.contract.TestContract
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.sdk.SharedClient
import java.net.URI

class ClientTest : WordSpec() {
    init {
        "Client.newSession" should {
            "look up data share affiliates on existing scope that have different signing/encryption keys" {
                val signingKeyPair = ProvenanceKeyGenerator.generateKeyPair()
                val encryptionKeyPair = ProvenanceKeyGenerator.generateKeyPair()

                val scope = createExistingScope().apply {
                    scopeBuilder.scopeBuilder
                        .clearDataAccess()
                        .addDataAccess(encryptionKeyPair.public.getAddress(false))
                }

                val client = Client(SharedClient(ClientConfig(0, 0, 0, URI.create("http://localhost:5000"), mainNet = false)), Affiliate(
                    DirectKeyRef(signingKeyPair),
                    DirectKeyRef(encryptionKeyPair),
                    Specifications.PartyType.OWNER
                ))

                client.inner.affiliateRepository.addAffiliate(signingPublicKey = signingKeyPair.public, encryptionPublicKey = encryptionKeyPair.public)

                val session = client.newSession(TestContract::class.java, scope.build())

                session.dataAccessKeys shouldContain encryptionKeyPair.public
            }
        }
    }
}
