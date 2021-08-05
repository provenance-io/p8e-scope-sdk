import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

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

    implementation("org.slf4j", "log4j-over-slf4j", "1.7.30")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlin)
    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)
    implementation("com.google.protobuf", "protobuf-java-util", Version.protobuf)
    implementation("com.google.guava", "guava", Version.guava)

    testImplementation("io.kotest:kotest-runner-junit5:4.4.+")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testImplementation("io.mockk:mockk:1.12.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    
    testLogging {
        showStandardStreams = true
        events = setOf(PASSED, FAILED, SKIPPED, STANDARD_ERROR)
        exceptionFormat = FULL
    }
}
