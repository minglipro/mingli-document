plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    api(project(":annotation"))
    api(project(":serialization"))
    ksp(project(":codegen"))
    kspTest(project(":codegen"))
    testImplementation(project(":format-json"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
