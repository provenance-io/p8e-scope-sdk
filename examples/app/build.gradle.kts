import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    idea

    id("io.provenance.scope.examples.kotlin-application-conventions")
}

group = "io.provenance.scope.examples"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://javadoc.jitpack.io") }
}

dependencies {
    implementation(project(":contract"))
    implementation(project(":proto"))


    // https://mvnrepository.com/artifact/io.provenance/proto-kotlin
    implementation("io.provenance", "proto-kotlin", "1.8.0-rc10")
    implementation("io.provenance.scope:sdk:1.0-SNAPSHOT")
    implementation("io.provenance.scope:util:1.0-SNAPSHOT")
    implementation("io.grpc:grpc-protobuf:1.39.0")
    implementation("io.grpc:grpc-stub:1.39.0")
    implementation("ch.qos.logback:logback-classic:1.0.13")

    implementation("com.fortanix", "sdkms-client", "3.23.1408")

    // TODO remove after new signer lib is implemented
    implementation("org.bouncycastle:bcprov-jdk15on:1.69")
    implementation("com.github.komputing.kethereum:crypto:0.83.4")
    implementation("com.github.komputing.kethereum:crypto_api:0.83.4")
    implementation("com.github.komputing.kethereum:crypto_impl_bouncycastle:0.83.4")

    runtimeOnly("io.grpc:grpc-netty-shaded:1.39.0")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClassName = project.properties["mainClass"] as String? ?: "Please provide a main class to run."
}
