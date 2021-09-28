package io.provenance.scope.sdk

import com.google.common.util.concurrent.Futures
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkConstructor
import io.provenance.scope.ContractEngine
import io.provenance.scope.contract.TestContract
import io.provenance.scope.contract.TestContractScopeSpecificationDefinition
import io.provenance.scope.contract.proto.*
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.util.toProtoUuid
import java.net.URI
import java.util.*

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
                
                val client = Client(
                    SharedClient(ClientConfig(0, 0, 0, URI.create("http://localhost:5000"), mainNet = false)),
                    Affiliate(
                        DirectKeyRef(signingKeyPair),
                        DirectKeyRef(encryptionKeyPair),
                        Specifications.PartyType.OWNER
                    )
                )

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )

                val session = client.newSession(TestContract::class.java, scope.build())

                session.dataAccessKeys shouldContain encryptionKeyPair.public
            }
            "set new session for non existing scope" {

                val signingKeyPair = ProvenanceKeyGenerator.generateKeyPair()
                val encryptionKeyPair = ProvenanceKeyGenerator.generateKeyPair()

                val client = Client(
                    SharedClient(ClientConfig(0, 0, 0, URI.create("http://localhost:5000"), mainNet = false)),
                    Affiliate(
                        DirectKeyRef(signingKeyPair),
                        DirectKeyRef(encryptionKeyPair),
                        Specifications.PartyType.OWNER
                    )
                )

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )

                val session =
                    client.newSession(TestContract::class.java, TestContractScopeSpecificationDefinition::class.java)

                session.spec!!.definition.name shouldBe "TestContract"
                session.spec!!.functionSpecsList[0].funcName shouldBe "printTest"
                session.spec!!.functionSpecsList[0].outputSpec.spec.name shouldBe "testRecord"
            }
        }
        "Client.hydrate" should {
            "hydrate data for the given class" {
                val signingKeyPair = ProvenanceKeyGenerator.generateKeyPair()
                val encryptionKeyPair = ProvenanceKeyGenerator.generateKeyPair()

                val scope = createExistingScope().apply {
                    scopeBuilder.scopeBuilder
                        .clearDataAccess()
                        .addDataAccess(encryptionKeyPair.public.getAddress(false))
                }

                mockkConstructor(CachedOsClient::class)
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").setLastName("TestLast")
                                .build()
                        )
                val client = Client(
                    SharedClient(ClientConfig(0, 0, 0, URI.create("http://localhost:5000"), mainNet = false)),
                    Affiliate(
                        DirectKeyRef(signingKeyPair.public, signingKeyPair.private),
                        DirectKeyRef(encryptionKeyPair.public, encryptionKeyPair.private),
                        Specifications.PartyType.OWNER
                    )
                )

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )
                var hydrateResponse: HelloWorldData =
                    HelloWorldData(name = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test3").build())
                shouldNotThrowAny {
                    hydrateResponse = client.hydrate(HelloWorldData::class.java, scope.build())
                }
                hydrateResponse.name.firstName shouldBe "Test"
                hydrateResponse.name.lastName shouldBe "TestLast"
            }
        }

        "Client.execute" should {
            "execute and return a signed result" {
                val signingKeyPair = ProvenanceKeyGenerator.generateKeyPair()
                val encryptionKeyPair = ProvenanceKeyGenerator.generateKeyPair()

                val provenanceReference = Commons.ProvenanceReference.newBuilder().setHash(
                    "M8PWxG2TFfO0YzL3sDW/l9"
                ).build()
                val dataLocation = Commons.Location.newBuilder()
                    .setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
                    .setRef(provenanceReference).build()

                mockkConstructor(ContractEngine::class)
                every { anyConstructed<ContractEngine>().handle(any(), any(), any(), any()) } returns
                        Envelopes.Envelope.newBuilder()
                            .setNewScope(true)
                            .setScopeSpecUuid(UUID.randomUUID().toProtoUuid())
                            .setNewSession(true)
                            .setRef(
                                Commons.ProvenanceReference.newBuilder()
                                    .setScopeUuid(UUID.randomUUID().toProtoUuid())
                                    .setSessionUuid(UUID.randomUUID().toProtoUuid())
                                    .build()
                            )
                            .setContract(
                                Contracts.Contract.newBuilder().setSpec(
                                    Contracts.Record.newBuilder().setDataLocation(dataLocation)
                                )
                                    .addConsiderations(
                                        Contracts.ConsiderationProto.newBuilder()
                                            .setConsiderationName("TestConsideration")
                                            .setResult(
                                                Contracts.ExecutionResult.newBuilder()
                                                    .setResult(Contracts.ExecutionResult.Result.PASS)
                                            ).addInputs(
                                                Contracts.ProposedRecord.newBuilder()
                                                    .setName("Test Proposed Record")
                                                    .setClassname("Test Class Name")
                                                    .setHash("1234567890")
                                            )
                                    )
                                    .build()
                            ).build()

                val client = Client(
                    SharedClient(ClientConfig(0, 0, 0, URI.create("http://localhost:5000"), mainNet = false)),
                    Affiliate(
                        DirectKeyRef(signingKeyPair),
                        DirectKeyRef(encryptionKeyPair),
                        Specifications.PartyType.OWNER
                    )
                )

                val builder = createSessionBuilderNoRecords(client)

                val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()
                builder.addProposedRecord("record2", exampleName)

                // Create Session and run package contract for tests
                val session = builder.build()
                val envelopePopulatedRecord = session.packageContract(false)

                var executionResult = client.execute(session)

                (executionResult as SignedResult).envelopeState.input.contract.definition.name shouldBe "record2"
                executionResult.envelopeState.input.contract.definition.resourceLocation.classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"

                executionResult = client.execute(envelopePopulatedRecord)

                (executionResult as FragmentResult).envelopeState.input.contract.definition.name shouldBe "record2"
                executionResult.envelopeState.input.contract.definition.resourceLocation.classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"

            }
        }
    }
}
