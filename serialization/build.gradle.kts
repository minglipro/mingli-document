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
    api("com.mingliqiye.utils.aliasfor:alias-for:1.0.4")
    api(project(":annotation"))
    testImplementation(project(":format-json"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Java record 只能在 JDK 16+ 运行时创建与加载，默认测试任务运行在 JDK 8 工具链上，
    // 由独立的 recordTest 任务用 Java 17 运行时验证。
    filter {
        excludeTestsMatching("*RecordMappingTest")
    }
}

// Java 测试模型保留构造参数名，用于覆盖 @DocumentCreator 的参数名回退与读写字段名对称性。
tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

// Java record 映射测试。
//
// 主代码与测试代码仍按 JDK 8 目标编译：record 模型不在源码中出现，由测试在 Java 17
// 运行时用系统编译器（ToolProvider）动态编译并加载。默认 test 任务运行在 JDK 8 上，
// 因此单独用 Java 17 工具链运行该测试。
val recordTest by tasks.registering(Test::class) {
    description = "Run Java record mapping tests on a JDK 17 runtime."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
        },
    )
    filter {
        includeTestsMatching("*RecordMappingTest")
    }
}
