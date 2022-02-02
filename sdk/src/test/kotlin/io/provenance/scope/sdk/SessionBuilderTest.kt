package io.provenance.p8e.testframework

import com.google.protobuf.ByteString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import io.provenance.metadata.v1.InputSpecification
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.contract.proto.*
import io.provenance.scope.encryption.ecies.ECUtils
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.proto.PK
import io.provenance.scope.sdk.*
import io.provenance.scope.util.toUuid
import java.util.*

class SessionBuilderTest : WordSpec({

    "SessionBuilder.Builder tests" should {
        mockkConstructor(Session.PermissionUpdater::class)
        every { anyConstructed<Session.PermissionUpdater>().saveConstructorArguments() } returns Unit
        every { anyConstructed<Session.PermissionUpdater>().saveProposedFacts(any()) } returns Unit

        "Package Contract Single Record" {
            //Setting up single record test
            val osClient = createClientDummy(0)

            val builder = createSessionBuilderNoRecords(osClient)

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()
            builder.addProposedRecord("record2", exampleName)

            // Create Session and run package contract for tests
            val session = builder.build()
            val envelopePopulatedRecord = session.packageContract(false)

            envelopePopulatedRecord.contract.invoker.signingPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.signingKeyRef.publicKey)))
                .build()
            envelopePopulatedRecord.contract.invoker.encryptionPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.encryptionKeyRef.publicKey)))
                .build()

            envelopePopulatedRecord.contract.considerationsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].considerationName shouldBe "record2"
            envelopePopulatedRecord.contract.considerationsList[0].inputsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].inputsList[0].classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"
        }

        "Package Contract Existing Scope" {
            //Setting up single record test

            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope()

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            builder.addProposedRecord("record2", exampleName)

            val session = builder.build()

            val envelopePopulatedRecord = session.packageContract(false)

            envelopePopulatedRecord.contract.invoker.signingPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.signingKeyRef.publicKey)))
                .build()
            envelopePopulatedRecord.contract.invoker.encryptionPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.encryptionKeyRef.publicKey)))
                .build()

            envelopePopulatedRecord.contract.considerationsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].considerationName shouldBe "record2"
            envelopePopulatedRecord.contract.considerationsList[0].inputsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].inputsList[0].classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"

            envelopePopulatedRecord.contract.inputsCount shouldBe 1
            envelopePopulatedRecord.contract.inputsList[0].name shouldBe "record2"
            envelopePopulatedRecord.contract.inputsList[0].dataLocation.classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"
        }

        "Package Contract Single Record with participants" {
            //Setting up single record test
            val osClient = createClientDummy(0)

            val builder = createSessionBuilderNoRecords(osClient)

            builder.setContractSpec(
                builder.contractSpec!!.toBuilder().addPartiesInvolved(Specifications.PartyType.AFFILIATE).build()
            )
                .addParticipant(Specifications.PartyType.AFFILIATE, localKeys[2].public, localKeys[2].public)

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()
            builder.addProposedRecord("record2", exampleName)

            // Create Session and run package contract for tests
            val session = builder.build()
            val envelopePopulatedRecord = session.packageContract(false)

            envelopePopulatedRecord.contract.invoker.signingPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.signingKeyRef.publicKey)))
                .build()
            envelopePopulatedRecord.contract.invoker.encryptionPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.encryptionKeyRef.publicKey)))
                .build()


            envelopePopulatedRecord.contract.recitalsList[0].signerRole shouldBe Specifications.PartyType.AFFILIATE
            envelopePopulatedRecord.contract.considerationsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].considerationName shouldBe "record2"
            envelopePopulatedRecord.contract.considerationsList[0].inputsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].inputsList[0].classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"
        }

        "handle setting scopeUuid and executionUuid then finish population" {
            val osClient = createClientDummy(0)

            val builder = createSessionBuilderNoRecords(osClient)

            builder.setContractSpec(
                builder.contractSpec!!.toBuilder().addPartiesInvolved(Specifications.PartyType.AFFILIATE).build()
            )
                .addParticipant(Specifications.PartyType.AFFILIATE, localKeys[2].public, localKeys[2].public)

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()
            builder.addProposedRecord("record2", exampleName)
            val scopeUuid = UUID.randomUUID()
            val executionUuid = UUID.randomUUID()
            builder.setScopeUuid(scopeUuid)
            builder.setExecutionUuid(executionUuid)

            // Create Session and run package contract for tests
            val session = builder.build()
            val envelopePopulatedRecord = session.packageContract(false)

            envelopePopulatedRecord.contract.invoker.signingPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.signingKeyRef.publicKey)))
                .build()
            envelopePopulatedRecord.contract.invoker.encryptionPublicKey shouldBe PK.PublicKey.newBuilder()
                .setPublicKeyBytes(ByteString.copyFrom(ECUtils.convertPublicKeyToBytes(osClient.affiliate.encryptionKeyRef.publicKey)))
                .build()

            envelopePopulatedRecord.contract.recitalsList[0].signerRole shouldBe Specifications.PartyType.AFFILIATE
            envelopePopulatedRecord.ref.scopeUuid.value shouldBe scopeUuid.toString()
            envelopePopulatedRecord.executionUuid.value shouldBe executionUuid.toString()
            envelopePopulatedRecord.contract.considerationsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].considerationName shouldBe "record2"
            envelopePopulatedRecord.contract.considerationsList[0].inputsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].inputsList[0].classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"
        }
        "throw ContractDefinitionException when the same participant is added to the participant list twice" {
            val osClient = createClientDummy(0)

            val builder = createSessionBuilderNoRecords(osClient)

            shouldThrow<ContractSpecMapper.ContractDefinitionException> {
                builder.setContractSpec(
                    builder.contractSpec!!.toBuilder().addPartiesInvolved(Specifications.PartyType.AFFILIATE).build()
                )
                    .addParticipant(Specifications.PartyType.AFFILIATE, localKeys[2].public, localKeys[2].public)
                    .addParticipant(Specifications.PartyType.AFFILIATE, localKeys[2].public, localKeys[2].public)
            }

        }
        "throw IllegalStateException when scope is already set" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            shouldThrow<java.lang.IllegalStateException> {
                builder.setScopeUuid(UUID.randomUUID())
            }
        }
        "throw not found error when no record with type == proposed is submitted or when name doesn't match" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()

            shouldThrow<ContractSpecMapper.NotFoundException> {
                builder.addProposedRecord("record45", exampleName)
            }

            builder.setContractSpec(
                Specifications.ContractSpec.newBuilder()
                    .addConditionSpecs(
                        Specifications.ConditionSpec.newBuilder().addInputSpecs(
                            Commons.DefinitionSpec.newBuilder().setType(
                                Commons.DefinitionSpec.Type.FACT
                            )
                                .setName("record2")
                                .build()
                        )
                    )
                    .addFunctionSpecs(
                        Specifications.FunctionSpec.newBuilder().addInputSpecs(
                            Commons.DefinitionSpec.newBuilder().setType(Commons.DefinitionSpec.Type.FACT)
                                .setName("record2")
                        )
                    )
                    .build()
            )

            shouldThrow<ContractSpecMapper.NotFoundException> {
                builder.addProposedRecord("record2", exampleName)
            }
        }

        "throw illegal argument exception when resource location classname doesn't match classname for record" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            builder.setContractSpec(
                Specifications.ContractSpec.newBuilder()
                    .addConditionSpecs(
                        Specifications.ConditionSpec.newBuilder().addInputSpecs(
                            Commons.DefinitionSpec.newBuilder().setType(
                                Commons.DefinitionSpec.Type.PROPOSED
                            )
                                .setName("record2")
                                .setResourceLocation(Commons.Location.newBuilder().setClassname("testfail"))
                                .build()
                        )
                    )
                    .addFunctionSpecs(
                        Specifications.FunctionSpec.newBuilder().addInputSpecs(
                            Commons.DefinitionSpec.newBuilder().setType(Commons.DefinitionSpec.Type.PROPOSED)
                                .setName("record2")
                        )
                    )
                    .build()
            )

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()

            shouldThrow<IllegalArgumentException> {
                builder.addProposedRecord("record2", exampleName)
            }
        }

        "disallow adding a scope when sessions were not requested" {
            val osClient = createClientDummy(0)
            val scopeResponse = createExistingScope().apply {
                requestBuilder.setIncludeSessions(false)
            }

            val exception = shouldThrow<IllegalStateException> {
                createSessionBuilderNoRecords(osClient, scopeResponse.build())
            }
            exception.message shouldBe "Provided scope must include sessions"
        }

        "disallow adding a scope when records were not requested" {
            val osClient = createClientDummy(0)
            val scopeResponse = createExistingScope().apply {
                requestBuilder.setIncludeRecords(false)
            }

            val exception = shouldThrow<IllegalStateException> {
                createSessionBuilderNoRecords(osClient, scopeResponse.build())
            }
            exception.message shouldBe "Provided scope must include records"
        }

        "disallow setting data access keys that aren't on the existing scope" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope().also { builder ->
                builder.scopeBuilder.scopeBuilder
                    .clearDataAccess()
            }

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("hello").build()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            builder.addProposedRecord("record2", exampleName)
            builder.dataAccessKeys.clear()
            builder.addDataAccessKey(localKeys[2].public)

            val session = builder.build()

            val exception = shouldThrow<IllegalStateException> {
                session.packageContract(false)
            }
            exception.message shouldContain localKeys[2].public.getAddress(false)
        }

        "disallow data access keys on the existing scope that are omitted in the proposed session" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope().also { builder ->
                builder.scopeBuilder.scopeBuilder
                    .clearDataAccess()
                    .addDataAccess(localKeys[2].public.getAddress(false))
            }

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            builder.addProposedRecord("record2", exampleName)
            builder.dataAccessKeys.clear()

            val session = builder.build()

            val exception = shouldThrow<IllegalStateException> {
                session.packageContract(false)
            }
            exception.message shouldContain localKeys[2].public.getAddress(false)
        }

        "allow setting data access keys that are the corresponding signing/encryption key to what is listed on the existing scope" {
            val osClient = createClientDummy(0)

            val correspondingEncryptionKeyPair = ProvenanceKeyGenerator.generateKeyPair()

            val scopeResponse = createExistingScope().also { builder ->
                builder.scopeBuilder.scopeBuilder
                    .clearDataAccess()
                    .addDataAccess(localKeys[2].public.getAddress(false))
            }

            val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()

            val builder = createSessionBuilderNoRecords(osClient, scopeResponse.build())

            val affiliateRepository = AffiliateRepository(false).apply {
                addAffiliate(localKeys[2].public, correspondingEncryptionKeyPair.public)
            }

            builder.addProposedRecord("record2", exampleName)
            builder.dataAccessKeys.clear()
            builder.addDataAccessKey(correspondingEncryptionKeyPair.public)

            val session = builder.build()

            val result = session.packageContract(false, affiliateRepository)
            result.dataAccessList.size shouldBe 1
            result.dataAccessList shouldContain correspondingEncryptionKeyPair.public.toPublicKeyProto()
        }

        "Create Session Builder with all given values" {
            val osClient = createClientDummy(0)

            val scopeResponse = createExistingScope().also { builder ->
                builder.scopeBuilder.scopeBuilder
                    .clearDataAccess()
                    .addDataAccess(localKeys[2].public.getAddress(false))
            }
            val defSpec = Commons.DefinitionSpec.newBuilder()
                .setType(Commons.DefinitionSpec.Type.PROPOSED)
                .setResourceLocation(
                    Commons.Location.newBuilder()
                        .setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
                )
                .setName("record2")
            val conditionSpec = Specifications.ConditionSpec.newBuilder()
                .addInputSpecs(defSpec)
                .setFuncName("record2")
                .build()
            val participants = HashMap<Specifications.PartyType, PK.PublicKey>()
            participants[Specifications.PartyType.OWNER] = PK.PublicKey.newBuilder().build()
            val spec = Specifications.ContractSpec.newBuilder()
                .setDefinition(defSpec)
                .addConditionSpecs(conditionSpec)
                .addFunctionSpecs(
                    Specifications.FunctionSpec.newBuilder()
                        .setFuncName("record2")
                        .addInputSpecs(defSpec)
                        .setOutputSpec(Commons.OutputSpec.newBuilder().setSpec(defSpec))
                        .setInvokerParty(Specifications.PartyType.OWNER)
                )
            if (scopeResponse.build() != ScopeResponse.getDefaultInstance()) {
                spec.addInputSpecs(defSpec)
            }
            val provenanceReference = Commons.ProvenanceReference.newBuilder().build()
            var scopeSpecUuid = UUID.randomUUID()
            var session = Session.Builder(scopeSpecUuid)
                .setContractSpec(spec.build())
                .setProvenanceReference(provenanceReference)
                .setClient(osClient)
                .setSessionUuid(UUID.randomUUID())
                .apply {
                    if (scopeResponse.build() != null) {
                        setScope(scopeResponse.build())
                        addDataAccessKey(localKeys[2].public)
                    }
                }.build()

            val envelopePopulatedRecord = session.packageContract(false)

            envelopePopulatedRecord.contract.considerationsCount shouldBe 1
            envelopePopulatedRecord.contract.considerationsList[0].considerationName shouldBe "record2"
            envelopePopulatedRecord.contract.considerationsList[0].inputsCount shouldBe 0

            envelopePopulatedRecord.contract.inputsCount shouldBe 1
            envelopePopulatedRecord.contract.inputsList[0].name shouldBe "record2"
            envelopePopulatedRecord.contract.inputsList[0].dataLocation.classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"

            envelopePopulatedRecord.scopeSpecUuid.value shouldBe scopeSpecUuid.toString()

        }
    }
})

