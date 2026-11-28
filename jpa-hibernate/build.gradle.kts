import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    // 主代码与发布字节码保持 JDK 8 目标；Hibernate ORM 6.5 的 class 文件版本是 55
    // （Java 11+），运行期需要 Java 11+，测试使用 JDK 17。
    jvmToolchain(8)
    explicitApi()
}

// jvmToolchain(8) 会让 testCompileClasspath / testRuntimeClasspath 默认声明
// 「目标 JVM 8」，Gradle 变体选择会因此拒绝 Java 11+ 的 Hibernate 依赖
// （例如 hibernate-commons-annotations:6.0.6.Final）。测试实际运行在 JDK 17 上，
// 因此把测试相关 configuration 的目标 JVM 改为 17；主代码目标不受影响。
configurations.named("compileClasspath") {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

configurations.named("runtimeClasspath") {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

configurations.named("testCompileClasspath") {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

configurations.named("testRuntimeClasspath") {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

dependencies {
    api(project(":jpa"))
    api("org.hibernate.orm:hibernate-core:6.5.3.Final")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        },
    )
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
}
