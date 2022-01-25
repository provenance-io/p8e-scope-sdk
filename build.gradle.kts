import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.google.protobuf", "protobuf-gradle-plugin", Version.protobufPlugin)
    }
}

repositories {
    mavenCentral()
}

plugins {
    idea
    jacoco
    `maven-publish`
    signing
    `java-library`

    id("org.jetbrains.kotlin.jvm") version Version.kotlin apply(false)
    id("io.github.gradle-nexus.publish-plugin") version Version.nexusPublishPlugin
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            stagingProfileId.set("3180ca260b82a7") // prevents querying for the staging profile id, performance optimization
        }
    }
}

val scopeSdkGroup = "io.provenance.scope"
val scopeSdkVersion = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

group = scopeSdkGroup
version = scopeSdkVersion

subprojects {
    group = scopeSdkGroup
    version = scopeSdkVersion

    val subProjectName = name

    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")

    repositories {
        mavenCentral()
    }

    tasks.withType<KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }

    dependencies {
        testImplementation("io.kotest:kotest-runner-junit5:4.4.+")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
        testImplementation("io.mockk:mockk:1.12.0")
        testImplementation("org.slf4j", "log4j-over-slf4j", "1.7.30")
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

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "io.provenance.scope"
                artifactId = subProjectName

                from(components["java"])

                pom {
                    name.set("Provenance Contract Execution")
                    description.set("A collection of libraries that interact and run Provenance Java based contracts.")
                    url.set("https://provenance.io")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("scirner22")
                            name.set("Stephen Cirner")
                            email.set("scirner@figure.com")
                        }
                    }

                    scm {
                        connection.set("git@github.com:provenance-io/p8e-scope-sdk.git")
                        developerConnection.set("git@github.com:provenance-io/p8e-scope-sdk.git")
                        url.set("https://github.com/provenance-io/p8e-scope-sdk")
                    }
                }
            }
        }

//        signing {
//            sign(publishing.publications["maven"])
//        }

        tasks.javadoc {
            if(JavaVersion.current().isJava9Compatible) {
                (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
            }
        }
    }
}

task<JacocoReport>("jacocoRootReport") {
    dependsOn(subprojects.map { it.tasks.withType<Test>() })
    dependsOn(subprojects.map { it.tasks.withType<JacocoReport>() })
    additionalSourceDirs.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
    sourceDirectories.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs })
    classDirectories.setFrom(subprojects.map { it.the<SourceSetContainer>()["main"].output })
    executionData.setFrom(project.fileTree(".") {
        include("sdk/build/jacoco/test.exec", "engine/build/jacoco/test.exec")
    })
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it).exclude(
                "com/google/*",
                "io/provenance/objectstore/*",
                "io/provenance/scope/contract/*",
                "io/provenance/scope/proto",
                "io/provenance/scope/encryption/*",
                "io/provenance/scope/objectstore/*",
                "io/provenance/scope/util",
                "io/provenance/scope/classloader",
                "io/provenance/scope/definition",
            )})
        )
    }
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(File("${buildDir}/reports/jacoco"))
        csv.required.set(false)
    }
}

tasks.check {
    dependsOn("jacocoRootReport")
}
