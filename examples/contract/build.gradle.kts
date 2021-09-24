plugins {
    kotlin
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":proto"))

    implementation("com.google.protobuf:protobuf-java:3.12.0")

    implementation("io.provenance.scope:contract-base:1.0-SNAPSHOT")
}
