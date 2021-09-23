import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
    id("com.google.protobuf")
}

dependencies {
    implementation(project(":util"))
    protobuf(project(":proto"))

    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)
    implementation("org.bouncycastle", "bcprov-jdk15on", Version.bouncy_castle)

    implementation("com.google.guava:guava:${Version.guava}")

    // TODO how to log in a library? What logging framework should this be?
    compileOnly("org.slf4j", "log4j-over-slf4j", "1.7.30")

    implementation("com.fasterxml.jackson.core", "jackson-annotations", Version.jackson_version)
    implementation("com.fasterxml.jackson.core", "jackson-core", Version.jackson_version)
    implementation("com.fasterxml.jackson.core", "jackson-databind", Version.jackson_version)
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", Version.jackson_version)
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", Version.jackson_version)
    implementation("com.hubspot.jackson", "jackson-datatype-protobuf", "0.9.9-jackson2.9-proto3")

    implementation("com.fortanix", "sdkms-client", Version.fortanixKms)
}

sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
        }
    }
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:${Version.protobuf}"
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
