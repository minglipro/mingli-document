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
    implementation("org.yaml:snakeyaml:2.6")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
