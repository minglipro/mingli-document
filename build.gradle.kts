plugins {
    kotlin("jvm") version "2.0.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
}

allprojects {
    group = "com.mingliqiye.document"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        maven {
            name = "mingliqiye-public"
            url = uri("https://repository.mingliqiye.com/repository/maven-public/")
        }
        mavenCentral()
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds all subprojects."
    dependsOn(subprojects.map { "${it.path}:build" })
}
