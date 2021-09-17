import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import de.undercouch.gradle.tasks.download.Download
import java.io.File

plugins {
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
    implementation(project(":encryption"))
    implementation(project(":util"))
    implementation(project(":contract-proto"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("io.grpc", "grpc-protobuf", Version.grpc_version)
    implementation("io.grpc", "grpc-stub", Version.grpc_version)

    implementation("javax.annotation", "javax.annotation-api", Version.javax_annotation_version)

    compileOnly("org.slf4j", "log4j-over-slf4j", "1.7.30")
    implementation("io.opentracing", "opentracing-api", "0.33.0")
    implementation("io.opentracing", "opentracing-util", "0.33.0")

    runtimeOnly("io.grpc:grpc-netty-shaded:${Version.grpc_version}")
}

val downloadProtos = tasks.create<Download>("downloadProtos") {
    src("https://github.com/provenance-io/object-store/releases/download/v${Version.os_proto_version}/protos-${Version.os_proto_version}.zip")
    dest(File(buildDir, "protos-${Version.os_proto_version}.zip"))
    onlyIfModified(true)
}

tasks.create<Copy>("downloadAndUnzipProtos") {
    dependsOn(downloadProtos)
    from(zipTree(downloadProtos.dest)) {
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

// tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
//     kotlinOptions.jvmTarget = "1.8"
// }
