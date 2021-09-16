dependencies {
    // internal projects
    implementation(project(":os-client"))
    implementation(project(":encryption"))
    implementation(project(":contract-proto"))
    implementation(project(":contract-base"))
    implementation(project(":util"))

    // Provenance
    api("io.provenance.protobuf", "pb-proto-java", Version.provenanceProtos)

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("io.grpc", "grpc-protobuf", Version.grpc_version)

    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)

    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation("com.google.guava", "guava", Version.guava)

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    api("org.apache.commons:commons-math3:3.6.1")
    implementation("commons-io:commons-io:2.8.0")

    compileOnly("org.slf4j", "log4j-over-slf4j", "1.7.30")

    implementation("io.arrow-kt", "arrow-core", Version.arrow)

    testImplementation(project(":contract-base", "testArtifacts"))
}
