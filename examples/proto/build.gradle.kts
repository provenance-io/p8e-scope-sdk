import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    idea
    kotlin
    java

    id("com.google.protobuf") version "0.8.16"
}

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
            srcDir("build/generated/source/proto/main/grpc")
        }
    }
}

dependencies {
    // at compile time we need access to ProtoHash on the classpath
    compileOnly("io.provenance.scope:contract-base:1.0-SNAPSHOT")

    // Protobuf
    implementation("com.google.protobuf", "protobuf-java", "3.12.0")

    // Grpc
    implementation("io.grpc", "grpc-stub", "1.39.0")
    implementation("io.grpc", "grpc-protobuf", "1.39.0") {
        exclude("com.google.protobuf")
    }

    // Validation
    implementation("javax.annotation", "javax.annotation-api", "1.3.2")
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:3.6.1"
    }
}
