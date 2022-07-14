dependencies {
    implementation(project(":contract-proto"))

    // https://mvnrepository.com/artifact/io.provenance/proto-kotlin
    implementation("io.provenance", "proto-kotlin", Version.provenanceProtos)

    implementation("org.bouncycastle", "bcprov-jdk15on", Version.bouncy_castle)
    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)

    implementation("org.bouncycastle", "bcprov-jdk15on", Version.bouncy_castle)
}
