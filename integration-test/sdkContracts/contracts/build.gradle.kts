buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
}

plugins {
    id("maven-publish")
    id("kotlin")
}

dependencies {
    api(project(":protos"))
    implementation("io.provenance.scope:contract-base:1.0-SNAPSHOT")
}

publishing {
    publications {
        create<MavenPublication>("contract") {
            artifact(tasks.jar)
        }
    }
}
