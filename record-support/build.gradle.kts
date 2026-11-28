import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(17)
    explicitApi()
    compilerOptions {
        // 本模块直接使用 Java 16+ 的 record 反射 API，字节码目标固定为 JDK 17。
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":serialization"))
    testImplementation(project(":format-json"))
    testImplementation(kotlin("test"))
}

// 测试里的 record 模型用 Java 17 编译（jvmToolchain(17) 已保证运行期 JDK 17）。
tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
}
