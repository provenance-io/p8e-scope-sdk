import io.provenance.p8e.plugin.P8eLocationExtension
import io.provenance.p8e.plugin.P8ePartyExtension

buildscript {
    repositories {
        mavenLocal()
        jcenter()
        mavenCentral()
        maven { url = uri("https://javadoc.jitpack.io") }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10")
        classpath("com.bmuschko:gradle-nexus-plugin:2.3.1")
    }
}

plugins {
    id("io.provenance.p8e.p8e-publish") version "0.8.0-rc1"
}

allprojects {
    group = "io.provenance.p8e.p8e-integration-tests.sdk"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        jcenter()
        mavenCentral()
        maven { url = uri("https://javadoc.jitpack.io") }
    }
}

// This block specifies the configuration needed to connect to a p8e instance as well as the audience list
// for all of the objects that will be created.
p8e {
    // Specifies the subproject names for the project containing P8eContract subclasses, and the associated protobuf messages
    // that make up those contracts.
    contractProject = "contracts" // defaults to "contract"
    protoProject = "protos" // defaults to "proto"

    // Package locations that the ContractHash and ProtoHash source files will be written to.
    language = "kt" // defaults to "java"
    contractHashPackage = "io.p8e.contracts.testframework"
    protoHashPackage = "io.p8e.proto.testframework"

    // specifies all of the p8e locations that this plugin will bootstrap to.
    locations = mapOf(
        "local" to P8eLocationExtension().apply {
            osUrl = System.getenv("OS_GRPC_URL")
            provenanceUrl = System.getenv("PROVENANCE_GRPC_URL")
            encryptionPrivateKey = System.getenv("ENCRYPTION_PRIVATE_KEY")
            signingPrivateKey = System.getenv("SIGNING_PRIVATE_KEY")
            chainId = System.getenv("CHAIN_ID")

            audience = mapOf(
                "local1" to P8ePartyExtension().apply {
                    publicKey =
                        "0A41046C57E9E25101D5E553AE003E2F79025E389B51495607C796B4E95C0A94001FBC24D84CD0780819612529B803E8AD0A397F474C965D957D33DD64E642B756FBC4"
                },
                "local2" to P8ePartyExtension().apply {
                    publicKey =
                        "0A4104D630032378D56229DD20D08DBCC6D31F44A07D98175966F5D32CD2189FD748831FCB49266124362E56CC1FAF2AA0D3F362BF84CACBC1C0C74945041EB7327D54"
                },
                "local3" to P8ePartyExtension().apply {
                    publicKey =
                        "0A4104CD5F4ACFFE72D323CCCB2D784847089BBD80EC6D4F68608773E55B3FEADC812E4E2D7C4C647C8C30352141D2926130D10DFC28ACA5CA8A33B7BD7A09C77072CE"
                },
                "local4" to P8ePartyExtension().apply {
                    publicKey =
                        "0A41045E4B322ED16CD22465433B0427A4366B9695D7E15DD798526F703035848ACC8D2D002C1F25190454C9B61AB7B243E31E83BA2B48B8A4441F922A08AC3D0A3268"
                },
                "local5" to P8ePartyExtension().apply {
                    publicKey =
                        "0A4104A37653602DA20D27936AF541084869B2F751953CB0F0D25D320788EDA54FB4BC9FB96A281BFFD97E64B749D78C85871A8E14AFD48048537E45E16F3D2FDDB44B"
                },
                "test1" to P8ePartyExtension().apply {
                    publicKey =
                        "0A4104B3A39EDA72D51F586D72C3F6A8277FF3D3190C17270D7C089AFEB93E8DE5168617A8F925FCD12A08F11ADE4FBB270A74D61B218B675137E2165B326BE8C74AE0"
                },
                "test2" to P8ePartyExtension().apply {
                    publicKey =
                        "0A4104191F22F1FF76D7C762CDCB47C5F87EEF4B0242C1472AF4A58F40A78BC45194968F42F52357CCFE64199988A277912EFF276612B19C9E9448D047827884EC449B"
                },
                "test3" to P8ePartyExtension().apply {
                    publicKey =
                        "0A4104325DB9615E55EFF9034DCBAA3641F0EB271BBBA94D39A05C13A2F2CFBE5456D3723777418B96FEBE155D9858EC4F4360E1C5E922FD4891033FB3DDF633FE533E"
                },
                "test4" to P8ePartyExtension().apply {
                    publicKey =
                        "0A410480308258CB9C12575954BBFEDDC57021F773ABA5A63EB0588F15B535B8AB6EE01617290E1604DEDEA497C18FF47EA43430C7123C79FA90EBD4CF46D9DDB3CC62"
                }
            )
        }
    )
}
