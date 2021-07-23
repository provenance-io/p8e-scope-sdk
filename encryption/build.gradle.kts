import com.google.protobuf.gradle.*

plugins {
    id("com.google.protobuf")
}

dependencies {
    // TODO needed for proto utils UUID and AuditFields
    compile(project(":os-proto"))

    // Encryption
    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)
    implementation("org.bouncycastle", "bcprov-jdk15on", Version.bouncy_castle)

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)

    // GRPC
    implementation("io.grpc", "grpc-protobuf", Version.grpc_version)
    implementation("io.grpc", "grpc-stub", Version.grpc_version)

    implementation("org.slf4j", "log4j-over-slf4j", "1.7.30")

    implementation("com.fasterxml.jackson.core", "jackson-core", Version.jackson_version)
    implementation("com.fasterxml.jackson.core", "jackson-databind", Version.jackson_version)
    implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", Version.jackson_version)
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", Version.jackson_version)
    implementation("com.fasterxml.jackson.core", "jackson-annotations", Version.jackson_version)

    implementation("com.fortanix", "sdkms-client", "3.23.1408")
}

sourceSets {
    main {
        java {
            srcDir("src/main/kotlin")
            srcDir("build/generated/source/proto/main/java")
        }
    }
    test {
        java {
            srcDirs("src/test/kotlin")
        }
    }
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:${Version.protobuf}"
    }
    plugins {
        // Optional: an artifact spec for a protoc plugin, with "grpc" as
        // the identifier, which can be referred to in the "plugins"
        // container of the "generateProtoTasks" closure.
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.0.0-pre2"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without options.
                id("grpc")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
