dependencies {
    // re-exported deps
    api(project(":os-client"))
    api(project(":contract-base"))
    api(project(":contract-proto"))
    api(project(":encryption"))
    api(project(":engine"))
    implementation(project(":util"))
    implementation(project(":engine"))
    api("io.provenance.protobuf", "pb-proto-java", Version.provenanceProtos)

    compileOnly("org.slf4j", "log4j-over-slf4j", "1.7.30")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)

    testImplementation(project(":contract-proto", "testArtifacts"))
}
