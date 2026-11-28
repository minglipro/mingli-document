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
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
