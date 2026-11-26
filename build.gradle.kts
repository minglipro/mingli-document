plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
}

group = "com.mingliqiye.json"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(8)
}

tasks.test {
    useJUnitPlatform()
}
