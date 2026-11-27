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
    api(project(":serialization"))
    api("com.google.protobuf:protobuf-java:3.25.5")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
