import com.google.protobuf.gradle.*

buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }

    dependencies {
        classpath("com.google.protobuf:protobuf-gradle-plugin:0.8.19")
    }
}

plugins {
    id("com.google.protobuf") version "0.8.19"
    id("maven-publish")
    id("kotlin")
}

sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
            srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    implementation("io.provenance.scope:contract-base:1.0-SNAPSHOT")

    // implementation("io.provenance.p8e:p8e-contract-base:1.0-SNAPSHOT")

    api("com.google.protobuf:protobuf-java:3.24.4")
    api("com.google.protobuf:protobuf-java-util:3.24.4")
}

protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:3.24.4"
    }
    plugins {
        // Optional: an artifact spec for a protoc plugin, with "grpc" as
        // the identifier, which can be referred to in the "plugins"
        // container of the "generateProtoTasks" closure.
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.58.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                id("python") {}
            }
        }
        ofSourceSet("main").forEach {
            it.plugins {
                // Apply the "grpc" plugin whose spec is defined above, without
                // options.  Note the braces cannot be omitted, otherwise the
                // plugin will not be added. This is because of the implicit way
                // NamedDomainObjectContainer binds the methods.
                id("grpc") {}
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("proto") {
            artifact(tasks.jar)
        }
    }
}
