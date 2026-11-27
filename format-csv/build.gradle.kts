plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    api(project(":core"))
    api(project(":exception"))
    api(project(":serialization"))
    implementation("org.apache.commons:commons-csv:1.10.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
