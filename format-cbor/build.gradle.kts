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
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.20.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
