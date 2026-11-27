plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    api("com.mingliqiye.exception:mingli-exception:1.1.1-Final")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
