plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":serialization"))
    implementation(project(":format-json"))
    implementation(project(":format-yaml"))
    implementation(project(":format-xml"))
    implementation(project(":format-properties"))
    implementation(project(":format-toml"))
    implementation(project(":format-csv"))
    implementation(project(":format-ion"))
    implementation(project(":format-cbor"))
    implementation(project(":format-smile"))
    implementation(project(":format-avro"))
    implementation(project(":format-protobuf"))
    testImplementation(kotlin("test"))
    testImplementation("edu.berkeley.cs.jqf:jqf-fuzz:3.0")
    testImplementation("edu.berkeley.cs.jqf:jqf-junit5:3.0")
}

tasks.test {
    useJUnitPlatform()
    exclude("**/*JqfTest*.class")
}

tasks.register<JavaExec>("fuzzContinuous") {
    group = "verification"
    description = "Run continuous random fuzzing for fuzzSeconds (default 10)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mingliqiye.document.fuzz.FuzzHarnessKt")
    val seconds = providers.gradleProperty("fuzzSeconds").orElse("10").get()
    args(seconds)
    systemProperty("fuzz.seconds", seconds)
}

val jazzer by configurations.creating

dependencies {
    jazzer("com.code-intelligence:jazzer:0.22.1")
}

tasks.register<JavaExec>("fuzzJazzer") {
    group = "verification"
    description = "Run one Jazzer target for fuzzSeconds (default 10)."
    dependsOn("classes")
    classpath = files(jazzer) + sourceSets["main"].runtimeClasspath
    mainClass.set("com.code_intelligence.jazzer.Jazzer")
    val seconds = providers.gradleProperty("fuzzSeconds").orElse("10").get()
    val target = providers.gradleProperty("jazzerTarget")
        .orElse("com.mingliqiye.document.fuzz.JsonJazzerTarget")
        .get()
    args(
        "--target_class=$target",
        "-max_total_time=$seconds",
    )
}

val jqfInstrument by configurations.creating {
    isTransitive = false
}

dependencies {
    jqfInstrument("edu.berkeley.cs.jqf:jqf-instrument:3.0")
}

tasks.register<Test>("fuzzJqf") {
    group = "verification"
    description = "Run JQF coverage-guided fuzz tests for fuzzSeconds (default 10)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
        },
    )
    val seconds = providers.gradleProperty("fuzzSeconds").orElse("10").get()
    maxHeapSize = "1g"
    jvmArgs("-javaagent:${jqfInstrument.singleFile.absolutePath}")
    systemProperty("jqf.fuzz", "true")
    systemProperty("jqf.fuzz.duration", "${seconds}s")
    systemProperty("jqf.fuzz.out", layout.buildDirectory.dir("jqf-results").get().asFile.absolutePath)
    filter {
        includeTestsMatching("*JqfTest*")
    }
}
