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
    protobuf(project(":proto"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:${Version.protobuf}"
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
