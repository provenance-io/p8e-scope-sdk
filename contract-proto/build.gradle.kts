import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
    id("com.google.protobuf")
}

sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
        }
    }
    test {
        java {
            srcDir("build/generated/source/proto/test/java")
        }
        proto {
            srcDir("src/main/proto")
        }
    }
}

dependencies {
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
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

val testConfig = configurations.create("testArtifacts") {
    extendsFrom(configurations["testCompile"])
}

tasks.register("testJar", Jar::class.java) {
    dependsOn("testClasses")
    classifier += "test"
    from(sourceSets["test"].output)
}

artifacts {
    add("testArtifacts", tasks.named<Jar>("testJar") )
}