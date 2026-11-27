plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    api(project(":serialization"))
    implementation(project(":annotation"))
    implementation(kotlin("reflect"))
    testImplementation(project(":format-json"))
    testImplementation(project(":format-yaml"))
    testImplementation(project(":format-xml"))
    testImplementation(project(":format-properties"))
    testImplementation(project(":format-ion"))
    testImplementation(project(":format-smile"))
    testImplementation(project(":format-cbor"))
    testImplementation(project(":format-csv"))
    testImplementation(project(":format-toml"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
