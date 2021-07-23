dependencies {
    compile(project(":os-proto"))
    compile(project(":encryption"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)
    implementation("io.grpc", "grpc-stub", Version.grpc_version)

    // implementation("com.fasterxml.jackson.core", "jackson-core", Version.jackson_version)
    implementation("com.fasterxml.jackson.core", "jackson-databind", Version.jackson_version)
    // implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", Version.jackson_version)
    // implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", Version.jackson_version)
    // implementation("com.fasterxml.jackson.core", "jackson-annotations", Version.jackson_version)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
