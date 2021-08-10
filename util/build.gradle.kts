dependencies {
    implementation(project(":contract-proto"))
    implementation(project(":encryption"))

    implementation("org.bouncycastle", "bcprov-jdk15on", Version.bouncy_castle)
    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
}
