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
    api("org.apache.avro:avro:1.11.3")
    implementation("org.xerial.snappy:snappy-java:1.1.10.5")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
