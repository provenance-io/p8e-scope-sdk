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
    implementation("io.opentracing", "opentracing-api", Version.openTracing)
    implementation("io.opentracing", "opentracing-util", Version.openTracing)


    testImplementation(project(":contract-proto", "testArtifacts"))
}
