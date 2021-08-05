dependencies {
    implementation(project(":contract-proto"))
    implementation(project(":encryption"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
}
