import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
//import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf

group = "io.provenance.p8e.p8e-integration-tests"
version = (project.property("version") as String?)
    ?.takeUnless { it.isBlank() || it == "unspecified" }
    ?: "1.0-SNAPSHOT"

plugins {
    `jacoco`
    kotlin("jvm") version "1.9.10"
//     id("com.google.protobuf") version "0.8.13"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://javadoc.jitpack.io") }
    maven { url = uri("https://plugins.gradle.org/m2/") }
}

dependencies {
    implementation(kotlin("stdlib", "1.9.10"))

//    implementation("io.provenance.p8e:p8e-sdk:0.7.+")
//    implementation("io.provenance.p8e:p8e-contract-base:1.0-SNAPSHOT")
//    implementation("io.provenance.p8e.p8e-integration-tests:contracts:1.0-SNAPSHOT")
//    implementation("io.provenance.p8e.p8e-integration-tests:protos:1.0-SNAPSHOT")

    implementation("io.provenance.p8e.p8e-integration-tests.sdk:contracts:1.0-SNAPSHOT")
    implementation("io.provenance.p8e.p8e-integration-tests.sdk:protos:1.0-SNAPSHOT")

    //Don't actually know if this will work or not
//    implementation("io.provenance.p8e.p8e-integration-tests:sdkContracts:1.0-SNAPSHOT")

    implementation("com.google.protobuf:protobuf-java:3.24.4")
    implementation("com.google.protobuf:protobuf-java-util:3.24.4")

    // protobuf(files("src/main/protos/"))

    testImplementation("io.kotest:kotest-runner-junit5:4.4.+")

    //Dependency for the logger
    implementation("ch.qos.logback:logback-classic:1.2.3")

    //p8e-scope-sdk
    implementation("io.provenance.scope:sdk:1.0-SNAPSHOT")

    //TransactionService.kt
    implementation("io.grpc:grpc-stub:1.58.0")
    implementation("io.provenance.scope:util:1.0-SNAPSHOT")
    implementation("org.bouncycastle:bcprov-jdk15on:1.68")

    implementation("com.github.komputing.kethereum:crypto:0.83.4")
    implementation("com.github.komputing.kethereum:crypto_impl_bouncycastle:0.83.4")
    implementation("com.github.komputing.kethereum:crypto_api:0.83.4")
    implementation("com.github.komputing.kethereum:model:0.83.4")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

// sourceSets{
//     create("protos"){
//         proto {
//             srcDir("src/main/protos")
//         }
//     }
// }

// protobuf {
//     protoc {
//         // The artifact spec for the Protobuf Compiler
//         artifact = "com.google.protobuf:protoc:3.6.+"
//     }
//     plugins {
//         // Optional: an artifact spec for a protoc plugin, with "grpc" as
//         // the identifier, which can be referred to in the "plugins"
//         // container of the "generateProtoTasks" closure.
//         id("grpc") {
//             artifact = "io.grpc:protoc-gen-grpc-java:1.0.0-pre2"
//         }
//     }
//     generateProtoTasks {
//         // all().each { task ->
//         //     task.builtins {
//         //         python {}
//         //     }
//         // }
//         ofSourceSet("main").forEach {
//             it.plugins {
//                 // Apply the "grpc" plugin whose spec is defined above, without
//                 // options.  Note the braces cannot be omitted, otherwise the
//                 // plugin will not be added. This is because of the implicit way
//                 // NamedDomainObjectContainer binds the methods.
//                 id("grpc")
//             }
//         }
//     }
// }

tasks.withType<Test> {
    useJUnitPlatform()


    testLogging {
        showStandardStreams = true
        events = setOf(PASSED, FAILED, SKIPPED, STANDARD_ERROR)
        exceptionFormat = FULL
    }
}

jacoco {
    toolVersion = "0.8.6"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        csv.required.set(false)
    }
}
