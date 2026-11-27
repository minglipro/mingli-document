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
    api("com.mingliqiye.utils.aliasfor:alias-for:1.0.3")
    implementation(project(":annotation"))
    testImplementation(project(":format-json"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
