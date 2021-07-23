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
}

subprojects {
    group = "io.provenance.p8e"
    version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"
    val subProjectName = name

    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    // apply(plugin = "java")

    // dependencies {
    //     val implementation by configurations
    //     implementation("org.slf4j", "log4j-over-slf4j", "1.7.30")
    //     implementation("com.fasterxml.jackson.core", "jackson-core", Version.jackson_version)
    //     implementation("com.fasterxml.jackson.core", "jackson-databind", Version.jackson_version)
    //     implementation("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", Version.jackson_version)
    //     implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", Version.jackson_version)
    //     implementation("com.fasterxml.jackson.core", "jackson-annotations", Version.jackson_version)
    //     implementation("com.fortanix", "sdkms-client", "3.23.1408")
    // }

    java {
        withJavadocJar()
        withSourcesJar()
    }

    publishing {
        repositories {
            maven {
                name = "MavenCentral"
                url = if (version == "1.0-SNAPSHOT") {
                   uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                } else {
                    uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                }

                credentials {
                    username = findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME")
                    password = findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD")
                }
            }
        }
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
                        connection.set("git@github.com:provenance-io/p8e.git")
                        developerConnection.set("git@github.com:provenance-io/p8e.git")
                        url.set("https://github.com/provenance-io/p8e")
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
