import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

plugins {
    jacoco
}


dependencies {
    // re-exported deps
    api(project(":os-client"))
    api(project(":contract-base"))
    api(project(":contract-proto"))
    api(project(":encryption"))
    api(project(":engine"))
    implementation(project(":util"))
    implementation(project(":engine"))
    api("io.provenance.protobuf", "pb-proto-java", Version.provenanceProtos)

    compileOnly("org.slf4j", "log4j-over-slf4j", "1.7.30")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)
    implementation("com.fortanix", "sdkms-client", "3.23.1408")

    testImplementation("io.kotest:kotest-runner-junit5:4.4.+")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testImplementation("io.mockk:mockk:1.12.0")
    testImplementation("org.slf4j", "log4j-over-slf4j", "1.7.30")
    testImplementation(project(":contract-proto", "testArtifacts"))
    testImplementation("org.junit.platform:junit-platform-commons:1.5.2")
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        events = setOf(PASSED, FAILED, SKIPPED, STANDARD_ERROR)
        exceptionFormat = FULL
    }
    finalizedBy("jacocoTestReport")
}
