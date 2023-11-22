dependencies {
    // re-exported deps
    api(project(":os-client"))
    api(project(":contract-base"))
    implementation(project(":contract-proto"))
    api(project(":encryption"))
    api(project(":engine"))
    implementation(project(":util"))
    implementation(project(":engine"))
    // https://mvnrepository.com/artifact/io.provenance/proto-kotlin
    implementation("io.provenance", "proto-kotlin", Version.provenanceProtos)

    compileOnly("org.slf4j", "log4j-over-slf4j", "1.7.30")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
    implementation("com.fortanix", "sdkms-client", "3.23.1408")
    implementation("io.opentracing", "opentracing-api", Version.openTracing)
    implementation("io.opentracing", "opentracing-util", Version.openTracing)
    implementation("io.grpc", "grpc-stub", Version.grpc_version)

    testImplementation(project(":contract-base", "testArtifacts"))
    testImplementation(project(":contract-proto", "testArtifacts"))
    testImplementation(project(":util"))

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
}
