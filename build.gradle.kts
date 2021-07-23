buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.google.protobuf", "protobuf-gradle-plugin", Version.protobufPlugin)
    }
}

plugins {
    `java-library`
    idea
    jacoco
}

subprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "java")

    dependencies {
        val implementation by configurations
        implementation("org.slf4j", "log4j-over-slf4j", "1.7.30")
        implementation("com.fasterxml.jackson.core", "jackson-core", Version.jackson_version)
        implementation("com.fasterxml.jackson.core", "jackson-databind", Version.jackson_version)
        implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", Version.jackson_version)
        implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", Version.jackson_version)
        implementation("com.fasterxml.jackson.core", "jackson-annotations", Version.jackson_version)
        implementation("com.fortanix", "sdkms-client", "3.23.1408")
    }
}
