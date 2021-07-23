dependencies {
    api(project(":contract-proto"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
