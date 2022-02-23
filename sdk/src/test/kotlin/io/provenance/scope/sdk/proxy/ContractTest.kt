package io.provenance.scope.sdk.proxy

import com.google.common.util.concurrent.Futures
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.provenance.metadata.v0.Consideration
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.Envelopes
import io.provenance.scope.contract.proto.HelloWorldExample
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import java.util.UUID

class ContractTest: WordSpec() {
    lateinit var envelope: Envelopes.Envelope
    lateinit var osClient: CachedOsClient
    lateinit var encryptionKeyRef: KeyRef
    lateinit var proxy: Contract

    private fun name(first: String = "first" + UUID.randomUUID().toString(), last: String = "last" + UUID.randomUUID().toString()) = HelloWorldExample.ExampleName.newBuilder().setFirstName(first).setLastName(last).build()

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        envelope = Envelopes.Envelope.newBuilder()
            .apply {
                contractBuilder.addConsiderations(Contracts.ConsiderationProto.newBuilder()
                    .addInputs(Contracts.ProposedRecord.newBuilder()
                        .setName("proposedInput1")
                        .setClassname(HelloWorldExample.ExampleName::class.java.name)
                        .setHash("proposedInput1Hash")
                    ).apply {
                        resultBuilder
                            .setResult(Contracts.ExecutionResult.Result.PASS)
                            .outputBuilder
                            .setName("proposedOutput1")
                            .setClassname(HelloWorldExample.ExampleName::class.java.name)
                            .setHash("proposedOutput1Hash")
                    }
                )
            }.build()
        osClient = mockk()
        encryptionKeyRef = ProvenanceKeyGenerator.generateKeyPair().let { DirectKeyRef(it) }
        proxy = Contract(envelope, osClient, encryptionKeyRef)
    }

    init {
        "Contract proxy getProposedRecord" should {
            "return the value for a proposed input" {
                val randomName = name()
                every { osClient.getRecord(HelloWorldExample.ExampleName::class.java.name, "proposedInput1Hash".base64Decode(), encryptionKeyRef) }.returns(Futures.immediateFuture(randomName))

                val record = proxy.getProposedRecord(HelloWorldExample.ExampleName::class.java, "proposedInput1")

                record shouldBe randomName
            }
            "return null for a proposed input that hasn't been provided" {
                val record = proxy.getProposedRecord(HelloWorldExample.ExampleName::class.java, "proposedInput2")

                record shouldBe null
            }
        }
        "Contract proxy getResult" should {
            "return the value for an output" {
                val randomName = name()
                every { osClient.getRecord(HelloWorldExample.ExampleName::class.java.name, "proposedOutput1Hash".base64Decode(), encryptionKeyRef) }.returns(Futures.immediateFuture(randomName))

                val record = proxy.getResult(HelloWorldExample.ExampleName::class.java, "proposedOutput1")

                record shouldBe randomName
            }
            "return null for an output that was skipped" {
                proxy = envelope.toBuilder().apply {
                    contractBuilder.considerationsBuilderList.last()
                        .resultBuilder.setResult(Contracts.ExecutionResult.Result.SKIP)
                }.build().let {
                    Contract(it, osClient, encryptionKeyRef)
                }

                val record = proxy.getResult(HelloWorldExample.ExampleName::class.java, "proposedOutput1")

                record shouldBe null
            }
        }
        "Contract proxy hasResult" should {
            "return true for a function that has a successful output" {
                val result = proxy.hasResult(HelloWorldExample.ExampleName::class.java, "proposedOutput1")

                result shouldBe true
            }
            "return true for a function that has an error output" {
                proxy = envelope.toBuilder().apply {
                    contractBuilder.considerationsBuilderList.last()
                        .resultBuilder.setResult(Contracts.ExecutionResult.Result.FAIL)
                }.build().let {
                    Contract(it, osClient, encryptionKeyRef)
                }

                val result = proxy.hasResult(HelloWorldExample.ExampleName::class.java, "proposedOutput1")

                result shouldBe true
            }
            "return false for a function that has no output" {
                proxy = envelope.toBuilder().apply {
                    contractBuilder.considerationsBuilderList.last()
                        .resultBuilder.setResult(Contracts.ExecutionResult.Result.SKIP)
                }.build().let {
                    Contract(it, osClient, encryptionKeyRef)
                }

                val result = proxy.hasResult(HelloWorldExample.ExampleName::class.java, "proposedOutput1")

                result shouldBe false
            }
        }
    }
}
