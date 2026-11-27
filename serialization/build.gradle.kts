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
    api("com.mingliqiye.utils.aliasfor:alias-for:1.0.3")
    api(project(":annotation"))
    testImplementation(project(":format-json"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// Java 测试模型保留构造参数名，用于覆盖 @DocumentCreator 的参数名回退与读写字段名对称性。
tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
