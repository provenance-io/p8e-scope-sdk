dependencies {
    // re-exported deps
    api(project(":os-client"))
    api(project(":contract-base"))
    api(project(":contract-proto"))
    api("io.provenance.protobuf", "pb-proto-java", Version.provenanceProtos)

    implementation(project(":encryption"))
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
}
