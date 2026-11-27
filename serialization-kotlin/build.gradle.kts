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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
