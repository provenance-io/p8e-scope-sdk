package io.provenance.scope

import com.google.common.util.concurrent.Futures
import com.google.protobuf.Message
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.provenance.scope.contract.BadTestContract
import io.provenance.scope.contract.SimpleTestContract
import io.provenance.scope.contract.TestContract
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.proto.Commons
import io.provenance.scope.contract.proto.Contracts
import io.provenance.scope.contract.proto.TestContractProtos
import io.provenance.scope.contract.spec.P8eContract
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.objectstore.util.base64EncodeString
import io.provenance.scope.util.ContractDefinitionException
import kotlin.random.Random
import kotlin.reflect.KClass

private class DefinitelyNotAContract

private class DoubleConstructorContract(someValue: TestContractProtos.TestProto): P8eContract() {
    constructor(someValue: TestContractProtos.TestProto, someOtherValue: TestContractProtos.TestProto) : this(someValue) {

    }
}

private class ContractWithUnAnnotatedConstructorArg(val someRecord: TestContractProtos.TestProto): P8eContract()

private class ContractWithOneRecord(@Record(name = "someRecord") val someRecord: TestContractProtos.TestProto): P8eContract()

class ContractWrapperTest: WordSpec() {
    private val encryptionKeyRef = DirectKeyRef(ProvenanceKeyGenerator.generateKeyPair())
    private lateinit var definitionService: DefinitionService
    private lateinit var osClient: CachedOsClient
    private lateinit var contractBuilder: Contracts.Contract.Builder

    override fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        definitionService = mockk()
        osClient = mockk()
        contractBuilder = Contracts.Contract.newBuilder()
    }

    private fun generateHash() = Random.Default.nextBytes(16)
    private fun getContractWrapper(contractClass: KClass<*> = TestContract::class) = ContractWrapper(contractClass.java, encryptionKeyRef, definitionService, osClient, contractBuilder)
    private fun DefinitionService.register(contractClass: KClass<*>, definition: Commons.DefinitionSpec? = null) = every { loadClass(definition ?: any()) } returns contractClass.java
    private fun addRecord(name: String, value: Message) = generateHash().let { hashBytes ->
        val record = Contracts.Record.newBuilder()
            .apply {
                dataLocationBuilder
                    .setClassname(value.javaClass.name)
                    .refBuilder
                    .setHash(hashBytes.base64EncodeString())
            }
            .setName(name)
            .build()

        every { osClient.getRecord(record.dataLocation.classname, hashBytes, encryptionKeyRef) } returns Futures.immediateFuture(value)
        contractBuilder.addInputs(record)

        record
    }
    private fun CachedOsClient.register(input: Contracts.ProposedRecord, message: Message) = every { getRecord(input.classname, input.hash.base64Decode(), encryptionKeyRef) } returns Futures.immediateFuture(message)
    private fun addConsideration(functionName: String, inputs: List<Pair<String, Message>>, result: Contracts.ExecutionResult = Contracts.ExecutionResult.getDefaultInstance()) {
        contractBuilder
            .addConsiderations(Contracts.ConsiderationProto.newBuilder()
                .setConsiderationName(functionName)
                .addAllInputs(inputs.map { (inputName, inputValue) ->
                    val inputHash = generateHash().base64EncodeString()
                    Contracts.ProposedRecord.newBuilder()
                        .setName(inputName)
                        .setClassname(inputValue::class.java.name)
                        .setHash(inputHash)
                        .build()
                        .also {
                            osClient.register(it, inputValue)
                        }

                }).setResult(result)
            )
    }

    init {
        "ContractWrapper construction" should {
            "throw ContractDefinitionException if the class loaded doesn't extend the specified class" {
                definitionService.register(BadTestContract::class)

                val exception = shouldThrow<ContractDefinitionException> {
                    getContractWrapper()
                }
                exception.message shouldContain "must implement ${TestContract::class.java.name}"
            }
            "throw ContractDefinitionException if the class loaded doesn't extend P8eContract" {
                definitionService.register(DefinitelyNotAContract::class)

                val exception = shouldThrow<ContractDefinitionException> {
                    getContractWrapper(DefinitelyNotAContract::class)
                }
                exception.message shouldContain "must extend ${P8eContract::class.java.name}"
            }
            "throw ContractDefinitionException if the class has more than one constructor" {
                definitionService.register(DoubleConstructorContract::class)

                val exception = shouldThrow<ContractDefinitionException> {
                    getContractWrapper(DoubleConstructorContract::class)
                }
                exception.message shouldContain "Class ${DoubleConstructorContract::class.java.name} must have only one constructor"
            }
            "throw ContractDefinitionException if all constructor parameters don't have a Record annotation" {
                definitionService.register(ContractWithUnAnnotatedConstructorArg::class)
                addRecord("someRecord", testProto("someRecordValue"))

                val exception = shouldThrow<ContractDefinitionException> {
                    getContractWrapper(ContractWithUnAnnotatedConstructorArg::class)
                }
                exception.message shouldContain "All constructor arguments for ${ContractWithUnAnnotatedConstructorArg::class.java.name} must have an @Record annotation"
            }
            "throw IllegalArgumentException if not all required records are present" {
                definitionService.register(ContractWithOneRecord::class)

                val exception = shouldThrow<IllegalArgumentException> {
                    getContractWrapper(ContractWithOneRecord::class)
                }
                exception.message shouldContain "wrong number of arguments"
            }
            "throw IllegalArgumentException if provided record type is a mismatch with constructor param type" {
                definitionService.register(ContractWithOneRecord::class)
                addRecord("someRecord", testProto2("someRecordValueWrongType"))

                val exception = shouldThrow<IllegalArgumentException> {
                    getContractWrapper(ContractWithOneRecord::class)
                }

                exception.message shouldContain "Error constructing contract class ${ContractWithOneRecord::class.java.name}\n\tparameter types were (io.provenance.scope.contract.proto.TestContractProtos\$TestProto2)\n\texpected (io.provenance.scope.contract.proto.TestContractProtos\$TestProto)"
            }
            "produce a list of functions that need to be executed based on considerations" {
                definitionService.register(SimpleTestContract::class)

                val inputValue = testProto("testRecordInputValue")
                addConsideration("testRecordOneInputFn", listOf("testRecordInput" to inputValue))

                val wrapper = getContractWrapper(SimpleTestContract::class)

                wrapper.functions.count() shouldBe 1
                val function = wrapper.functions.first()
                function.fact.name shouldBe "testRecordOneInput"
                function.method.name shouldBe "testRecordOneInputFn"
                function.method.returnType shouldBe TestContractProtos.TestProto::class.java
                val result = function.invoke()
                result.second shouldNotBe null
                result.second!!.javaClass shouldBe TestContractProtos.TestProto::class.java
                (result.second!! as TestContractProtos.TestProto).value shouldBe inputValue.value + "-modified"
            }
            "omit functions that already have a non-default result" {
                definitionService.register(SimpleTestContract::class)

                val inputValue = testProto("testRecordInputValue")
                addConsideration("testRecordOneInputFn", listOf("testRecordInput" to inputValue), Contracts.ExecutionResult.newBuilder()
                    .setResult(Contracts.ExecutionResult.Result.PASS)
                    .setOutput(Contracts.ProposedRecord.newBuilder()
                        .setName("testRecordOneInput")
                        .setClassname(TestContractProtos.TestProto::class.java.name)
                        .setHash(generateHash().base64EncodeString())
                    )
                    .build()
                )

                val wrapper = getContractWrapper(SimpleTestContract::class)

                wrapper.functions.count() shouldBe 0
            }
        }
    }
}
