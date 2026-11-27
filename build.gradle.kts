import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.SigningExtension
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.0.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1" apply false
    id("org.jetbrains.dokka") version "2.1.0" apply false
    id("org.jetbrains.dokka-javadoc") version "2.1.0" apply false
}

val projectVersion = providers.gradleProperty("projectVersion").get()
val defaultGroup = providers.gradleProperty("projectGroup").get()
val formatGroup = providers.gradleProperty("formatProjectGroup").get()
val jvmVersion = providers.gradleProperty("jvmVersion").get().toInt()

allprojects {
    group = if (name.startsWith("format-")) formatGroup else defaultGroup
    version = projectVersion

    if (name == "bom") {
        return@allprojects
    }

    plugins.apply("idea")
    plugins.apply("java")
    plugins.apply("maven-publish")
    plugins.apply("signing")
    plugins.apply("org.jetbrains.kotlin.jvm")
    plugins.apply("org.jetbrains.dokka")
    plugins.apply("org.jetbrains.dokka-javadoc")

    repositories {
        mavenLocal()
        maven {
            name = "mingliqiye-public"
            url = uri("https://repository.mingliqiye.com/repository/maven-public/")
        }
        mavenCentral()
    }

    extensions.configure<SigningExtension> {
        useGpgCmd()
    }

    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = if (project.name.startsWith("format-")) {
                        project.name.removePrefix("format-")
                    } else {
                        project.name
                    }
                    artifact(tasks.named("javaDocJar"))
                    artifact(tasks.named("kotlinDocJar"))
                    pom {
                        url = "https://github.com/minglipro/mingli-document"
                        licenses {
                            license {
                                name = "The Apache License, Version 2.0"
                                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                            }
                        }
                        developers {
                            developer {
                                id = "minglipro"
                                name = "mingli"
                                email = "minglipro@163.com"
                            }
                        }
                        scm {
                            connection = "scm:git:https://github.com/minglipro/mingli-document.git"
                            developerConnection = "scm:git:https://git.mingliqiye.com:minglipro/mingli-document.git"
                            url = "https://github.com/minglipro/mingli-document"
                        }
                    }
                }
            }
        }
        extensions.configure<SigningExtension> {
            sign(project.extensions.getByType<PublishingExtension>().publications.getByName("mavenJava"))
        }
    }

    // 推送仓库由 init.gradle 提供；这里只按版本选择 release/snapshot 仓库，
    // 并跳过 Gradle Plugin 专用仓库。
    tasks.withType<PublishToMavenRepository>().configureEach {
        onlyIf {
            val repositoryName = repository.name
            val snapshot = project.version.toString().endsWith("-SNAPSHOT")
            when {
                repositoryName.contains("GradlePlugin", ignoreCase = true) -> false
                snapshot -> repositoryName.contains("Snapshot", ignoreCase = true)
                else -> repositoryName.contains("Release", ignoreCase = true)
            }
        }
    }

    tasks {
        register<Jar>("javaDocJar") {
            description = "javaDocJar"
            group = "build"
            archiveClassifier.set("javadoc")
            dependsOn("dokkaGeneratePublicationJavadoc")
            from(project.layout.buildDirectory.get().asFile.resolve("dokka/javadoc"))
        }
        register<Jar>("kotlinDocJar") {
            description = "kotlinDocJar"
            group = "build"
            archiveClassifier.set("kotlindoc")
            dependsOn("dokkaGeneratePublicationHtml")
            from(project.layout.buildDirectory.get().asFile.resolve("dokka/html"))
        }
        named("build") {
            dependsOn("javaDocJar", "kotlinDocJar")
        }

        withType<org.gradle.jvm.tasks.Jar> {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(rootProject.file("LICENSE")) { into("META-INF") }
            from(rootProject.file("NOTICE")) { into("META-INF") }
            manifest {
                attributes(
                    mapOf(
                        "Specification-Title" to project.name,
                        "Specification-Version" to project.version,
                        "Specification-Vendor" to "minglipro",
                        "Specification-Build-Time" to LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS")),
                        "Specification-Package-Name" to project.group,
                        "Specification-Package-Id" to group,
                        "Specification-Build-Number" to "1",
                        "Specification-Build-OS" to System.getProperty("os.name"),
                        "Specification-Build-Java" to System.getProperty("java.version"),
                        "Specification-Build-Java-Version" to jvmVersion,
                        "Specification-Build-Java-Vendor" to System.getProperty("java.vendor"),
                        "Specification-Build-Java-Vendor-URL" to System.getProperty("java.vendor.url"),
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to project.version,
                        "Implementation-Package-Name" to project.group,
                        "Implementation-Package-Id" to group,
                        "Implementation-Vendor" to "minglipro",
                        "Implementation-Build-Time" to LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS")),
                    ),
                )
            }
        }
    }
}

val publishableProjects = subprojects.filter { it.name != "benchmarks" && it.name != "fuzz" }

tasks.register("buildAll") {
    group = "build"
    description = "Builds all subprojects."
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("generateChecksums") {
    group = "release"
    description = "Generate SHA-256 checksums for all assembled jar artifacts."
    dependsOn(subprojects.map { "${it.path}:assemble" })
    doLast {
        val outputDirectory = rootProject.layout.buildDirectory.dir("checksums").get().asFile
        outputDirectory.mkdirs()
        val lines = subprojects
            .flatMap { project ->
                project.layout.buildDirectory.dir("libs").get().asFile
                    .listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "jar" }
                    .map { file -> project.name to file }
            }
            .sortedBy { (projectName, file) -> "$projectName/${file.name}" }
            .map { (projectName, file) ->
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                "$hash  $projectName/${file.name}"
            }
        outputDirectory.resolve("SHA256SUMS.txt").writeText(lines.joinToString("\n") + "\n")
    }
}