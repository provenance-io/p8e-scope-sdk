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
}
