package io.provenance.scope

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.provenance.scope.contract.TestContract
import io.provenance.scope.contract.proto.*
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.client.ObjectHash
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.objectstore.util.toPublicKeyProtoOS
import io.provenance.scope.proto.PK
import io.provenance.scope.util.ContractDefinitionException
import io.provenance.scope.util.toProtoUuid
import java.net.URI
import java.util.*

class ContractEngineTest : WordSpec() {
    private lateinit var osClient: CachedOsClient
    private lateinit var contractEngine: ContractEngine
    private val encryptionKeyRef = DirectKeyRef(ProvenanceKeyGenerator.generateKeyPair())
    private val signingKeyRef = DirectKeyRef(ProvenanceKeyGenerator.generateKeyPair())

    private lateinit var testContract: TestContract

    fun createContractSpec(className: String, hash: String): Specifications.ContractSpec.Builder {
        val defSpec = Commons.DefinitionSpec.newBuilder()
            .setType(Commons.DefinitionSpec.Type.PROPOSED)
            .setResourceLocation(
                Commons.Location.newBuilder().setClassname(className)
                    .setRef(Commons.ProvenanceReference.newBuilder().setHash(hash))
            )
            .setName("testRecordOne")
        val conditionSpec = Specifications.ConditionSpec.newBuilder()
            .addInputSpecs(defSpec)
            .setFuncName("printTest")
            .build()
        val participants = HashMap<Specifications.PartyType, PK.PublicKey>()
        participants[Specifications.PartyType.OWNER] = PK.PublicKey.newBuilder().build()
        return Specifications.ContractSpec.newBuilder()
            .setDefinition(defSpec)
            .addConditionSpecs(conditionSpec)
            .addFunctionSpecs(
                Specifications.FunctionSpec.newBuilder().setFuncName("printTest").addInputSpecs(defSpec)
                    .setInvokerParty(Specifications.PartyType.OWNER)
            )
    }

    fun createInputEnvelope(className: String): Envelopes.Envelope.Builder {
        val provenanceReference = Commons.ProvenanceReference.newBuilder().setHash(
            "M8PWxG2TFfO0YzL3sDW/l9"
        ).build()
        val dataLocation = Commons.Location.newBuilder()
            .setClassname("")
            .setRef(provenanceReference).build()

        val proposedRecord = Contracts.ProposedRecord.newBuilder()
            .setName("testRecordOne")
            .setClassname(className)
            .setHash("1234567890")

        return Envelopes.Envelope.newBuilder()
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
                Contracts.Contract.newBuilder()
                    .setSpec(
                        Contracts.Record.newBuilder().setDataLocation(dataLocation)
                    )
                    .addConsiderations(
                        Contracts.ConsiderationProto.newBuilder()
                            .setConsiderationName("testRecord")
                            .setResult(
                                Contracts.ExecutionResult.getDefaultInstance()
                            )
                            .addInputs(proposedRecord)
                    )
                    .addConsiderations(
                        Contracts.ConsiderationProto.newBuilder()
                            .setConsiderationName("printTest")
                            .setResult(
                                Contracts.ExecutionResult.getDefaultInstance()
                            )
                            .addInputs(proposedRecord)
                    )
            )
    }

    override fun beforeTest(testCase: TestCase) {
        mockkConstructor(CachedOsClient::class)
        mockkConstructor(ContractEngine::class)
        mockkConstructor(DefinitionService::class)
        every { anyConstructed<CachedOsClient>().getJar(any(), any()) } returns
                Futures.immediateFuture(
                    "testing".byteInputStream()
                )

        every { anyConstructed<DefinitionService>().addJar(any<String>(), any()) } returns mockk()
        every { anyConstructed<DefinitionService>().addJar(any<KeyRef>(), any()) } returns mockk()

        val osDecryptionThreadCount: Short = 10
        val osConcurrencySize: Short = 20000

        osClient = CachedOsClient(
            OsClient(URI("https://localhost:5000"), 20000L),
            osDecryptionThreadCount,
            osConcurrencySize,
            20000L
        )
        contractEngine = ContractEngine(osClient)

        testContract = TestContract()
    }


    init {
        "ContractEngine.handle" should {
            "return contract with proper values assigned" {
                val spec = createContractSpec(
                    "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName",
                    "M8PWxG2TFfO0YzL3sDW/l9"
                )
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            spec.build()
                        )
                every { anyConstructed<CachedOsClient>().putRecord(any(), any(), any(), any(), any(), any()) } returns
                        Futures.immediateFuture(
                            ObjectHash("M8PWxG2TFfO0YzL3sDW/l9")
                        )
                every { anyConstructed<DefinitionService>().loadClass(any(), any()) } returns TestContract::class.java
                every { anyConstructed<DefinitionService>().loadClass(any()) } returns TestContract::class.java

                val envelope =
                    createInputEnvelope(className = "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName").build()

                val contract = contractEngine.handle(
                    encryptionKeyRef,
                    signingKeyRef,
                    envelope,
                    mutableListOf(signingKeyRef.publicKey)
                )

                contract.contract.considerationsList.map { it.considerationName } shouldContain "testRecord"
                contract.contract.considerationsList.map { it.considerationName } shouldContain "printTest"
                contract.signaturesList.map { it.signer.signingPublicKey.publicKeyBytes} shouldContain signingKeyRef.publicKey.toPublicKeyProtoOS().secp256K1
                contract.contract.spec.dataLocation.ref.hash shouldBe "M8PWxG2TFfO0YzL3sDW/l9"
            }
            "handle an error from executable contract function" {
                val spec = createContractSpec(
                    "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName",
                    "M8PWxG2TFfO0YzL3sDW/l9"
                )
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            spec.build()
                        )
                every { anyConstructed<DefinitionService>().loadClass(any(), any()) } returns TestContract::class.java
                every { anyConstructed<DefinitionService>().loadClass(any()) } returns TestContract::class.java

                val proposedRecord = Contracts.ProposedRecord.newBuilder()
                    .setName("testRecordOne")
                    .setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
                    .setHash("1234567890")

                val envelope =
                    createInputEnvelope(className = "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
                val envelopeNullFunction = envelope.setContract(
                    envelope.contract.toBuilder()
                        .addConsiderations(
                            Contracts.ConsiderationProto.newBuilder()
                                .setConsiderationName("testThrowError")
                                .setResult(
                                    Contracts.ExecutionResult.getDefaultInstance()
                                )
                                .addInputs(proposedRecord)
                        )
                        .build()
                ).build()

                val contract = contractEngine.handle(
                    encryptionKeyRef,
                    signingKeyRef,
                    envelopeNullFunction,
                    mutableListOf(signingKeyRef.publicKey)
                )

                contract.contract.considerationsList.map { it.considerationName } shouldContain "testRecord"
                contract.contract.considerationsList.map { it.considerationName } shouldContain "testThrowError"
                contract.contract.considerationsList.map { it.considerationName } shouldContain "printTest"
                contract.contract.spec.dataLocation.ref.hash shouldBe "M8PWxG2TFfO0YzL3sDW/l9"
                contract.signaturesList.map { it.signer.signingPublicKey.publicKeyBytes} shouldContain signingKeyRef.publicKey.toPublicKeyProtoOS().secp256K1
            }
            "throw a IllegalArgumentException for a hash that isn't long enough" {
                val spec = createContractSpec("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName", "1")
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            spec.build()
                        )

                val envelope =
                    createInputEnvelope(className = "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName").build()

                shouldThrow<IllegalArgumentException> {
                    contractEngine.handle(
                        encryptionKeyRef,
                        signingKeyRef,
                        envelope,
                        mutableListOf(signingKeyRef.publicKey)
                    )
                }
            }
            "throw a ContractDefinitionException for a dataLocation that isn't of type ContractSpec" {
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            HelloWorldExample.ExampleName.getDefaultInstance()
                        )

                val envelope = createInputEnvelope(className = "io.provenance.scope.contract\$TestContract").build()

                shouldThrow<ContractDefinitionException> {
                    contractEngine.handle(
                        encryptionKeyRef,
                        signingKeyRef,
                        envelope,
                        mutableListOf(signingKeyRef.publicKey)
                    )
                }
            }
            "throw a ContractDefinitionException for null result from a function on the contract" {
                val spec = createContractSpec(
                    "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName",
                    "M8PWxG2TFfO0YzL3sDW/l9"
                )
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            spec.build()
                        )
                every { anyConstructed<DefinitionService>().loadClass(any(), any()) } returns TestContract::class.java
                every { anyConstructed<DefinitionService>().loadClass(any()) } returns TestContract::class.java

                val proposedRecord = Contracts.ProposedRecord.newBuilder()
                    .setName("testRecordOne")
                    .setClassname("io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
                    .setHash("1234567890")

                val envelope =
                    createInputEnvelope(className = "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName")
                val envelopeNullFunction = envelope.setContract(
                    envelope.contract.toBuilder()
                        .addConsiderations(
                            Contracts.ConsiderationProto.newBuilder()
                                .setConsiderationName("testReturnNull")
                                .setResult(
                                    Contracts.ExecutionResult.getDefaultInstance()
                                )
                                .addInputs(proposedRecord)
                        )
                        .build()
                ).build()

                shouldThrow<ContractDefinitionException> {
                    contractEngine.handle(
                        encryptionKeyRef,
                        signingKeyRef,
                        envelopeNullFunction,
                        mutableListOf(signingKeyRef.publicKey)
                    )
                }
            }
            "throw a StatusRuntimeException" {
                val spec = createContractSpec(
                    "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName",
                    "M8PWxG2TFfO0YzL3sDW/l9"
                )
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            spec.build()
                        )
                every { anyConstructed<DefinitionService>().loadClass(any(), any()) } throws StatusRuntimeException(
                    Status.ABORTED
                )
                every { anyConstructed<DefinitionService>().loadClass(any()) } throws StatusRuntimeException(
                    Status.ABORTED
                )

                val envelope =
                    createInputEnvelope(className = "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName").build()

                shouldThrow<StatusRuntimeException> {
                    contractEngine.handle(
                        encryptionKeyRef,
                        signingKeyRef,
                        envelope,
                        mutableListOf(signingKeyRef.publicKey)
                    )
                }
            }
            "throw a ContractDefinitionException for not found class load" {
                val spec = createContractSpec(
                    "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName",
                    "M8PWxG2TFfO0YzL3sDW/l9"
                )
                every { anyConstructed<CachedOsClient>().getRecord(any(), any(), any()) } returns
                        Futures.immediateFuture(
                            spec.build()
                        )
                every { anyConstructed<DefinitionService>().loadClass(any(), any()) } throws StatusRuntimeException(
                    Status.NOT_FOUND
                )
                every { anyConstructed<DefinitionService>().loadClass(any()) } throws StatusRuntimeException(
                    Status.NOT_FOUND
                )

                val envelope =
                    createInputEnvelope(className = "io.provenance.scope.contract.proto.HelloWorldExample\$ExampleName").build()

                shouldThrow<ContractDefinitionException> {
                    contractEngine.handle(
                        encryptionKeyRef,
                        signingKeyRef,
                        envelope,
                        mutableListOf(signingKeyRef.publicKey)
                    )
                }
            }
        }
    }
}
