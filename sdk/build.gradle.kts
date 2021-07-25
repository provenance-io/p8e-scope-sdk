dependencies {
    api(project(":os-client"))
    api(project(":contract-base"))
    api(project(":contract-proto"))

    implementation(project(":encryption"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
}
