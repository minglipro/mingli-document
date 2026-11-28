plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.0-1.0.21")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
