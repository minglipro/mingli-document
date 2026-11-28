import org.gradle.api.attributes.LibraryElements
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File

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
    api(project(":exception"))
    api(project(":serialization"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

/**
 * 生成只包含 JSON 映射链的 JDK 17 jlink 运行时镜像。
 *
 * 依赖模块均为显式 module-info：format-json / serialization / core / exception /
 * annotation 以及 kotlin-stdlib / alias-for / mingli-exception。
 */
tasks.register<Exec>("jlinkImage") {
    group = "distribution"
    description = "Creates a JDK 17 jlink runtime image for format-json."
    dependsOn(tasks.jar)
    val runtimeJars = configurations.runtimeClasspath.get().incoming.artifactView {
        attributes {
            attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                objects.named(LibraryElements::class.java, LibraryElements.JAR),
            )
        }
    }.files
    val java17 = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    val outputDir = layout.buildDirectory.dir("jlink/format-json")
    inputs.files(runtimeJars)
    inputs.files(tasks.jar.flatMap { it.archiveFile })
    outputs.dir(outputDir)
    doFirst {
        val target = outputDir.get().asFile
        if (target.exists()) {
            target.deleteRecursively()
        }
        val jlink = java17.get().metadata.installationPath.file("bin/jlink.exe").asFile
        val modulePath = (runtimeJars.files + tasks.jar.get().archiveFile.get().asFile)
            .filter { it.extension == "jar" }
            .joinToString(File.pathSeparator) { it.absolutePath }
        commandLine(
            jlink.absolutePath,
            "--module-path",
            modulePath,
            "--add-modules",
            "com.mingliqiye.document.format.json",
            "--output",
            target.absolutePath,
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
        )
    }
}
