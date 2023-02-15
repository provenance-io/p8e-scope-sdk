package io.provenance.scope.sdk

import com.google.common.util.concurrent.Futures
import com.google.protobuf.Any
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkConstructor
import io.provenance.metadata.v1.MsgAddScopeDataAccessRequest
import io.provenance.metadata.v1.MsgWriteScopeRequest
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.scope.ContractEngine
import io.provenance.scope.contract.TestContract
import io.provenance.scope.contract.TestContractScopeSpecificationDefinition
import io.provenance.scope.contract.proto.*
import io.provenance.scope.contract.proto.Specifications.ContractSpecV2
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.client.ObjectHash
import io.provenance.scope.proto.Common
import io.provenance.scope.proto.PK
import io.provenance.scope.util.base64String
import io.provenance.scope.util.sha256
import io.provenance.scope.util.sha256String
import io.provenance.scope.util.toProtoUuid
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.KeyPair
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ClientTest : WordSpec() {
    lateinit var signingKeyPair: KeyPair
    lateinit var encryptionKeyPair: KeyPair
    lateinit var cleanupHandlers: MutableList<() -> Unit>

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        signingKeyPair = ProvenanceKeyGenerator.generateKeyPair()
        encryptionKeyPair = ProvenanceKeyGenerator.generateKeyPair()
        cleanupHandlers = mutableListOf()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)

        cleanupHandlers.forEach { it.invoke() }
    }

    private fun getContractSpec(): ContractSpecV2 = ContractSpecV2.newBuilder()
        .apply {
            wasmBuilder.setHash("fakeHash")
        }
        .build()

    private fun getClient(spec: ContractSpecV2 = getContractSpec()): Client {
        every { anyConstructed<CachedOsClient>().getJar(any(), any()) } answers {
            when (firstArg<ByteArray>().base64String()) {
                spec.toByteArray().sha256String() -> Futures.immediateFuture(spec.toByteArray())
                else -> throw Exception("Unexpected hash")
            }
        }
        return Client(
            SharedClient(ClientConfig(0, 0, 0, URI.create("httpvlbucincecnvfdltfnjgulnhtl" +
                    "://localhost:5000"), mainNet = false)),
            Affiliate(
                DirectKeyRef(signingKeyPair),
                DirectKeyRef(encryptionKeyPair),
                Specifications.PartyType.OWNER
            )
        ).also {
            cleanupHandlers.add {
                it.close()
                it.awaitTermination(1, TimeUnit.SECONDS)
            }
        }
    }

    init {
        "Client.newSession" should {
            "look up data share affiliates on existing scope that have different signing/encryption keys" {
                val scope = createExistingScope().apply {
                    scopeBuilder.scopeBuilder
                        .clearDataAccess()
                        .addDataAccess(encryptionKeyPair.public.getAddress(false))
                }

                val client = getClient()

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )

                val session = client.newSession(TestContract::class.java, scope.build())

                session.dataAccessKeys shouldContain encryptionKeyPair.public
            }
            "set new session for non existing scope" {
                val client = getClient()

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )

                val session = client.newSession(TestContract::class.java, TestContractScopeSpecificationDefinition::class.java)

                session.contractSpec!!.definition.name shouldBe "TestContract"
                session.contractSpec!!.functionSpecsList[0].funcName shouldBe "testRecordFn"
                session.contractSpec!!.functionSpecsList[0].outputSpec.spec.name shouldBe "testRecord"
            }
        }
        "Client.hydrate" should {
            "hydrate data for the given class" {
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
                val client = getClient()

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )
                val hydrateResponse = shouldNotThrowAny {
                    client.hydrate(HelloWorldData::class.java, scope.build())
                }
                hydrateResponse.name.firstName shouldBe "Test"
                hydrateResponse.name.lastName shouldBe "TestLast"
            }
            "allow for null values for nullable records" {
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
                val client = getClient()

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )
                val hydrateResponse = shouldNotThrowAny {
                    client.hydrate(HelloWorldDataNullable::class.java, scope.build())
                }
                hydrateResponse.name.firstName shouldBe "Test"
                hydrateResponse.name.lastName shouldBe "TestLast"
                hydrateResponse.nullableRecord shouldBe null
            }
            "allow for null value when only record requested is not present and is nullable" {
                val scope = createExistingScope().apply {
                    scopeBuilder.scopeBuilder
                        .clearDataAccess()
                        .addDataAccess(encryptionKeyPair.public.getAddress(false))
                }.clearRecords() // no records

                val client = getClient()

                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = signingKeyPair.public,
                    encryptionPublicKey = encryptionKeyPair.public
                )
                val hydrateResponse = shouldNotThrowAny {
                    client.hydrate(HelloWorldDataOnlyNullable::class.java, scope.build())
                }
                hydrateResponse.nullableRecord shouldBe null
            }
        }

        "Client.execute" should {
            "add new data access keys to scope" {

                val owner = localKeys[2].public.getAddress(false)
                setupContractExecutionMocks(
                    scope = createExistingScope(ownerAddress = owner).build(),
                    dataAccessKeys = listOf(
                        localKeys[0].public.toPublicKeyProto(),
                        localKeys[1].public.toPublicKeyProto(),
                    )
                )

                val client = getClient()
                client.inner.affiliateRepository.addAffiliate(
                    signingPublicKey = localKeys[2].public,
                    encryptionPublicKey = localKeys[2].public
                )

                val builder = createSessionBuilderNoRecords(client, createExistingScope(ownerAddress = owner).build())
                val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()
                builder.addProposedRecord("record2", exampleName)

                val executionResult = client.execute(builder.build()) as SignedResult

                executionResult.messages.first().javaClass shouldBe MsgAddScopeDataAccessRequest::class.java
                (executionResult.messages.first() as MsgAddScopeDataAccessRequest).dataAccessList shouldBe listOf(
                    localKeys[2].public.getAddress(false),
                    localKeys[0].public.getAddress(false),
                )

                (executionResult.messages.first() as MsgAddScopeDataAccessRequest).signersList.first() shouldBe localKeys[2].public.getAddress(false)
            }
            "execute and return a signed result" {
                setupContractExecutionMocks()
                val client = getClient()

                val builder = createSessionBuilderNoRecords(client, createExistingScope(client.inner.affiliateRepository).build())

                val exampleName = HelloWorldExample.ExampleName.newBuilder().setFirstName("Test").build()
                builder.addProposedRecord("record2", exampleName)

                // Create Session and run package contract for tests
                val session = builder.build()
                val envelopePopulatedRecord = session.packageContract(false, client.inner.affiliateRepository)

                val executionResult = client.execute(session)

                (executionResult as SignedResult).envelopeState.input.contract.definition.name shouldBe "record2"
                executionResult.envelopeState.input.contract.definition.resourceLocation.classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"
                executionResult.messages.first().javaClass shouldBe MsgWriteScopeRequest::class.java
                (executionResult.messages.first() as MsgWriteScopeRequest).scope.valueOwnerAddress shouldBe localKeys[2].public.getAddress(false)

                val executionResult2 = client.execute(envelopePopulatedRecord)

                (executionResult2 as FragmentResult).envelopeState.input.contract.definition.name shouldBe "record2"
                executionResult2.envelopeState.input.contract.definition.resourceLocation.classname shouldBe "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName"
            }
        }
    }

    private fun setupContractExecutionMocks(scope: ScopeResponse? = null, dataAccessKeys: List<PK.PublicKey>? = null) {
        val provenanceReference = Commons.ProvenanceReference.newBuilder().setHash(
            "M8PWxG2TFfO0YzL3sDW/l9"
        ).build()
        val dataLocation = Commons.Location.newBuilder()
            .setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
            .setRef(provenanceReference).build()

        val proposedRecord = Contracts.ProposedRecord.newBuilder()
            .setName("record2")
            .setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
            .setHash("1234567890")

        mockkConstructor(ContractEngine::class)

        val signer = PK.SigningAndEncryptionPublicKeys.newBuilder()
            .setSigningPublicKey(localKeys[2].public.toPublicKeyProto())
            .setEncryptionPublicKey(localKeys[2].public.toPublicKeyProto())

        every { anyConstructed<ContractEngine>().handle(any(), any(), any(), any()) } returns
                Envelopes.Envelope.newBuilder()
                    .setScopeSpecUuid(UUID.randomUUID().toProtoUuid())
                    .setNewSession(true)
                    .also { builder ->
                        scope?.let {
                            builder.setNewScope(false)
                            builder.setScope(Any.pack(scope, ""))
                        } ?: run {
                            builder.setNewScope(true)
                        }
                    }
                    .addDataAccess(localKeys[2].public.toPublicKeyProto())
                    .also { builder ->
                        dataAccessKeys?.let { builder.addAllDataAccess(it) }
                    }
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
                                    .setConsiderationName("record2")
                                    .setResult(
                                        Contracts.ExecutionResult.newBuilder()
                                            .setResult(Contracts.ExecutionResult.Result.PASS)
                                            .setOutput(proposedRecord)
                                    ).addInputs(proposedRecord)
                            )
                            .setInvoker(signer)
                            .addAllRecitals(
                                listOf(
                                    Contracts.Recital.newBuilder()
                                        .setSignerRole(Specifications.PartyType.OWNER)
                                        .setSigner(signer)
                                        .build()
                                )
                            )
                            .build()
                    ).addAllSignatures(
                        listOf(
                            Common.Signature.newBuilder()
                                .setSigner(signer)
                                .build()
                        )
                    )
                    .build()

        mockkConstructor(CachedOsClient::class)
        every { anyConstructed<CachedOsClient>().putRecord(any(), any(), any(), any(), any(), any(), any()) } returns Futures.immediateFuture(
            ObjectHash("1234567890")
        )
        every { anyConstructed<CachedOsClient>().getJar(any(), any()) } returns Futures.immediateFuture(
            ByteArrayInputStream(Random.nextBytes(10))
        )
        every { anyConstructed<CachedOsClient>().putJar(any(), any(), any(), any(), any(), any(), any(), any()) } returns Futures.immediateFuture(
            ObjectHash("1234567890")
        )
    }
}
