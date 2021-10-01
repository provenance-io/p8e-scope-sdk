package io.provenance.scope

import arrow.core.left
import com.google.common.util.concurrent.Futures
import com.google.protobuf.Message
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.mockk.every
import io.mockk.mockk
import io.provenance.scope.contract.TestContract
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.TestContractProtos
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.orThrow
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.util.ContractDefinitionException
import io.provenance.scope.util.NotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class FunctionTest: WordSpec() {
    private lateinit var osClient: CachedOsClient
    private val encryptionKeyRef = DirectKeyRef(ProvenanceKeyGenerator.generateKeyPair())
    private lateinit var contract: TestContract

    private val inputs = listOf(
        Contracts.ProposedRecord.newBuilder().setName("testRecordInputOne").setClassname(TestContractProtos.TestProto::class.java.name).setHash("0000000000000000").build(),
        Contracts.ProposedRecord.newBuilder().setName("testRecordInputTwo").setClassname(TestContractProtos.TestProto::class.java.name).setHash("1111111111111111").build(),
    )

    private val records = listOf(
        RecordInstance("testRecordOne", TestContractProtos.TestProto::class.java, TestContractProtos.TestProto.newBuilder().setValue("testRecordInputOneValue").build().left()),
        RecordInstance("testRecordTwo", TestContractProtos.TestProto::class.java, TestContractProtos.TestProto.newBuilder().setValue("testRecordInputTwoValue").build().left()),
    )

    override fun beforeTest(testCase: TestCase) {
        osClient = mockk()
        contract = TestContract()
    }

    private fun getMethod(name: String) = TestContract::class.java.methods.find { it.name == name }.orThrow { NotFoundException("Contract method '$name' not found") }

    private fun buildFunction(name: String, inputs: List<Contracts.ProposedRecord> = listOf(), records: List<RecordInstance> = listOf()) = Function(
        encryptionKeyRef,
        osClient,
        contract,
        Contracts.ConsiderationProto.newBuilder().addAllInputs(inputs),
        getMethod(name),
        records
    )

    private fun CachedOsClient.returnAll(message: Message) = every { getRecord(any(), any(), encryptionKeyRef) } returns Futures.immediateFuture(message)
    private fun CachedOsClient.register(input: Contracts.ProposedRecord, message: Message) = every { getRecord(input.classname, input.hash.base64Decode(), encryptionKeyRef) } returns Futures.immediateFuture(message)

    init {
        "Function.canExecute" should {
            "return true when all parameters are available and all parameters are inputs (proposed records)" {
                osClient.returnAll(TestContractProtos.TestProto.getDefaultInstance())

                val function = buildFunction("testRecordTwoInputs", inputs = inputs)

                assert(function.canExecute()) { "Function.canExecute return false when all required inputs were supplied properly" }
            }
            "return false when not all parameters are available and all parameters are inputs (proposed records)" {
                osClient.returnAll(TestContractProtos.TestProto.getDefaultInstance())

                val function = buildFunction("testRecordTwoInputs", inputs = inputs.subList(0, 1))

                assertFalse(function.canExecute(), "Function.canExecute returned true when it not all required inputs were supplied")
            }
            "return true when all parameters are available and all parameters are records" {
                val function = buildFunction("testRecordTwoRecords", records = records)

                assert(function.canExecute()) { "Function.canExecute returned false all required records were supplied" }
            }
            "return false when not all parameters are available and all parameters are records" {
                val function = buildFunction("testRecordTwoRecords", records = records.subList(0, 1))

                assertFalse(function.canExecute(), "Function.canExecute returned true when not all required records were supplied")
            }
            "return true when all inputs/records are supplied and parameters are both inputs and records" {
                osClient.returnAll(TestContractProtos.TestProto.getDefaultInstance())

                val function = buildFunction("testRecordOneInputOneRecord", inputs = inputs.subList(0, 1), records = records.subList(0, 1))

                assert(function.canExecute()) { "Function.canExecute returned false when all required inputs and records were supplied" }
            }
            "return false when only all inputs are supplied and parameters are both inputs and records" {
                osClient.returnAll(TestContractProtos.TestProto.getDefaultInstance())

                val function = buildFunction("testRecordOneInputOneRecord", inputs = inputs.subList(0, 1))

                assertFalse(function.canExecute(), "Function.canExecute returned true not all required records were supplied")
            }
            "return false when only all records are supplied and parameters are both inputs and records" {
                val function = buildFunction("testRecordOneInputOneRecord", records = records)

                assertFalse(function.canExecute(), "Function.canExecute returned true when not all required inputs were supplied")
            }
            "return false when input supplied is of wrong type" {
                osClient.returnAll(TestContractProtos.TestProto2.getDefaultInstance())

                val function = buildFunction("testRecordTwoInputs", inputs = inputs)

                assertFalse(function.canExecute(), "Function.canExecute returned true when required input was of wrong type")
            }
            "return false when record supplied is of wrong type" {
                val function = buildFunction("testRecordTwoRecords", records = records.map { RecordInstance(it.name, TestContractProtos.TestProto2::class.java, it.messageOrCollection) })

                assertFalse(function.canExecute(), "Function.canExecute returned true when required record was of wrong type")
            }
        }
        "Function construction" should {
            "throw a ContractDefinitionException for a parameter without an Input or Record annotation" {
                shouldThrow<ContractDefinitionException> {
                    buildFunction("testRecordNonAnnotatedArgument")
                }
            }
            "throw a ContractDefinitionException for a parameter with both an Input and Record annotation" {
                shouldThrow<ContractDefinitionException> {
                    buildFunction("testRecordDoubleAnnotatedArgument")
                }
            }
        }
        "Function invoke" should {
            "Return the expected value" {
                osClient.register(inputs[0], TestContractProtos.TestProto.newBuilder().setValue("testRecordInputOneValue").build())
                osClient.register(inputs[1], TestContractProtos.TestProto.newBuilder().setValue("testRecordInputTwoValue").build())

                val function = buildFunction("testRecordTwoInputs", inputs = inputs)

                val result = function.invoke()

                assertNotNull(result.second) { "Function invocation returned null" }
                assertEquals<Class<*>>(TestContractProtos.TestProto::class.java, result.second!!.javaClass, "Function invocation returned wrong type")
                assertEquals("testRecordInputOneValuetestRecordInputTwoValue", (result.second as TestContractProtos.TestProto).value, "Function invocation returned wrong value (should have concatenated the input values)")
            }
            "Throw an exception if the all parameters are not provided" {
                osClient.returnAll(TestContractProtos.TestProto.getDefaultInstance())

                val function = buildFunction("testRecordTwoInputs", inputs = inputs.subList(0, 1))

                shouldThrow<IllegalArgumentException> {
                    function.invoke()
                }
            }
        }
    }
}
