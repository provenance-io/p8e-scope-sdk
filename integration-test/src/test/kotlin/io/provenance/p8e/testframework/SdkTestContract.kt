package io.provenance.p8e.testframework

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
import io.provenance.p8e.shared.extension.logger
import io.provenance.p8e.testframework.contracts.SdkSinglePartyContractMedium
import io.provenance.p8e.testframework.contracts.SdkSinglePartyContractSmall
import io.provenance.p8e.testframework.contracts.SdkSinglePartyContractSmallModify
import io.provenance.scope.sdk.extensions.uuid

//TODO: Add more assertions to make sure everything is working
//TODO: Maybe re-oragnize some of the files into different packages so everything isn't just under testframework

class SdkTestContractTests : WordSpec({
    "ContractBuilder" should {
        "Have a modifiable owner client"{
            val sharedClient = SharedClient(ClientConfig(
            10_000_000,
            10_000_000,
            10_000_000,
            URI.create("grpc://localhost:5000"),
            5_000,
            mainNet = false)
            )
            val sampleKey: KeyPair = localKeys[0]
            val client: Client = Client(sharedClient,
                Affiliate(
                    DirectKeyRef(sampleKey.public, sampleKey.private),    //Made from local, private keypair from localKeys list
                    DirectKeyRef(sampleKey.public, sampleKey.private),
                    Specifications.PartyType.OWNER
                )
            )

            val contractBuilder: SdkTestContractBuilder = SdkTestContractBuilder()
            contractBuilder.setOwnerClient(client)

            contractBuilder.ownerClient shouldBe client
        }

        "factMap grows with each fact"{
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractMedium::class.java)

            builder.addProposedFact(Facts.FACT1.fact, 10)
            builder.factMap.size shouldBe 1

            builder.addProposedFact(Facts.FACT2.fact, 100)
            builder.factMap.size shouldBe 2

            builder.addProposedFact(Facts.FACT3.fact, 5)
            builder.factMap.size shouldBe 3
        }

        "Builder's map's fact's ByteArray is correct size"{
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)

            builder.addProposedFact(Facts.FACT1.fact, 13)
            builder.addProposedFact(Facts.FACT2.fact, 32)

            builder.factMap[Facts.FACT1.fact]?.size shouldBe 13
            builder.factMap[Facts.FACT2.fact]?.size shouldBe 32
        }

        "Builder's map's fact's are correct name"{
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)

            builder.addProposedFact(Facts.FACT1.fact, 53)
            builder.addProposedFact(Facts.FACT2.fact, 100)

            builder.factMap.containsKey(Facts.FACT1.fact) shouldBe true
            builder.factMap.containsKey(Facts.FACT2.fact) shouldBe true
            builder.factMap.containsKey(Facts.FACT3.fact) shouldBe false
        }

        "Builder's scopeUuid should be modifiable with setScopeUuid"{
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
            val uuid: UUID = UUID.randomUUID()
            builder.setScopeUuid(uuid)
            builder.scopeUuid shouldBe uuid
        }
    }

    "Sdk Contracts" should{
        "Be able to execute single-party contracts"{
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
            builder.addProposedFact(Facts.FACT1.fact, 53)
            builder.addProposedFact(Facts.FACT2.fact, 100)

            val contract: SdkTestContract = builder.build()
            contract.execute()
        }

        "Have a proper SdkContractResult"{
            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
            builder.addProposedFact(Facts.FACT1.fact, 53)
            builder.addProposedFact(Facts.FACT2.fact, 100)

            val contract: SdkTestContract = builder.build()
            val result = contract.execute()

            result.result shouldBe ResultState.SUCCESS
            result.scope shouldNotBe null
        }

        //TODO: Add test for updating a scope(making more builders after calling execute and using the same scope)
        //TODO: Need update contracts to implement this.  See highlighted example on the page in chrome
        //TODO: Use newSession function with 3 parameters
        "Be able to change the scope and still execute"{
            val log = logger()
            log.info("Starting final test...")

            val builder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmall::class.java)
            builder.addProposedFact(Facts.FACT1.fact, 53)
            builder.addProposedFact(Facts.FACT2.fact, 100)

            val contract: SdkTestContract = builder.build()
            val result = contract.execute()

            result.result shouldBe ResultState.SUCCESS
            result.scope shouldNotBe null

            log.info("Making second contract...")

            //TODO: I think the type of the contract will need to change to one of the modify contracts, which needs to be written
            val secondBuilder: SdkTestContractBuilder = SdkTestContractBuilder(SdkSinglePartyContractSmallModify::class.java)
            secondBuilder.setScope(result.scope!!)
            secondBuilder.addProposedFact(Facts.FACT1.fact, 27)
            secondBuilder.addProposedFact(Facts.FACT2.fact, 75)

            val secondContract = secondBuilder.build()
            secondContract.scope shouldBe result.scope
            val secondResult = secondContract.execute()

            secondResult.result shouldBe ResultState.SUCCESS
            secondResult.scope!!.uuid() shouldBe result.scope!!.uuid()
        }
    }
})