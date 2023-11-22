dependencies {
    implementation(project(":contract-proto"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)

    testImplementation(kotlin("test"))
    testImplementation(project(":contract-proto", "testArtifacts"))
}

val testConfig = configurations.create("testArtifacts") {
    extendsFrom(configurations["testImplementation"])
}

tasks.register("testJar", Jar::class.java) {
    dependsOn("testClasses")
    archiveClassifier = "test"
    from(sourceSets.test.get().output)
}

artifacts {
    add("testArtifacts", tasks.named<Jar>("testJar") )
}
