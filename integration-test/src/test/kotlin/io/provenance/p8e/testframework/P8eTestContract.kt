package io.provenance.p8e.testframework

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import io.p8e.ContractManager
import io.p8e.util.toUuidProv
import io.provenance.p8e.testframework.contracts.*
import java.util.*

enum class Facts(val fact: String) {
    FACT1("fact1"),
    FACT2("fact2"),
    FACT3("fact3"),
    FACT4("fact4"),
    FACT7("fact7")
}

class UtilsTest : WordSpec({

    "Util functions" should {
        "Always passes" {
            "left" shouldNotBe "right"
        }

        "generateRandomBytes should be correctly sized" {
            generateRandomBytes(0).size shouldBe 0
            generateRandomBytes(10).size shouldBe 10
            generateRandomBytes(10_000_000).size shouldBe 10_000_000
        }

        "generateRandomBytes should be randomized" {
            // TODO make this test condition better
            //This test could theoretically fail because of the random numbers chosen, but the chance is extremely small
            generateRandomBytes(100) shouldNotBe generateRandomBytes(100)
        }

        "generateRandomBytes of different sizes should not be equal"{
            generateRandomBytes(50) shouldNotBe generateRandomBytes(100)
        }

    }

    "testframework.Contract tests" should {

        val cm: ContractManager = ContractManager.create(
            System.getenv("OWNER_PRIVATE_KEY"),
            System.getenv("API_URL")
        )

        "Builder's map grows with each fact"{
            val builder: P8eTestContractBuilder = P8eTestContractBuilder(SinglePartyContractMedium::class.java)

            builder.addProposedFact(Facts.FACT1.fact, 10)
            builder.factMap.size shouldBe 1

            builder.addProposedFact(Facts.FACT2.fact, 100)
            builder.factMap.size shouldBe 2

            builder.addProposedFact(Facts.FACT3.fact, 5)
            builder.factMap.size shouldBe 3
        }

        "Builder's map's fact's ByteArray is correct size"{
            val builder: P8eTestContractBuilder = P8eTestContractBuilder(SinglePartyContractSmall::class.java)

            builder.addProposedFact(Facts.FACT1.fact, 13)
            builder.addProposedFact(Facts.FACT2.fact, 32)

            builder.factMap[Facts.FACT1.fact]?.size shouldBe 13
            builder.factMap[Facts.FACT2.fact]?.size shouldBe 32
        }

        "Builder's map's fact's are correct name"{
            val builder: P8eTestContractBuilder = P8eTestContractBuilder(SinglePartyContractSmall::class.java)

            builder.addProposedFact(Facts.FACT1.fact, 53)
            builder.addProposedFact(Facts.FACT2.fact, 100)

            builder.factMap.containsKey(Facts.FACT1.fact) shouldBe true
            builder.factMap.containsKey(Facts.FACT2.fact) shouldBe true
            builder.factMap.containsKey(Facts.FACT3.fact) shouldBe false
        }

        "Builder's scopeUuid should be modifiable with setScopeUuid"{
            val builder: P8eTestContractBuilder = P8eTestContractBuilder(SinglePartyContractSmall::class.java)
            val uuid: UUID = UUID.randomUUID()
            builder.setScopeUuid(uuid)
            builder.scopeUuid shouldBe uuid
        }

        "Builder properly builds into a testframework.P8eTestContract"{
            val builder: P8eTestContractBuilder = P8eTestContractBuilder(SinglePartyContractSmall::class.java)
            val uuid: UUID = UUID.randomUUID()
            builder.addProposedFact(Facts.FACT1.fact, 10)
            builder.addProposedFact(Facts.FACT2.fact, 15)
            builder.setScopeUuid(uuid)

            val contract: P8eTestContract = builder.build() as P8eTestContract

            contract.factMap.containsKey(Facts.FACT1.fact) shouldBe true
            contract.factMap[Facts.FACT2.fact]?.size shouldBe 15
            contract.factMap shouldBe builder.factMap
            contract.scopeUuid shouldBe uuid
        }

        "Contract should have designated ContractManager and type should be customizable"{
            val contract: P8eTestContract = P8eTestContractBuilder(SinglePartyContractMedium::class.java)
                                        .addProposedFact(Facts.FACT1.fact, 5)
                                        .setOwnerContractManager(cm).build() as P8eTestContract

            contract.ownerCM shouldBe cm
            contract.type shouldBe SinglePartyContractMedium::class.java
        }

        "Contract cust_cm and omni_cm should be customizable"{
            val cust_cm = ContractManager.create(System.getenv("CUST_PRIVATE_KEY"))
            val omni_cm = ContractManager.create(System.getenv("OMNI_PRIVATE_KEY"))
            val contract: P8eTestContract = P8eTestContractBuilder(MultiPartyContractSmall::class.java)
                .addProposedFact(Facts.FACT1.fact, 2)
                .setCustContractManager(cust_cm)
                .setOmniContractManager(omni_cm).build() as P8eTestContract

            contract.custCM shouldBe cust_cm
            contract.omniCM shouldBe omni_cm
        }

        "Contract should have a default type of SinglePartyContractLarge"{
            val contract: P8eTestContract = P8eTestContractBuilder().addProposedFact(Facts.FACT1.fact, 2).build() as P8eTestContract

            contract.type shouldBe SinglePartyContractLarge::class.java
        }

        "Contract should be able to execute"{
            val builder: P8eTestContractBuilder = P8eTestContractBuilder(SinglePartyContractSmall::class.java)

            val contract: P8eTestContract = builder.addProposedFact(Facts.FACT1.fact, 1000).build() as P8eTestContract

            contract.execute()
        }

        "Result should have proper envelope"{
            val builder: P8eTestContractBuilder = P8eTestContractBuilder(SinglePartyContractSmall::class.java)

            val contract: P8eTestContract = builder.addProposedFact(Facts.FACT1.fact, 1).build() as P8eTestContract

            contract.execute()

            val res: ContractResult = contract.waitForResult()

            res.envelope.ref.groupUuid.toUuidProv() shouldBe contract.groupUuid
            res.result shouldBe ResultState.SUCCESS
        }

        "Multi-Party contracts should be able to execute and be complete"{
            val builder = P8eTestContractBuilder(MultiPartyContractSmall::class.java)
            val contract: P8eTestContract = builder.addProposedFact(Facts.FACT1.fact, 3).build() as P8eTestContract
            contract.execute()
            val res = contract.waitForResult()

            res.envelope.ref.groupUuid.toUuidProv() shouldBe contract.groupUuid
            res.result shouldBe ResultState.SUCCESS
        }

        "Multi-Step contracts should be able to execute"{
            val contract: P8eTestContract = P8eTestContractBuilder(MultiPartyMultiStepContractSmall::class.java)
                .addProposedFact(Facts.FACT1.fact, 3)
                .addProposedFact(Facts.FACT2.fact, 1).build() as P8eTestContract

            contract.execute()
            val res = contract.waitForResult()

            res.envelope.ref.groupUuid.toUuidProv() shouldBe contract.groupUuid
            res.result shouldBe ResultState.SUCCESS

        }

        "More complicated multi-step contract should work too"{
            val contract: P8eTestContract = P8eTestContractBuilder(MultiPartyMultiStepContractMedium::class.java)
                .addProposedFact(Facts.FACT1.fact, 3)
                .addProposedFact(Facts.FACT4.fact, 6)
                .addProposedFact(Facts.FACT7.fact, 2).build() as P8eTestContract

            contract.execute()
            val res = contract.waitForResult()

            res.envelope.ref.groupUuid.toUuidProv() shouldBe contract.groupUuid
            res.result shouldBe ResultState.SUCCESS
        }

    }


})
