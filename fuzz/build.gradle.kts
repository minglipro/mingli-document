plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":serialization"))
    implementation(project(":format-json"))
    implementation(project(":format-yaml"))
    implementation(project(":format-xml"))
    implementation(project(":format-properties"))
    implementation(project(":format-toml"))
    implementation(project(":format-csv"))
    implementation(project(":format-ion"))
    implementation(project(":format-cbor"))
    implementation(project(":format-smile"))
    implementation(project(":format-avro"))
    implementation(project(":format-protobuf"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("fuzzContinuous") {
    group = "verification"
    description = "Run continuous random fuzzing for fuzzSeconds (default 10)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mingliqiye.document.fuzz.FuzzHarnessKt")
    val seconds = providers.gradleProperty("fuzzSeconds").orElse("10").get()
    args(seconds)
    systemProperty("fuzz.seconds", seconds)
}
