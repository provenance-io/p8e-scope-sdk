package io.provenance.p8e.testframework

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.google.protobuf.TextFormat.parse
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.provenance.scope.contract.proto.*
import io.provenance.scope.sdk.Session
import io.provenance.scope.contract.proto.Commons.DefinitionSpec.Type.PROPOSED
import io.provenance.scope.encryption.model.DirectKeyRef
import io.provenance.scope.sdk.Affiliate
import io.provenance.scope.sdk.Client
import io.provenance.scope.sdk.ClientConfig
import io.provenance.scope.util.toJavaPrivateKey
import java.net.URI
import java.security.KeyPair
import io.provenance.scope.util.toJavaPublicKey
//import org.mockito.Mockito
import java.util.*


val localKeys = listOf(
    "0A41046C57E9E25101D5E553AE003E2F79025E389B51495607C796B4E95C0A94001FBC24D84CD0780819612529B803E8AD0A397F474C965D957D33DD64E642B756FBC4" to "0A2071E487C3FB00642F1863D57749F32D94F13892FA68D02049F7EA9E8FC58D6E63",
    "0A4104D630032378D56229DD20D08DBCC6D31F44A07D98175966F5D32CD2189FD748831FCB49266124362E56CC1FAF2AA0D3F362BF84CACBC1C0C74945041EB7327D54" to "0A2077170DEDCB6CFEDCE5A19BC8F0CD408A254F1E48A7350FC2E9019F50AE52524F",
    "0A4104CD5F4ACFFE72D323CCCB2D784847089BBD80EC6D4F68608773E55B3FEADC812E4E2D7C4C647C8C30352141D2926130D10DFC28ACA5CA8A33B7BD7A09C77072CE" to "0A203CE1967EF504559302CB027A52CB36E5BF6EDC2D8CAFEFF86CA2AAF2817C929F",
    "0A41045E4B322ED16CD22465433B0427A4366B9695D7E15DD798526F703035848ACC8D2D002C1F25190454C9B61AB7B243E31E83BA2B48B8A4441F922A08AC3D0A3268" to "0A210082B2714718EE8CEBEBC9AE1106175E0905DF0018553F22EF90374D197B278D0C",
    "0A4104A37653602DA20D27936AF541084869B2F751953CB0F0D25D320788EDA54FB4BC9FB96A281BFFD97E64B749D78C85871A8E14AFD48048537E45E16F3D2FDDB44B" to "0A203CEE9F786716409DF70336E8F38D606A53AE24877AD56ED72D3C10E1D0BD3DE0",
).map { (public, private) -> KeyPair(public.toJavaPublicKey(), private.toJavaPrivateKey()) }

class UtilsTest : WordSpec({


    "SessionBuilder.Builder tests" should {
        //Setting up single empty record test
        val encryptionKeyRef = DirectKeyRef(localKeys[0].public, localKeys[0].private)
        val signingKeyRef = DirectKeyRef(localKeys[1].public, localKeys[1].private)
        val partyType = Specifications.PartyType.OWNER
        val affiliate = Affiliate(signingKeyRef, encryptionKeyRef, partyType)
        val jarCacheSizeInBytes = 20000L
        val osGrpcDeadlineMs = 20000L
        val specCacheSizeInBytes = 20000L
        val recordCacheSizeInBytes = 20000L
        val osGrpcUri = URI.create("https://localhost:5000")

        val clientConfig = ClientConfig(jarCacheSizeInBytes, specCacheSizeInBytes, recordCacheSizeInBytes, osGrpcUri, osGrpcDeadlineMs )
        val osClient = Client(clientConfig, affiliate)
        val defSpec = Commons.DefinitionSpec.newBuilder()
            .setTypeValue(PROPOSED.ordinal)
            .setResourceLocation(Commons.Location.newBuilder().setClassname("io.provenance.scope.contract.proto.Contracts\$Record"))
            .setName("record1")
        val conditionSpec = Specifications.ConditionSpec.newBuilder()
            .addInputSpecs(defSpec)
            .setFuncName("record1")
            .build()
        val proposedSession = io.provenance.metadata.v1.Session.newBuilder()
            .setSessionId(ByteString.copyFromUtf8("1234567890"))
            .build()
        val participants = HashMap<Specifications.PartyType, PublicKeys.PublicKey>()
        participants[Specifications.PartyType.OWNER] = PublicKeys.PublicKey.newBuilder().build()
        val spec = Specifications.ContractSpec.newBuilder()
            .addConditionSpecs(conditionSpec)
            .addFunctionSpecs(Specifications.FunctionSpec.getDefaultInstance())
            .build()
        val provenanceReference = Commons.ProvenanceReference.newBuilder().build()

        val builder = Session.Builder()
            .setProposedSession(proposedSession)
            .setContractSpec(spec)
            .setProvenanceReference(provenanceReference)
            .setClient(osClient)

        builder.addProposedRecord("record1", Contracts.Record.getDefaultInstance())

        val session = builder.build()

        "Package Contract" {
            val envelope = session.packageContract()
            val classString = envelope.javaClass
            classString shouldBe Envelopes.Envelope.getDefaultInstance().javaClass

            val expectedJson = "ref {\n" +
                    "  hash: \"XvbWuMiSKI7XD3gri1ZQopT9yvv/6oMJD/OxxIUjc1FwrhzWbfPJ+Lr5hhcmI5Qq7gq0X0YpMW+S83B7RLgJQQ==\"\n" +
                    "}\n" +
                    "contract {\n" +
                    "  invoker {\n" +
                    "    signing_public_key {\n" +
                    "      public_key_bytes: \"\\004\\3260\\003#x\\325b)\\335 \\320\\215\\274\\306\\323\\037D\\240}\\230\\027Yf\\365\\323,\\322\\030\\237\\327H\\203\\037\\313I&a\$6.V\\314\\037\\257*\\240\\323\\363b\\277\\204\\312\\313\\301\\300\\307IE\\004\\036\\2672}T\"\n" +
                    "    }\n" +
                    "    encryption_public_key {\n" +
                    "      public_key_bytes: \"\\004lW\\351\\342Q\\001\\325\\345S\\256\\000>/y\\002^8\\233QIV\\a\\307\\226\\264\\351\\\\\\n\\224\\000\\037\\274\$\\330L\\320x\\b\\031a%)\\270\\003\\350\\255\\n9\\177GL\\226]\\225}3\\335d\\346B\\267V\\373\\304\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "  inputs {\n" +
                    "    name: \"record1\"\n" +
                    "    data_location {\n" +
                    "      ref {\n" +
                    "      }\n" +
                    "      classname: \"record1\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "  times_executed: 1\n" +
                    "}\n" +
                    "execution_uuid {\n" +
                    "value: \"1234567890\"\n" +
                    "}\n"

            val expectedObj = parse(expectedJson, Envelopes.Envelope.getDefaultInstance().javaClass)

            expectedObj.toString()
            envelope shouldBe expectedObj

            //Setting up single empty record test
//            val encryptionKeyRef = DirectKeyRef(localKeys[0].public, localKeys[0].private)
//            val signingKeyRef = DirectKeyRef(localKeys[1].public, localKeys[1].private)
//            val partyType = Specifications.PartyType.OWNER
//            val affiliate = Affiliate(signingKeyRef, encryptionKeyRef, partyType)
//            val jarCacheSizeInBytes = 20000L
//            val osGrpcDeadlineMs = 20000L
//            val specCacheSizeInBytes = 20000L
//            val recordCacheSizeInBytes = 20000L
//            val osGrpcUri = URI.create("https://localhost:5000")
//
//            val clientConfig = ClientConfig(jarCacheSizeInBytes, specCacheSizeInBytes, recordCacheSizeInBytes, osGrpcUri, osGrpcDeadlineMs )
//            val osClient = Client(clientConfig, affiliate)
//            val defSpec = Commons.DefinitionSpec.newBuilder()
//                .setType(PROPOSED)
//                .setResourceLocation(Commons.Location.newBuilder().setClassname("io.provenance.scope.contract.proto.Contracts\$Record"))
//                .setName("record2")
//            val conditionSpec = Specifications.ConditionSpec.newBuilder()
//                .addInputSpecs(defSpec)
//                .setFuncName("record2")
//                .build()
//            val proposedSession = io.provenance.metadata.v1.Session.newBuilder()
//                .setSessionId(ByteString.copyFromUtf8("1234567890"))
//                .build()
//            val participants = HashMap<Specifications.PartyType, PublicKeys.PublicKey>()
//            participants[Specifications.PartyType.OWNER] = PublicKeys.PublicKey.newBuilder().build()
//            val spec = Specifications.ContractSpec.newBuilder()
//                .addConditionSpecs(conditionSpec)
//                .addFunctionSpecs(Specifications.FunctionSpec.newBuilder().setFuncName("record2").addInputSpecs(defSpec).setInvokerParty(Specifications.PartyType.OWNER))
//                .addInputSpecs(defSpec)
//                .build()
//            val provenanceReference = Commons.ProvenanceReference.newBuilder().build()
//
//            val builder = Session.Builder()
//                .setProposedSession(proposedSession)
//                .setContractSpec(spec)
//                .setProvenanceReference(provenanceReference)
//                .setClient(osClient)
//
//            val dataLocation = Commons.Location.newBuilder().setClassname("record2").setRef(provenanceReference).build()
//            val record = Contracts.Record.newBuilder().setDataLocation(dataLocation).setName("record2").build()
//            builder.addProposedRecord("record2", record)
//            builder.proposedRecords.size shouldBe 1
//
//            val session = builder.build()
//            val envelopePopulatedRecord = session.packageContract()
//            envelopePopulatedRecord.toString()
        }
    }
})