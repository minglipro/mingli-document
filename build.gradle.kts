import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm") version "2.0.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
}

val projectVersion = providers.gradleProperty("projectVersion").get()
val defaultGroup = providers.gradleProperty("projectGroup").get()
val formatGroup = providers.gradleProperty("formatProjectGroup").get()

allprojects {
    group = if (name.startsWith("format-")) formatGroup else defaultGroup
    version = projectVersion

    repositories {
        mavenLocal()
        maven {
            name = "mingliqiye-public"
            url = uri("https://repository.mingliqiye.com/repository/maven-public/")
        }
        mavenCentral()
    }
}

val publishableProjects = subprojects.filter { it.name != "benchmarks" && it.name != "fuzz" }

configure(publishableProjects) {
    apply(plugin = "maven-publish")

    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])

                    // 格式模块保留模块名作为 artifactId：
                    // com.mingliqiye.document.format:format-json
                    artifactId = project.name

                    pom {
                        name.set(project.name)
                        description.set("mingli-document ${project.name} module")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                    }
                }
            }
        }
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds all subprojects."
    dependsOn(subprojects.map { "${it.path}:build" })
}
