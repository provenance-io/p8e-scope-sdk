dependencies {
    api(project(":os-client"))
    api(project(":contract-base"))
    api(project(":contract-proto"))

    implementation(project(":encryption"))
    implementation(project(":contract-proto"))

    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
}
