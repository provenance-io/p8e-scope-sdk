package io.provenance.scope.sdk

import io.mockk.mockk
import io.mockk.every
import io.provenance.metadata.v1.*
import io.provenance.metadata.v1.Session
import io.provenance.metadata.v1.p8e.ConsiderationSpec
import io.provenance.metadata.v1.p8e.ContractSpec
import io.provenance.scope.definition.DefinitionService
import io.provenance.scope.objectstore.client.OsClient
import io.provenance.scope.contract.proto.TestProtos
import io.provenance.scope.encryption.ecies.ProvenanceKeyGenerator
import io.provenance.scope.util.getAddress
import io.provenance.scope.util.toByteString

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import java.security.KeyPair

//@IncludeTags("Test")
//class ProtoIndexerTest {
//    lateinit var mockDefinitionService: DefinitionService
//    lateinit var mockOsClient: OsClient
//    lateinit var protoIndexer: ProtoIndexer
//
//    @BeforeAll
//    fun setUp(){
//        mockDefinitionService = mockk<DefinitionService>()
//        mockOsClient = mockk<OsClient>()
//        protoIndexer = ProtoIndexer(mockOsClient, false) { mockOsClient, memoryClassLoader -> mockDefinitionService }
//    }
//
//    @Test
////    @Tag("Test")
//    fun oneIndexableOneNot(){
//        // set up fake data
//        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
//        val testScope = ScopeResponse.newBuilder()
//            // set up the scope response with whatever is needed
//            .addSessions(SessionWrapper.newBuilder()
//                .setSession(Session.newBuilder()
//                    .setSessionId("sessionid".toByteString())
//                    .addParties(Party.newBuilder().setAddress(keyPair.public.getAddress(false)))
//                )
//                .setContractSpecIdInfo(ContractSpecIdInfo.newBuilder()
//                    .setContractSpecAddr("contractspecaddr")
//                )
//            )
//            .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
//                .setSessionId("sessionid".toByteString())
//                .addOutputs(RecordOutput.newBuilder()
//                    .setHash("outputhash")
//                    .build()
//                )
//                .build()
//            ))
//            .build()
//        val testProto = TestProtos.TestProto.newBuilder()
//            .setName("cool name")
//            .setSsn("123-456-7890")
//            .build()
//
//        // set up mockDefinitionService call(s) so that the proper data is returned
//        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
//            ContractSpec.newBuilder()
////                .apply {
////                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
////                    considerationSpecsList.add(0, ConsiderationSpec.newBuilder()
////                        .outputSpecBuilder.specb.resource
////                        .build()
////                    )
////                }
//                .build(),
//            testProto
//        )
//
//        // perform indexing
//        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))
//
//        assert(indexFields.size == 1)
//        assert(indexFields.containsKey("name"))
//        assert(indexFields.get("name") == testProto.name)
//    }
//
////    @Test
////    fun noneIndexable(){
////
////    }
////
////    @Test
////    fun allIndexable(){
////
////    }
////
////    @Test
////    fun someIndexableSomeNot(){
////
////    }
//}

//class ProtoIndexerTests: WordSpec({
//
//    "Various Indexable Protos:" should {
//
//        "One Indexable One Not Should Work"{
//            //        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
//            val testScope = ScopeResponse.newBuilder()
//                // set up the scope response with whatever is needed
//                .addSessions(SessionWrapper.newBuilder()
//                    .setSession(Session.newBuilder()
//                        .setSessionId("sessionid".toByteString())
//                        .addParties(Party.newBuilder().setAddress(keyPair.public.getAddress(false)))
//                    )
//                    .setContractSpecIdInfo(ContractSpecIdInfo.newBuilder()
//                        .setContractSpecAddr("contractspecaddr")
//                    )
//                )
//                .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
//                    .setSessionId("sessionid".toByteString())
//                    .addOutputs(RecordOutput.newBuilder()
//                        .setHash("outputhash")
//                        .build()
//                    )
//                    .build()
//                ))
//                .build()
//            val testProto = TestProtos.TestProto.newBuilder()
//                .setName("cool name")
//                .setSsn("123-456-7890")
//                .build()
//
//            // set up mockDefinitionService call(s) so that the proper data is returned
//            every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
//                ContractSpec.newBuilder()
//    //                .apply {
//    //                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
//    //                    considerationSpecsList.add(0, ConsiderationSpec.newBuilder()
//    //                        .outputSpecBuilder.specb.resource
//    //                        .build()
//    //                    )
//    //                }
//                    .build(),
//                testProto
//            )
//
//            // perform indexing
//            val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))
//
//            assert(indexFields.size == 1)
//            assert(indexFields.containsKey("name"))
//            assert(indexFields.get("name") == testProto.name)
//        }
//    }
//
//})

class ProtoIndexerTests: AnnotationSpec(){

    lateinit var mockDefinitionService: DefinitionService
    lateinit var mockOsClient: OsClient
    lateinit var protoIndexer: ProtoIndexer

    fun createSingleRecordScope(keyPair: KeyPair): ScopeResponse{
        return ScopeResponse.newBuilder()
            // set up the scope response with whatever is needed
            .addSessions(SessionWrapper.newBuilder()
                .setSession(Session.newBuilder()
                    .setSessionId("sessionid".toByteString())
                    .addParties(Party.newBuilder().setAddress(keyPair.public.getAddress(false)))
                )
                .setContractSpecIdInfo(ContractSpecIdInfo.newBuilder()
                    .setContractSpecAddr("contractspecaddr")
                )
            )
            .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
                .setName("person")
                .setSessionId("sessionid".toByteString())
                .addOutputs(RecordOutput.newBuilder()
                    .setHash("outputhash")
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
                    .setContractSpecAddr("contractspecaddr")
                )
            )
            .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
                .setName("person")
                .setSessionId("sessionid".toByteString())
                .addOutputs(RecordOutput.newBuilder()
                    .setHash("outputhash")
                    .build()
                )
                .build()
            ))
            .addRecords(RecordWrapper.newBuilder().setRecord(Record.newBuilder()
                .setName("cat")
                .setSessionId("sessionid".toByteString())
                .addOutputs(RecordOutput.newBuilder()
                    .setHash("otherhash")
                    .build()
                )
                .build()
            ))
            .build()
    }

    @BeforeAll
    fun setUp(){
        mockDefinitionService = mockk<DefinitionService>()
        mockOsClient = mockk<OsClient>()
        protoIndexer = ProtoIndexer(mockOsClient, false) { mockOsClient, memoryClassLoader -> mockDefinitionService }
    }

    @Test
    fun oneIndexableOneNot(){
        // set up fake data
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                                        .build()
                                    )
                                }
                .build(),
            testProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

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
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.NoneIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            testProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

        indexFields.size shouldBe 0
    }

    @Test
    fun allIndexable(){
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.AllIndexable.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .build()

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            testProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

        indexFields.size shouldBe 1
        val record = indexFields.get("person")
        record.shouldBeInstanceOf<Map<String, String>>()
        (record as Map<String, Any>).size shouldBe 3
        (record as Map<String, Any>).get("food") shouldBe "Bagel"
    }

    @Test
    fun someIndexableSomeNot(){
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
        val testScope = createSingleRecordScope(keyPair)
        val testProto = TestProtos.SomeIndexableSomeNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .setFood("Bagel")
            .setShape("Circle")
            .setMaterial("Metal")
            .build()

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            testProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

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
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
        val testScope = createMultiRecordScope(keyPair)
        val personProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("cool name")
            .setSsn("123-456-7890")
            .build()
        val catProto = TestProtos.OneIndexableOneNot.newBuilder()
            .setName("Luna")
            .setSsn("098-765-4321")
            .build()

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            catProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

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
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
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

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            catProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

        indexFields.size shouldBe 0
    }

    @Test
    fun multiRecordAllIndexable(){
        // set up fake data
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
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

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            catProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

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
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
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

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
//                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
//                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            catProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

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
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
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

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
    //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
                    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
                        //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

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
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
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

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
                    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
                        //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
                    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
                        //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

        indexFields.size shouldBe 0
    }

    @Test
    fun nestedAllIndexable(){
        // set up fake data
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
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

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
                    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
                        //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
                    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
                        //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

        indexFields.size shouldBe 1
        val proto = indexFields.get("person") as Map<String, Any>
        proto.size shouldBe 2
        (proto.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (proto.get("nestedProto") as Map<String, Any>).get("food") shouldBe "Bagel"
    }

    @Test
    fun nestedSomeIndexableSomeNot(){
        // set up fake data
        val keyPair = ProvenanceKeyGenerator.generateKeyPair()
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

        // set up mockDefinitionService call(s) so that the proper data is returned
        every { mockDefinitionService.loadProto(any(), any<String>(), any(), any()) } returnsMany listOf(
            ContractSpec.newBuilder()
                .apply {
                    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
                        //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            personProto,
            ContractSpec.newBuilder()
                .apply {
                    //                                    definitionBuilder.resourceLocationBuilder.refBuilder.setHash("contractspechash")
                    addConsiderationSpecs(ConsiderationSpec.newBuilder()
                        //                                        .outputSpecBuilder.specb.resource
                        .build()
                    )
                }
                .build(),
            insideProto
        )

        every { mockDefinitionService.addJar(any(), any(), any()) } returns Unit

        every { mockDefinitionService.forThread(any<() -> Any>()) } answers { firstArg<() -> Any>()() }

        // perform indexing
        val indexFields = protoIndexer.indexFields(testScope, listOf(keyPair), mapOf("contractspecaddr" to "contractspechash"))

        indexFields.size shouldBe 1
        val proto = indexFields.get("person") as Map<String, Any>
        proto.size shouldBe 2
        (proto.get("nestedProto") as Map<String, Any>).size shouldBe 3
        (proto.get("nestedProto") as Map<String, Any>).get("food") shouldBe "Bagel"
    }

//    //TODO: Create multi record Scope with nested messages tests
//    @Test
//    fun multiRecordNestedOneIndexable(){
//
//    }
//
//    @Test
//    fun multiRecordNestedestedNoneIndexable(){
//
//    }
//
//    @Test
//    fun multiRecordNestedestedAllIndexable(){
//
//    }
//
//    @Test
//    fun multiRecordNestedestedSomeIndexableSomeNot(){
//
//    }


}