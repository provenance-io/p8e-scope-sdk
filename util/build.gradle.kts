dependencies {
    implementation(project(":contract-proto"))
    // TODO imo a subproject like util should not pull in something like encryption
    implementation(project(":encryption"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
}
