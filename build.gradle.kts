import org.codehaus.groovy.tools.shell.util.Logger.io
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.internal.impldep.org.bouncycastle.cms.RecipientId.password

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.google.protobuf", "protobuf-gradle-plugin", Version.protobufPlugin)
    }
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

group = "io.provenance.scope"
version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
        }
    }
}

subprojects {
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

        signing {
            sign(publishing.publications["maven"])
        }

        tasks.javadoc {
            if(JavaVersion.current().isJava9Compatible) {
                (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
            }
        }
    }
}
