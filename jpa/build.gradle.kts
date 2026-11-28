import org.gradle.jvm.toolchain.JavaLanguageVersion

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
    api(project(":serialization"))
    api("jakarta.persistence:jakarta.persistence-api:3.1.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // jakarta.persistence-api 3.1.0（Jakarta EE 10）的 class 文件版本是 55（Java 11+），
    // 主代码仍以 JDK 8 为目标编译（发布字节码不变）；仅测试执行 JVM 使用 17，
    // 否则运行期加载 JPA 注解类会抛 UnsupportedClassVersionError。
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        },
    )
}
