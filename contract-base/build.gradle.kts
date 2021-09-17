dependencies {
    api(project(":contract-proto"))

    implementation("com.google.protobuf", "protobuf-java", Version.protobuf)

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation(project(":contract-proto", "testArtifacts"))
}

val testConfig = configurations.create("testArtifacts") {
    extendsFrom(configurations["testCompile"])
}

tasks.register("testJar", Jar::class.java) {
    dependsOn("testClasses")
    classifier += "test"
    from(sourceSets["test"].output)
}

artifacts {
    add("testArtifacts", tasks.named<Jar>("testJar") )
}
