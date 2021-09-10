package io.provenance.scope.sdk.test

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.proto.Specifications
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.client.OsClient
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
import io.provenance.scope.sdk.testframework.*
import io.provenance.scope.util.toUuid
import io.provenance.scope.sdk.testframework.proto.RandomBytes

class SinglePartyTests : WordSpec({
    //All of these tests use the small 2 fact/record contracts for these tests, but any size contract will work
    "Single party contracts" should {
        "allow simple new contract execution" {
            //Create a builder and propose some facts/records
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
            builder.addProposedRecord("fact1", 5).addProposedRecord("fact2", 27)

            //Create the TestContract from the builder and execute it and get back the results
            val contract: SdkTestContract = builder.build()
            val result: SdkContractResult = contract.execute()

            //Contract should complete
            result.result shouldBe ResultState.SUCCESS
        }

        "allow simple update contract execution" {
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
            builder.addProposedRecord("fact1", 9).addProposedRecord("fact2", 3)
            val result = builder.build().execute()

            //One of the things the result contains is the scope as ScopeResponse?
            val scope: ScopeResponse = result.scope!!

            val newBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmallModify::class.java)

            //All of a builder's functions except for build returns itself so they can be strung together
            newBuilder.addProposedRecord("fact2", 30).setScope(scope)
            val newResult: SdkContractResult = newBuilder.build().execute()

            newResult.result shouldBe ResultState.SUCCESS
        }

        "allow setting data access parties" {
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
            builder.addProposedRecord("fact1", 9).addProposedRecord("fact2", 3)

            //Made from local, private keypair from localKeys list from Utils.kt(ownerClient uses localKeys[0]
            val custAffiliate = Affiliate(
                DirectKeyRef(localKeys[1].public, localKeys[1].private),
                DirectKeyRef(localKeys[1].public, localKeys[1].private),
                Specifications.PartyType.CUSTODIAN
            )

            //Add an access key
            builder.addAccessKey(custAffiliate.encryptionKeyRef.publicKey)

            val contract = builder.build()
            val result = contract.execute()
            val scope: ScopeResponse = result.scope!!

            //Check to make sure the access key is within the data access list
            scope.scope.scope.dataAccessList.contains(
                custAffiliate.encryptionKeyRef.publicKey.getAddress(mainNet=false)) shouldBe true

        }

        "allow hydrating an existing scope" {
            data class SmallContractData(
                @Record("fact1") val fact1Data: RandomBytes.Data,
                @Record("fact2") val fact2Data: RandomBytes.Data
            )

            val clazz = SdkSinglePartyContractSmall::class.java
            val builder = SdkTestContractBuilder(clazz)
            val contract = builder.addProposedRecord("fact1", 5)
                .addProposedRecord("fact2", 3).build()
            val result = contract.execute()

            result.result shouldBe ResultState.SUCCESS

            val fact1Val = contract.recordMap.get("fact1")
            val fact2Val = contract.recordMap.get("fact2")
            val hydrated = contract.ownerClient.hydrate(SmallContractData::class.java, result.scope!!)

            hydrated.fact1Data.data shouldBe fact1Val!!.toByteString()
            hydrated.fact2Data.data shouldBe fact2Val!!.toByteString()
        }
    }
})
