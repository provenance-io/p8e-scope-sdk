import io.provenance.p8e.plugin.P8eLocationExtension
import io.provenance.p8e.plugin.P8ePartyExtension

buildscript {
    repositories {
        mavenCentral()
        jcenter()
        maven { url = uri("https://javadoc.jitpack.io") }
    }
}

plugins {
    id("io.provenance.p8e.p8e-publish") version "0.5.2-p8e-scope-sdk"
}

p8e {
    // Package locations that the ContractHash and ProtoHash source files will be written to.
    language = "kt" // defaults to "java"
    contractHashPackage = "io.provenance.scope.examples.contract"
    protoHashPackage = "io.provenance.scope.examples.contract"

    locations = mapOf(
        "local" to P8eLocationExtension().also { location ->
            location.osUrl = System.getenv("OS_GRPC_URL")
            location.provenanceUrl = System.getenv("PROVENANCE_GRPC_URL")
            location.encryptionPrivateKey = System.getenv("ENCRYPTION_PRIVATE_KEY")
            location.signingPrivateKey = System.getenv("SIGNING_PRIVATE_KEY")
            location.chainId = System.getenv("CHAIN_ID")
            location.txBatchSize = "5"

            location.audience = mapOf(
                "local1" to P8ePartyExtension().also {
                    it.publicKey =
                        "0A41046C57E9E25101D5E553AE003E2F79025E389B51495607C796B4E95C0A94001FBC24D84CD0780819612529B803E8AD0A397F474C965D957D33DD64E642B756FBC4"
                },
                "local2" to P8ePartyExtension().also {
                    it.publicKey =
                        "0A4104D630032378D56229DD20D08DBCC6D31F44A07D98175966F5D32CD2189FD748831FCB49266124362E56CC1FAF2AA0D3F362BF84CACBC1C0C74945041EB7327D54"
                },
                "local3" to P8ePartyExtension().also {
                    it.publicKey =
                        "0A4104CD5F4ACFFE72D323CCCB2D784847089BBD80EC6D4F68608773E55B3FEADC812E4E2D7C4C647C8C30352141D2926130D10DFC28ACA5CA8A33B7BD7A09C77072CE"
                },
                "local4" to P8ePartyExtension().also {
                    it.publicKey =
                        "0A41045E4B322ED16CD22465433B0427A4366B9695D7E15DD798526F703035848ACC8D2D002C1F25190454C9B61AB7B243E31E83BA2B48B8A4441F922A08AC3D0A3268"
                },
                "local5" to P8ePartyExtension().also {
                    it.publicKey =
                        "0A4104A37653602DA20D27936AF541084869B2F751953CB0F0D25D320788EDA54FB4BC9FB96A281BFFD97E64B749D78C85871A8E14AFD48048537E45E16F3D2FDDB44B"
                }
            )
        }
    )
}
