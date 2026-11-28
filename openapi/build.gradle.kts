plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    api(project(":schema"))
    api(project(":core"))
    api(project(":exception"))
    testImplementation(project(":format-json"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
