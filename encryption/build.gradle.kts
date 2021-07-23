import com.google.protobuf.gradle.*

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.google.protobuf:protobuf-gradle-plugin:0.8.7")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.31"
    `java-library`
    id("com.google.protobuf")
}

dependencies {
//    compile project(":p8e-proto-internal")

    compile(project(":os-proto"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Encryption
    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)
    implementation("org.bouncycastle", "bcprov-jdk15on", Version.bouncy_castle)

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)

    // GRPC
    implementation("io.grpc", "grpc-protobuf", Version.grpc_version)
    implementation("io.grpc", "grpc-stub", Version.grpc_version)

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation("com.google.guava:guava:30.1-jre")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    api("org.apache.commons:commons-math3:3.6.1")
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