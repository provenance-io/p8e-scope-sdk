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
}

val testConfig = configurations.create("testArtifacts") {
    extendsFrom(configurations["testImplementation"])
}

tasks.register("testJar", Jar::class.java) {
    dependsOn("testClasses")
    archiveClassifier = "test"
    from(sourceSets.test.get().output)
}

artifacts {
    add("testArtifacts", tasks.named<Jar>("testJar") )
}
