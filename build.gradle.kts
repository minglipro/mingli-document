import org.gradle.api.attributes.LibraryElements
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.plugins.signing.SigningExtension
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
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

    // JPMS 自动模块名（Automatic-Module-Name）。
    //
    // 本库字节码目标是 JDK 8，无法在源码中编译 module-info.java（module-info.class 需要
    // class file 53 / JDK 9+，且要放进 Multi-Release jar 的 META-INF/versions/9 才能同时
    // 兼容 JDK 8）。因此先给所有可发布 jar 写入稳定的自动模块名，供 JDK 9+ 以「自动模块」
    // 方式放到 module path 上使用；后续再补真正的 Multi-Release module-info。
    //
    // 规则：
    //   format-*       -> com.mingliqiye.document.format.<name>
    //   其他可发布模块  -> com.mingliqiye.document.<name>（短横线转点，模块名不允许 '-'）
    //
    // 根项目只是聚合构建，benchmarks / fuzz 是非发布模块（见 publishableProjects），
    // 都不写自动模块名；bom 是 java-platform，没有 jar，已在上方提前返回。
    val automaticModuleName = when {
        project == rootProject || name == "benchmarks" || name == "fuzz" -> null
        name.startsWith("format-") ->
            "com.mingliqiye.document.format." + name.removePrefix("format-").replace('-', '.')
        else -> "com.mingliqiye.document." + name.replace('-', '.')
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
            repositories {
                if (providers.environmentVariable("MAVEN_CENTRAL_USERNAME").isPresent &&
                    providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").isPresent
                ) {
                    maven {
                        name = "MavenCentral"
                        url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                        credentials {
                            username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").get()
                            password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").get()
                        }
                    }
                    maven {
                        name = "MavenCentralSnapshot"
                        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                        credentials {
                            username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").get()
                            password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").get()
                        }
                    }
                }
            }
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
                        name.set(artifactId)
                        description.set("mingli-document module ${project.name}")
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
                else -> repositoryName.contains("Release", ignoreCase = true) ||
                    repositoryName == "MavenCentral"
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

        if (automaticModuleName != null) {
            // 只有模块主 jar 需要自动模块名；javadoc / kotlindoc 等 classifier jar 不参与模块路径。
            named<Jar>("jar") {
                manifest {
                    attributes(mapOf("Automatic-Module-Name" to automaticModuleName))
                }
            }
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

    // ------------------------------------------------------------------
    // JPMS：显式 module-info 的 Multi-Release jar（JDK 9+）
    // ------------------------------------------------------------------
    //
    // 本库主 classes 的目标字节码是 JDK 8，不能直接编译 module-info.java（module-info.class
    // 是 class file 53 / JDK 9+ 格式）。这里的做法：
    //   1. 模块把 module-info.java 放在 src/main/java9/，不加入主 source set；
    //   2. 用 JDK 17 工具链的 javac --release 9 单独编译；用 --patch-module 把已编译的
    //      主 classes 挂到待编译模块上（否则 javac 会报 exports 的包不存在）；
    //   3. jar 任务把它放进 META-INF/versions/9/module-info.class，并在清单写
    //      Multi-Release: true。JDK 8 会把 META-INF/versions/9 当作普通资源忽略，
    //      JDK 9+ 才会用这里的模块描述替换「自动模块」。
    //
    // module path 使用本构建内依赖模块的 jar（它们各自带 Multi-Release module-info）与
    // 第三方依赖 jar。compileClasspath 默认以 classes 目录消费同构建内的 project 依赖
    // （compile avoidance），因此这里用 artifactView 重新选择 LibraryElements=jar 的变体；
    // 依赖模块 jar 的构建由 artifact 的 buildDependencies 保证在本任务之前完成。
    val java9ModuleInfoSource = file("src/main/java9/module-info.java")
    if (automaticModuleName != null && java9ModuleInfoSource.isFile) {
        val moduleName = automaticModuleName
        // 一致性校验：module-info.java 里的模块名必须与 Automatic-Module-Name 相同。
        val declaredModuleName = java9ModuleInfoSource.readText().lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("module ") }
            ?.removePrefix("module ")
            ?.substringBefore(' ')
            ?.trim()
        require(declaredModuleName == moduleName) {
            "src/main/java9/module-info.java declares module '$declaredModuleName' " +
                "but Automatic-Module-Name is '$moduleName' (project ${project.path})."
        }

        val java9ModuleInfoClassesDir = layout.buildDirectory.dir("classes/java9ModuleInfo")
        val java17Compiler = extensions.getByType<JavaToolchainService>().compilerFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        val java9ModulePath = configurations.named("compileClasspath").get().incoming.artifactView {
            attributes {
                attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements::class.java, LibraryElements.JAR),
                )
            }
        }.files
        val mainClassesDirs = extensions.getByType<SourceSetContainer>().named("main").map { it.output.classesDirs }

        val compileJava9ModuleInfo = tasks.register<JavaCompile>("compileJava9ModuleInfo") {
            group = "build"
            description = "Compiles src/main/java9/module-info.java with --release 9 for the Multi-Release jar."
            source(java9ModuleInfoSource)
            dependsOn(tasks.named("classes"))
            dependsOn(java9ModulePath)
            javaCompiler.set(java17Compiler)
            destinationDirectory.set(java9ModuleInfoClassesDir)
            classpath = files()
            options.release.set(9)
            options.encoding = "UTF-8"
            options.compilerArgs.add("-proc:none")
            options.compilerArgumentProviders.add(CommandLineArgumentProvider {
                val patchPath = mainClassesDirs.get().files
                    .filter { it.exists() }
                    .joinToString(File.pathSeparator)
                listOf(
                    "--module-path", java9ModulePath.asPath,
                    "--patch-module", "$moduleName=$patchPath",
                )
            })
        }

        tasks.named<Jar>("jar") {
            dependsOn(compileJava9ModuleInfo)
            manifest {
                attributes("Multi-Release" to "true")
            }
            from(compileJava9ModuleInfo) {
                into("META-INF/versions/9")
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

tasks.register("printNativeSmokeClasspath") {
    group = "verification"
    description = "Prints the runtime classpath used by the native-image smoke build."
    dependsOn(":format-json:jar", ":record-support:jar")
    doLast {
        val formatJson = project(":format-json")
        val runtimeJars = formatJson.configurations.getByName("runtimeClasspath").incoming.artifactView {
            attributes {
                attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements::class.java, LibraryElements.JAR),
                )
            }
        }.files
        val jars = LinkedHashSet<File>()
        jars.addAll(runtimeJars.files.filter { it.extension == "jar" })
        jars.add(formatJson.tasks.named<Jar>("jar").get().archiveFile.get().asFile)
        jars.add(project(":record-support").tasks.named<Jar>("jar").get().archiveFile.get().asFile)
        println(jars.joinToString(File.pathSeparator) { it.absolutePath })
    }
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
tasks.register("generateSbom") {
    group = "release"
    description = "Generate a minimal CycloneDX JSON SBOM from runtime dependencies."
    doLast {
        val components = sortedMapOf<String, Triple<String, String, String>>()
        publishableProjects.forEach { project ->
            val artifactId = when {
                project.name == "bom" -> "mingli-document-bom"
                project.name.startsWith("format-") -> project.name.removePrefix("format-")
                else -> project.name
            }
            val purl = "pkg:maven/${project.group}/${artifactId}@${project.version}"
            components[purl] = Triple(project.group.toString(), artifactId, project.version.toString())
        }
        subprojects.forEach { project ->
            val configuration = project.configurations.findByName("runtimeClasspath") ?: return@forEach
            configuration.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                val id = artifact.moduleVersion.id
                if (id.group.isNullOrBlank() || id.version.isNullOrBlank()) return@forEach
                if (id.group.startsWith("com.mingliqiye.document")) return@forEach
                val purl = "pkg:maven/${id.group}/${id.name}@${id.version}"
                components[purl] = Triple(id.group, id.name, id.version)
            }
        }
        val outputDirectory = rootProject.layout.buildDirectory.dir("sbom").get().asFile
        outputDirectory.mkdirs()
        val lines = mutableListOf<String>()
        lines += "{"
        lines += "  \"bomFormat\": \"CycloneDX\","
        lines += "  \"specVersion\": \"1.4\","
        lines += "  \"version\": 1,"
        lines += "  \"components\": ["
        val componentLines = components.entries.mapIndexed { index, entry ->
            val (group, name, version) = entry.value
            val comma = if (index == components.size - 1) "" else ","
            "    {\"type\":\"library\",\"group\":\"${group}\",\"name\":\"${name}\",\"version\":\"${version}\",\"purl\":\"${entry.key}\"}$comma"
        }
        lines += componentLines
        lines += "  ]"
        lines += "}"
        outputDirectory.resolve("cyclonedx.json").writeText(lines.joinToString("\n") + "\n")
    }
}