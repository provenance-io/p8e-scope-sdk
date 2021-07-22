plugins {
    id("org.jetbrains.kotlin.jvm") version "1.4.31"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    compile(project(":os-proto"))
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("org.bouncycastle", "bcpkix-jdk15on", Version.bouncy_castle)
    implementation("io.grpc", "grpc-stub", Version.grpc_version)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
