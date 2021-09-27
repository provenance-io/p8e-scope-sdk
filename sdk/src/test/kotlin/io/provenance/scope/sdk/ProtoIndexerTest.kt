package io.provenance.scope.sdk

import com.google.common.util.concurrent.Futures
import com.google.protobuf.Message
import io.mockk.mockk
import io.mockk.every
import io.provenance.metadata.v1.*
import io.provenance.metadata.v1.Session
import io.provenance.scope.contract.proto.Specifications.FunctionSpec
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.contract.proto.TestProtos
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.util.toByteString
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.provenance.scope.contract.proto.Specifications.PartyType.OWNER
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64EncodeString
import io.provenance.scope.objectstore.util.toByteArray
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.ProtoIndexer
import io.provenance.scope.util.MetadataAddress
import java.security.KeyPair
import java.util.UUID

class ProtoIndexerTest: AnnotationSpec(){

    lateinit var mockDefinitionService: DefinitionService
    lateinit var mockOsClient: CachedOsClient
    lateinit var protoIndexer: ProtoIndexer

    val keyPair = ProvenanceKeyGenerator.generateKeyPair()

    fun contractSpecAddr(uuid: UUID = UUID.randomUUID()) = MetadataAddress.forContractSpecification(uuid)
    fun randomHash() = UUID.randomUUID().toByteArray().base64EncodeString()

    fun createContractSpec(): ContractSpec{
        return ContractSpec.newBuilder()
            .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                addAllFunctionSpecs(listOf(FunctionSpec.newBuilder()
                    .setFuncName("person")
                    .build(),
                FunctionSpec.newBuilder()
                    .setFuncName("cat")
                    .build()
                ))
            }
            .build()
    }

    fun createSingleRecordScope(keyPair: KeyPair): ScopeResponse{
        return ScopeResponse.newBuilder()
            // set up the scope response with whatever is needed
            .addSessions(SessionWrapper.newBuilder()
                .setSession(Session.newBuilder()
                    .setSessionId("sessionid".toByteString())
                    .addParties(Party.newBuilder().setAddress(keyPair.public.getAddress(false)))
                )
                .setContractSpecIdInfo(ContractSpecIdInfo.newBuilder()
                    .setContractSpecAddr(contractSpecAddr().toString())
                )
            )
            .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
                .setName("person")
                .setSessionId("sessionid".toByteString())
                .addOutputs(RecordOutput.newBuilder()
                    .setHash(randomHash())
                    .build()
                )
                .build()
            ))
            .build()
    }

    fun createMultiRecordScope(keyPair: KeyPair): ScopeResponse{
        return ScopeResponse.newBuilder()
            // set up the scope response with whatever is needed
            .addSessions(SessionWrapper.newBuilder()
                .setSession(Session.newBuilder()
                    .setSessionId("sessionid".toByteString())
                    .addParties(Party.newBuilder().setAddress(keyPair.public.getAddress(false)))
                )
                .setContractSpecIdInfo(ContractSpecIdInfo.newBuilder()
                    .setContractSpecAddr(contractSpecAddr().toString())
                )
            )
            .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
                .setName("person")
                .setSessionId("sessionid".toByteString())
                .addOutputs(RecordOutput.newBuilder()
                    .setHash(randomHash())
                    .build()
                )
                .build()
            ))
            .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
                .setName("cat")
                .setSessionId("sessionid".toByteString())
                .addOutputs(RecordOutput.newBuilder()
                    .setHash(randomHash())
                    .build()
                )
                .build()
            ))
            .build()
    }

    fun queueOsResponses(vararg messages: Message) {
        every { mockOsClient.getRecord(any(), any(), any()) } returnsMany messages.map { Futures.immediateFuture(it) }
    }

    @BeforeAll
    fun setUp(){
        val affiliate = Affiliate(
            signingKeyRef = DirectKeyRef(keyPair.public, keyPair.private),
            encryptionKeyRef = DirectKeyRef(keyPair.public, keyPair.private),
            partyType = OWNER,
        )
        mockDefinitionService = mockk<DefinitionService>()
        mockOsClient = mockk<CachedOsClient>()
        protoIndexer = ProtoIndexer(mockOsClient, false, affiliate) { _, _ -> mockDefinitionService }
    }

    @Test
    fun oneIndexableOneNot(){
        // set up fake data
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()

        queueOsResponses(
            createContractSpec(),
            testProto,
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        /*
        * scope is essentially an array of records, which can be hydrated to the actual data, like [{ name: "person", value: { name: "cool name", ssn: "123-456-789" } }, { name: "some other record", value: <some other proto> }]
        *
        * the result of indexing, is the scope filtered down to only the data listed as indexable (top-level is a map of records that contained something indexable)
        * so, the result of the above scope example, assuming the only indexable field anywhere was the person.name, would result in a map like:
        * { "person" : { "name" : "cool name" } }
        * note, the distinct lack of non-indexable things like the ssn field, or the other record that presumably didn't contain anything indexable
        *
        * General format is:
        *  {nameOfRecord: {indexableFieldName: indexableFieldValue, repeat for other indexable fields}, repeat for other records}
        *
        * In example of person with an address, result could be
        *
        * { "person" : { "name" : "cool name", "address" : { "city": "Bozeman", "state": "Montana" } } }
        * */

//        throw Exception("Index Fields is $indexFields")
        indexFields.size shouldBe 1
        indexFields.containsKey("person") shouldBe true
        val nameMessage = indexFields.get("person")!!
//        throw Exception("type is ${nameMessage.javaClass.name}")
        nameMessage.shouldBeInstanceOf<Map<String, String>>()
        (nameMessage as Map<String, Any>).size shouldBe 1
        (nameMessage as Map<String, Any>).get("name") shouldBe testProto.name
    }

    @Test
    fun noneIndexable(){
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.NoneIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()

        queueOsResponses(
            createContractSpec(),
            testProto,
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 0
    }

    @Test
    fun allIndexable(){
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.AllIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .build()

        queueOsResponses(
            createContractSpec(),
            testProto,
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 1
        val record = indexFields.get("person")
        record.shouldBeInstanceOf<Map<String, String>>()
        (record as Map<String, Any>).size shouldBe 3
        (record as Map<String, Any>).get("food") shouldBe "Bagel"
    }

    @Test
    fun someIndexableSomeNot(){
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.SomeIndexableSomeNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .setShape("Circle")
            .setMaterial("Metal")
            .build()

        queueOsResponses(
            createContractSpec(),
            testProto,
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 1
        val record = indexFields.get("person")
        record.shouldBeInstanceOf<Map<String, String>>()
        (record as Map<String, Any>).size shouldBe 3
        (record as Map<String, Any>).get("food") shouldBe "Bagel"
    }

    //TODO: Create multi record Scope tests
    @Test
    fun multiRecordOneIndexable(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val personProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()
        val catProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto,
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        //throw Exception("indexFields is: $indexFields")
        indexFields.size shouldBe 2
        val personVal = indexFields.get("person") as Map<String, Any>
        val catVal = indexFields.get("cat") as Map<String, Any>
        personVal.size shouldBe 1
        personVal.get("name") shouldBe "cool name"
        catVal.size shouldBe 1
        catVal.get("name") shouldBe "Luna"
    }

    @Test
    fun multiRecordNoneIndexable(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val personProto = TestProtos.NoneIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .build()
        val catProto = TestProtos.NoneIndexable.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .setFood("Treats")
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto,
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 0
    }

    @Test
    fun multiRecordAllIndexable(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val personProto = TestProtos.AllIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .build()
        val catProto = TestProtos.AllIndexable.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .setFood("Treats")
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        //throw Exception("indexFields is: $indexFields")
        indexFields.size shouldBe 2
        val personVal = indexFields.get("person") as Map<String, Any>
        val catVal = indexFields.get("cat") as Map<String, Any>
        personVal.size shouldBe 3
        personVal.get("name") shouldBe "cool name"
        catVal.size shouldBe 3
        catVal.get("food") shouldBe "Treats"
    }

    @Test
    fun multiRecordSomeIndexableSomeNot(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val personProto = TestProtos.SomeIndexableSomeNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .setShape("Circle")
            .setMaterial("Metal")
            .build()
        val catProto = TestProtos.SomeIndexableSomeNot.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .setFood("Treats")
            .setShape("Loaf")
            .setMaterial("Wood")
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        //throw Exception("indexFields is: $indexFields")
        indexFields.size shouldBe 2
        val personVal = indexFields.get("person") as Map<String, Any>
        val catVal = indexFields.get("cat") as Map<String, Any>
        personVal.size shouldBe 3
        personVal.get("name") shouldBe "cool name"
        catVal.size shouldBe 3
        catVal.get("food") shouldBe "Treats"
    }
//
    //TODO: Create nested message tests
    @Test
    fun nestedOneIndexable(){
        // set up fake data
        val testScope = createSingleRecordScope(keyPair)
        val insideProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()
        val personProto = TestProtos.ParentOneIndexable.newBuilder()
            .setDrink("water")
            .setOs("Windows")
            .setNestedProto(insideProto)
            .build()


        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

//        throw Exception("indexFields is $indexFields")
        indexFields.size shouldBe 1
        val proto = indexFields.get("person") as Map<String, Any>
        proto.size shouldBe 2
        (proto.get("nestedProto") as Map<String, Any>).size shouldBe 1
        (proto.get("nestedProto") as Map<String, Any>).get("name") shouldBe "cool name"
    }

    @Test
    fun nestedNoneIndexable(){
        // set up fake data
        val testScope = createSingleRecordScope(keyPair)
        val insideProto = TestProtos.NoneIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .build()
        val personProto = TestProtos.ParentNoneIndexable.newBuilder()
            .setDrink("water")
            .setOs("Windows")
            .setNestedProto(insideProto)
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 0
    }

    @Test
    fun nestedAllIndexable(){
        // set up fake data
        val testScope = createSingleRecordScope(keyPair)
        val insideProto = TestProtos.AllIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .build()
        val personProto = TestProtos.ParentAllIndexable.newBuilder()
            .setDrink("water")
            .setOs("Windows")
            .setNestedProto(insideProto)
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 1
        val proto = indexFields.get("person") as Map<String, Any>
        proto.size shouldBe 2
        (proto.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (proto.get("nestedProto") as Map<String, Any>).get("food") shouldBe "Bagel"
    }

    @Test
    fun nestedSomeIndexableSomeNot(){
        // set up fake data
        val testScope = createSingleRecordScope(keyPair)
        val insideProto = TestProtos.SomeIndexableSomeNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .setShape("Circle")
            .setMaterial("Metal")
            .build()
        val personProto = TestProtos.ParentSomeIndexable.newBuilder()
            .setDrink("water")
            .setOs("Windows")
            .setNestedProto(insideProto)
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 1
        val proto = indexFields.get("person") as Map<String, Any>
        proto.size shouldBe 2
        (proto.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (proto.get("nestedProto") as Map<String, Any>).get("food") shouldBe "Bagel"
    }

    //TODO: Create multi record Scope with nested messages tests
    @Test
    fun multiRecordNestedOneIndexable(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val innerPersonProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()
        val innerCatProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .build()
        val personProto = TestProtos.ParentOneIndexable.newBuilder()
            .setDrink("soda")
            .setOs("Windows")
            .setNestedProto(innerPersonProto)
            .build()
        val catProto = TestProtos.ParentOneIndexable.newBuilder()
            .setDrink("water")
            .setOs("Linux")
            .setNestedProto(innerCatProto)
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto,
            createContractSpec(),
            innerPersonProto,
            createContractSpec(),
            innerCatProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

//        throw Exception("indexFields is $indexFields")
        indexFields.size shouldBe 2
        val personInfo = indexFields.get("person") as Map<String, Any>
        val catInfo = indexFields.get("cat") as Map<String, Any>
        personInfo.size shouldBe 2
        (personInfo.get("nestedProto") as Map<String, Any>).size shouldBe 1
        (personInfo.get("nestedProto") as Map<String, Any>).get("name") shouldBe "cool name"
        catInfo.size shouldBe 2
        (catInfo.get("nestedProto") as Map<String, Any>).size shouldBe 1
        (catInfo.get("nestedProto") as Map<String, Any>).get("name") shouldBe "Luna"
    }

    @Test
    fun multiRecordNestedestedNoneIndexable(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val innerPersonProto = TestProtos.NoneIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("sandwich")
            .build()
        val innerCatProto = TestProtos.NoneIndexable.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .setFood("treats")
            .build()
        val personProto = TestProtos.ParentNoneIndexable.newBuilder()
            .setDrink("soda")
            .setOs("Windows")
            .setNestedProto(innerPersonProto)
            .build()
        val catProto = TestProtos.ParentNoneIndexable.newBuilder()
            .setDrink("water")
            .setOs("Linux")
            .setNestedProto(innerCatProto)
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto,
            createContractSpec(),
            innerPersonProto,
            createContractSpec(),
            innerCatProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 0
    }

    @Test
    fun multiRecordNestedestedAllIndexable(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val innerPersonProto = TestProtos.AllIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("sandwich")
            .build()
        val innerCatProto = TestProtos.AllIndexable.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .setFood("treats")
            .build()
        val personProto = TestProtos.ParentAllIndexable.newBuilder()
            .setDrink("soda")
            .setOs("Windows")
            .setNestedProto(innerPersonProto)
            .build()
        val catProto = TestProtos.ParentAllIndexable.newBuilder()
            .setDrink("water")
            .setOs("Linux")
            .setNestedProto(innerCatProto)
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto,
            createContractSpec(),
            innerPersonProto,
            createContractSpec(),
            innerCatProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 2
        val personInfo = indexFields.get("person") as Map<String, Any>
        val catInfo = indexFields.get("cat") as Map<String, Any>
        personInfo.size shouldBe 2
        (personInfo.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (personInfo.get("nestedProto") as Map<String, Any>).get("name") shouldBe "cool name"
        catInfo.size shouldBe 2
        (catInfo.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (catInfo.get("nestedProto") as Map<String, Any>).get("name") shouldBe "Luna"
    }

    @Test
    fun multiRecordNestedestedSomeIndexableSomeNot(){
        // set up fake data
        val testScope = createMultiRecordScope(keyPair)
        val innerPersonProto = TestProtos.SomeIndexableSomeNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("sandwich")
            .setShape("circle")
            .setMaterial("metal")
            .build()
        val innerCatProto = TestProtos.SomeIndexableSomeNot.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .setFood("treats")
            .setShape("dodecahedron")
            .setMaterial("wood")
            .build()
        val personProto = TestProtos.ParentSomeIndexable.newBuilder()
            .setDrink("soda")
            .setOs("Windows")
            .setNestedProto(innerPersonProto)
            .build()
        val catProto = TestProtos.ParentSomeIndexable.newBuilder()
            .setDrink("water")
            .setOs("Linux")
            .setNestedProto(innerCatProto)
            .build()

        queueOsResponses(
            createContractSpec(),
            personProto,
            createContractSpec(),
            catProto,
            createContractSpec(),
            innerPersonProto,
            createContractSpec(),
            innerCatProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope)

        indexFields.size shouldBe 2
        val personInfo = indexFields.get("person") as Map<String, Any>
        val catInfo = indexFields.get("cat") as Map<String, Any>
        personInfo.size shouldBe 2
        (personInfo.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (personInfo.get("nestedProto") as Map<String, Any>).get("name") shouldBe "cool name"
        catInfo.size shouldBe 2
        (catInfo.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (catInfo.get("nestedProto") as Map<String, Any>).get("name") shouldBe "Luna"
    }


}
