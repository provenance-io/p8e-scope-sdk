import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import de.undercouch.gradle.tasks.download.Download
import java.io.File

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

sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
            srcDir("build/generated/source/proto/main/grpc")
        }
    }
}

dependencies {
    compile("com.google.protobuf", "protobuf-java", Version.protobuf)
    compile("com.google.protobuf", "protobuf-java-util", Version.protobuf)

    // GRPC
    implementation("io.grpc", "grpc-protobuf", Version.grpc_version)
    implementation("io.grpc", "grpc-stub", Version.grpc_version)

    // Javax annotation
    implementation("javax.annotation", "javax.annotation-api", Version.javax_annotation_version)

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
//    implementation("com.google.guava:guava:30.1-jre")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    api("org.apache.commons:commons-math3:3.6.1")
}

val downloadOSProtos = tasks.create<Download>("downloadOSProtos") {
    src("https://github.com/provenance-io/object-store/releases/download/v${Version.os_proto_version}/protos-${Version.os_proto_version}.zip")
    dest(File(buildDir, "protos-${Version.os_proto_version}.zip"))
    onlyIfModified(true)
}

val downloadAndUnzipOSProtos = tasks.create<Copy>("downloadAndUnzipOSProtos") {
    dependsOn(downloadOSProtos)
    from(zipTree(downloadOSProtos.dest)) {
        eachFile {
            relativePath = RelativePath(true, "proto", *relativePath.segments.drop(1).toTypedArray())
        }
        includeEmptyDirs = false
    }
    into("src/main")
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