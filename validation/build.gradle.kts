plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(8)
    explicitApi()
}

dependencies {
    api(project(":serialization"))
    api(project(":annotation"))
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    testImplementation(project(":format-json"))
    testImplementation(kotlin("test"))
    testImplementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    testImplementation("org.glassfish:jakarta.el:4.0.2")
}

tasks.test {
    useJUnitPlatform()
    // Hibernate Validator 8 与 Jakarta EL 4 需要 Java 11+ 运行时，
    // 主代码仍按 JDK 8 目标编译与发布，仅测试任务使用 Java 17 运行时。
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(17))
        },
    )
}

// Java 测试模型包含中文注释，显式按 UTF-8 编译，避免平台默认编码告警。
tasks.compileTestJava {
    options.encoding = "UTF-8"
}
