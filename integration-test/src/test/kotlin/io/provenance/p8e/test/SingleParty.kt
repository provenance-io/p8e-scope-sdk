package io.provenance.scope.sdk.test

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.sdk.SharedClient
import java.net.URI
import java.security.KeyPair
import java.util.*
//import io.provenance.p8e.shared.extension.logger
import io.provenance.scope.sdk.testframework.contracts.*
import io.provenance.scope.sdk.extensions.uuid

//TODO: Add more assertions to make sure everything is working
//TODO: Maybe re-oragnize some of the files into different packages so everything isn't just under testframework

class SinglePartyTests : WordSpec({
    "Single party contracts" should {
        "allow simple new contract execution" {
        }

        "allow simple update contract execution" {
        }

        "allow setting data access parties" {
        }

        "allow hydrating an existing scope" {
        }
    }
})
