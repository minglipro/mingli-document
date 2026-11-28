---
title: 安装与依赖
---

# 安装与依赖

> 本页说明运行环境、Maven 坐标、BOM、发布仓库、传递依赖，以及从源码构建与发布的方式。
> 所有坐标都以仓库内 `gradle.properties` 的 `projectVersion` 为准，当前为 `2.0.0`。

## 目录

- [环境要求](#环境要求)
- [发布仓库](#发布仓库)
- [Gradle 安装](#gradle-安装)
- [Maven 安装](#maven-安装)
- [使用 BOM 统一版本](#使用-bom-统一版本)
- [模块与 artifact](#模块与-artifact)
- [传递依赖](#传递依赖)
- [从源码构建](#从源码构建)
- [发布流程](#发布流程)
- [安装排错](#安装排错)
- [验证安装](#验证安装)

## 环境要求

| 项目 | 要求 | 说明 |
|---|---|---|
| JDK 运行 | **JDK 8+** | 所有模块字节码目标为 JDK 8（`gradle.properties` 的 `jvmVersion=8`） |
| Java record 映射 | **JDK 16+ 运行时** | 反射 `Class.isRecord()` / `RecordComponent` 从 JDK 16 开始提供；运行在 JDK 8 时自动跳过该分支 |
| Kotlin | `serialization-kotlin` 需要 **Kotlin 2.0**（本库用 Kotlin 2.0.0 编译） | 只用 Java 模块时 Kotlin 版本无关 |
| Gradle | **Gradle 8.x** | 仅构建源码时需要；项目使用 `foojay-resolver-convention` 解析 JDK 工具链 |
| KSP | `2.0.0-1.0.21` | 仅使用 `codegen`（`@DocumentEnumCodec`）时需要，版本必须与 Kotlin 2.0.0 匹配 |

::: warning 关于 Kotlin 版本
`serialization-kotlin` 使用 Kotlin 2.0.0 编译，其 Kotlin 元数据版本为 2.0。
Kotlin 1.x 编译器可能无法读取。若你的项目仍是 Kotlin 1.9 或更早，请只使用 Java 侧
模块（`core` / `serialization` / `format-*`），或先升级到 Kotlin 2.0。
:::

## 发布仓库

正式版本同时发布到项目自建仓库与 Maven Central。推荐优先配置自建仓库，再回退
Maven Central：

```text
https://repository.mingliqiye.com/repository/maven-public/
```

Gradle：

```kotlin
repositories {
    maven {
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}
```

Maven：

```xml
<repositories>
    <repository>
        <id>mingliqiye-public</id>
        <url>https://repository.mingliqiye.com/repository/maven-public/</url>
    </repository>
</repositories>
```

发布规则（源码 `build.gradle.kts` 实现）：

- `-SNAPSHOT` 版本只推 snapshot 仓库；
- 正式版本只推 release 仓库与 Maven Central；
- Gradle Plugin 专用仓库不参与本库发布；
- 所有 jar、pom、module metadata、javadoc、kotlindoc 都带 GPG `.asc` 签名。

## Gradle 安装

### 最基本：只要 JSON

```kotlin
plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    maven {
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    implementation("com.mingliqiye.document.format:json:2.0.0")
}
```

`format:json` 会传递依赖 `core`、`exception`、`serialization`，无需重复声明。

### 常用组合

```kotlin
dependencies {
    // 格式
    implementation("com.mingliqiye.document.format:json:2.0.0")
    implementation("com.mingliqiye.document.format:yaml:2.0.0")
    implementation("com.mingliqiye.document.format:cbor:2.0.0")

    // Kotlin data class 支持
    implementation("com.mingliqiye.document:serialization-kotlin:2.0.0")
}
```

### Groovy DSL

```groovy
dependencies {
    implementation 'com.mingliqiye.document.format:json:2.0.0'
    implementation 'com.mingliqiye.document:serialization-kotlin:2.0.0'
}
```

## Maven 安装

```xml
<dependencies>
    <dependency>
        <groupId>com.mingliqiye.document.format</groupId>
        <artifactId>json</artifactId>
        <version>2.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.mingliqiye.document</groupId>
        <artifactId>serialization-kotlin</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
```

## 使用 BOM 统一版本

BOM 的坐标为：

```text
com.mingliqiye.document:mingli-document-bom:2.0.0
```

### Gradle

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))

    // 之后所有模块都不写版本
    implementation("com.mingliqiye.document:core")
    implementation("com.mingliqiye.document:serialization")
    implementation("com.mingliqiye.document.format:json")
    implementation("com.mingliqiye.document.format:yaml")
}
```

Groovy：

```groovy
dependencies {
    implementation platform('com.mingliqiye.document:mingli-document-bom:2.0.0')
    implementation 'com.mingliqiye.document.format:json'
}
```

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.mingliqiye.document</groupId>
            <artifactId>mingli-document-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.mingliqiye.document.format</groupId>
        <artifactId>json</artifactId>
    </dependency>
</dependencies>
```

BOM 只管理版本，不引入依赖。它覆盖：

```text
com.mingliqiye.document:annotation
com.mingliqiye.document:exception
com.mingliqiye.document:core
com.mingliqiye.document:schema
com.mingliqiye.document:serialization
com.mingliqiye.document:serialization-kotlin
com.mingliqiye.document:codegen
com.mingliqiye.document:openapi
com.mingliqiye.document.format:{json,yaml,xml,properties,toml,csv,ion,cbor,smile,avro,protobuf}
```

`benchmarks`、`fuzz`、`codegen-test` **不在** BOM 中，它们也不发布。

## 模块与 artifact

普通模块与格式模块的 group 不同，这是最常见的坐标错误来源：

| 模块 | group | artifactId | 说明 |
|---|---|---|---|
| annotation | `com.mingliqiye.document` | `annotation` | 格式无关注解 |
| exception | `com.mingliqiye.document` | `exception` | `DocumentException` / `DocumentTypeException` |
| core | `com.mingliqiye.document` | `core` | Node 树、`DocumentTypeReference` |
| schema | `com.mingliqiye.document` | `schema` | `DocumentSchema` / `SchemaField` / `SchemaType` |
| serialization | `com.mingliqiye.document` | `serialization` | `DocumentMapper`、配置、Token、Bean 映射 |
| serialization-kotlin | `com.mingliqiye.document` | `serialization-kotlin` | Kotlin data class / value class / sealed |
| codegen | `com.mingliqiye.document` | `codegen` | KSP 处理器（`ksp` 配置引用） |
| openapi | `com.mingliqiye.document` | `openapi` | OpenAPI 3.0 生成 |
| json | `com.mingliqiye.document.format` | `json` | JSON / JSONC |
| yaml | `com.mingliqiye.document.format` | `yaml` | YAML |
| xml | `com.mingliqiye.document.format` | `xml` | XML |
| properties | `com.mingliqiye.document.format` | `properties` | Java Properties |
| toml | `com.mingliqiye.document.format` | `toml` | TOML |
| csv | `com.mingliqiye.document.format` | `csv` | CSV |
| ion | `com.mingliqiye.document.format` | `ion` | Amazon Ion |
| cbor | `com.mingliqiye.document.format` | `cbor` | CBOR |
| smile | `com.mingliqiye.document.format` | `smile` | Smile |
| avro | `com.mingliqiye.document.format` | `avro` | Apache Avro |
| protobuf | `com.mingliqiye.document.format` | `protobuf` | Protocol Buffers |
| bom | `com.mingliqiye.document` | `mingli-document-bom` | Maven BOM |

规则：源码模块名以 `format-` 开头时，发布 group 取 `formatProjectGroup`，artifactId
去掉 `format-` 前缀；其余模块 group 取 `projectGroup`，artifactId 就是模块名。

## 传递依赖

格式模块的 `core` / `exception` / `serialization` 通过 `api` 暴露，因此传递可用。
第三方依赖如下（版本取自各模块 `build.gradle.kts`）：

| 模块 | 第三方依赖 |
|---|---|
| serialization | `com.mingliqiye.utils.aliasfor:alias-for:1.0.3` |
| serialization-kotlin | `org.jetbrains.kotlin:kotlin-reflect` |
| json | `com.fasterxml.jackson.core:jackson-core:2.15.3` |
| yaml | `org.yaml:snakeyaml:2.6` |
| cbor | `com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.15.3` |
| smile | `com.fasterxml.jackson.dataformat:jackson-dataformat-smile:2.15.3` |
| ion | `com.amazon.ion:ion-java:1.11.9` |
| avro | `org.apache.avro:avro:1.11.3`（`api`，含 `schema` 模块）、`org.xerial.snappy:snappy-java:1.1.10.5` |
| protobuf | `com.google.protobuf:protobuf-java:3.25.5`（`api`，含 `schema` 模块） |
| csv | `org.apache.commons:commons-csv:1.10.0`（`api` 暴露 `schema` 模块） |
| toml | `org.tomlj:tomlj:1.0.0` |
| xml | 项目内 `annotation`；无第三方依赖（JDK 自带 DOM/SAX） |
| properties | 无第三方依赖 |

::: tip 最小体积
只想要 JSON 时不引入 `serialization-kotlin`（它带 `kotlin-reflect`）；
只是做 JSON ↔ Node 和简单 Bean 映射，一个 `format:json` 即可。
:::

## 从源码构建

```bash
# 全量检查与构建（测试 + ktlint + Dokka）
gradle clean ktlintCheck build

# 单个模块测试
gradle :format-json:test
gradle :serialization:test
gradle :serialization-kotlin:test
gradle :fuzz:test

# 代码格式
gradle ktlintFormat
```

说明：

- 根 `build.gradle.kts` 统一应用 `java`、`kotlin("jvm")`、`maven-publish`、`signing`、
  `dokka`、`dokka-javadoc`、`ktlint`；各模块只声明自己的依赖。
- `jvmToolchain(8)`：主代码按 JDK 8 目标编译。
- Java record 映射测试需要 JDK 16+ 运行时，单独用 Java 17 工具链执行：
  `gradle :serialization:recordTest`。
- `fuzz:test` 会排除 `*JqfTest*`；Jazzer/JQF 是独立任务。

本地安装到 `~/.m2`：

```bash
gradle publishToMavenLocal
```

然后在业务项目里用 `mavenLocal()` 获取 `2.0.0`。

### 使用 codegen 时的构建配置

根工程声明 KSP 插件（版本必须与 Kotlin 2.0.0 匹配）：

```kotlin
plugins {
    kotlin("jvm") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}
```

业务模块应用插件、声明处理器：

```kotlin
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

dependencies {
    api("com.mingliqiye.document:annotation")
    api("com.mingliqiye.document:serialization")
    ksp("com.mingliqiye.document:codegen")
}
```

详细用法见 [代码生成](./codegen.md)。

## 发布流程

以下流程面向本项目维护者，使用方不需要关心：

```bash
# 本地
gradle publishToMavenLocal

# 远程：仓库地址与凭据由 Gradle init.gradle 提供
gradle publish

# Maven Central：需要环境变量与 GPG
# MAVEN_CENTRAL_USERNAME / MAVEN_CENTRAL_PASSWORD
gradle publish
```

- 版本号只改根 `gradle.properties` 的 `projectVersion`；
- 正式版本推送 Maven Central 与 release 仓库，`-SNAPSHOT` 只推 snapshot；
- jar、pom、module metadata、javadoc、kotlindoc 全部签名。

## 安装排错

| 现象 | 原因 | 解决 |
|---|---|---|
| 找不到 `com.mingliqiye.document:json` | 格式模块 group 不同 | 用 `com.mingliqiye.document.format:json` |
| 找不到 `format-json` artifact | artifactId 去掉了 `format-` 前缀 | 用 `format:json` |
| 解析不到版本 | 只配置了 Maven Central，而版本在自建仓库 | 加 `repository.mingliqiye.com` 仓库 |
| `Failed to resolve: ...2.0.0` 但本地明明有 | 业务项目用了 `mavenLocal()` 但仓库顺序/缓存问题 | `gradle --refresh-dependencies` |
| Kotlin 编译报元数据版本不兼容 | 业务 Kotlin < 2.0，同时依赖了 `serialization-kotlin` | 升级 Kotlin 到 2.0，或仅依赖 Java 模块 |
| KSP 报 “ksp version mismatch” | KSP 版本与 Kotlin 版本不匹配 | 使用 `2.0.0-1.0.21` |
| record 反序列化失败 / 走了无参构造 | 运行在 JDK 8~15 | 需要 JDK 16+ 运行时 |
| 构建源码时 `signing` 失败 | 本地没有 GPG | 用 `publishToMavenLocal`，或配置 GPG（发布才需要） |

## 验证安装

写一个最小 `main`，确认依赖与版本正确：

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.JsonMapper

fun main() {
    val mapper = JsonMapper()
    val node: Node = mapper.parse("""{"ok":true,"version":"2.0.0"}""", Node::class.java)
    check(node.asObject()["ok"]?.asBoolean() == true)
    println("mingli-document ${node.asObject()["version"]?.asString()} is ready")
}
```

下一步：读 [核心概念](./core-concepts.md) 理解 Node 树与 Mapper 的关系。
