# bom 模块

`bom` 是 `mingli-document` 的 Maven BOM（Bill of Materials），用于统一本库所有模块的
依赖版本。它是一个 `java-platform` 项目，只声明版本约束，不引入任何依赖。

- 坐标：`com.mingliqiye.document:mingli-document-bom`
- 当前版本：`2.0.0`（来自根 `gradle.properties` 的 `projectVersion`）
- 类型：`pom`（Gradle `java-platform`）
- 目标字节码：不适用（无代码）
- 源码：`bom/build.gradle.kts`

## 职责

| 能力 | 说明 |
|---|---|
| 统一版本 | 导入 BOM 后，本库模块依赖无需再写版本 |
| 版本约束 | 通过 `dependencies.constraints` 约束普通模块与格式模块 |
| 发布元数据 | 生成独立 `mavenBom` publication，带 POM 信息与 GPG 签名 |
| 版本单一来源 | 版本统一取根 `gradle.properties` 的 `projectVersion` |

BOM **不引入依赖**：导入它不会把任何 jar 加到你的 classpath，只有当你声明某个模块时
才解析对应依赖。

## 依赖关系

```text
根 gradle.properties
   projectVersion = 2.0.0
        │
        ▼
   bom（java-platform）
        │ constraints
        ├── annotation
        ├── exception
        ├── core
        ├── schema
        ├── serialization
        ├── serialization-kotlin
        ├── codegen
        ├── openapi
        └── format-* × 11
```

`bom` 是唯一一个不经过根 `build.gradle.kts` 常规 `allprojects` 配置的项目
（源码中 `if (name == "bom") return@allprojects`），因此它不应用 Kotlin/Java/Dokka
插件，只应用 `java-platform`、`maven-publish`、`signing`。

## 管理范围

### 普通模块（`com.mingliqiye.document`）

```text
annotation
exception
core
schema
serialization
serialization-kotlin
codegen
openapi
```

### 格式模块（`com.mingliqiye.document.format`）

```text
json
yaml
xml
properties
toml
csv
ion
cbor
smile
avro
protobuf
```

### 不在 BOM 中

| 项目 | 说明 |
|---|---|
| `benchmarks` | JMH 基准，不发布 |
| `fuzz` | fuzz 工具链，不发布 |
| `codegen-test` | KSP 集成测试工程，不对外发布 |
| `mingli-document-bom` 自身 | 通过 `platform(...)` 导入，不需要版本约束自己 |

## 使用方式

### Gradle（Kotlin DSL）

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))

    implementation("com.mingliqiye.document:core")
    implementation("com.mingliqiye.document:serialization")
    implementation("com.mingliqiye.document.format:json")
    implementation("com.mingliqiye.document.format:yaml")

    // 需要 Kotlin 类型支持时
    implementation("com.mingliqiye.document:serialization-kotlin")

    // KSP 处理器（仅编译期）
    ksp("com.mingliqiye.document:codegen")
}
```

### Gradle（Groovy DSL）

```groovy
dependencies {
    implementation platform("com.mingliqiye.document:mingli-document-bom:2.0.0")
    implementation "com.mingliqiye.document:serialization"
    implementation "com.mingliqiye.document.format:json"
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
```

导入后：

```xml
<dependencies>
    <dependency>
        <groupId>com.mingliqiye.document</groupId>
        <artifactId>serialization</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mingliqiye.document.format</groupId>
        <artifactId>json</artifactId>
    </dependency>
</dependencies>
```

### 仓库

```kotlin
repositories {
    maven {
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}
```

## 版本规则

- 版本唯一来源：根 `gradle.properties`

```properties
projectVersion=2.0.0
```

- 所有发布模块的 `version` 都在根 `build.gradle.kts` 的 `allprojects` 中统一赋值；
- BOM 的 `constraints` 使用同一个 `version` 变量，因此 BOM 版本与它管理的模块版本一致；
- `-SNAPSHOT` 版本只发布到 snapshot 仓库，正式版本只发布到 release 仓库；
- 升级版本时只需改 `projectVersion`，BOM 与全部模块自动跟随。

## 发布

BOM 的发布配置（`bom/build.gradle.kts`）：

```kotlin
plugins {
    `java-platform`
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
            artifactId = "mingli-document-bom"
            pom { /* name / description / url / license */ }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenBom"])
}
```

仓库过滤规则与根项目一致：

| 仓库名 | 是否推送 |
|---|---|
| 含 `GradlePlugin`（忽略大小写） | 永不推送 |
| 版本以 `-SNAPSHOT` 结尾 | 只推名称含 `Snapshot` 的仓库 |
| 正式版本 | 只推名称含 `Release` 的仓库 |

常用命令：

```bash
gradle :bom:build
gradle :bom:publishToMavenLocal
gradle publish                 # 推送到 init.gradle 配置的远程仓库
```

发布产物带 GPG `.asc` 签名；POM 中包含名称、描述、项目 URL 与 Apache-2.0 许可证信息。

## 示例：多模块工程

```kotlin
// 根 build.gradle.kts
subprojects {
    repositories {
        maven { url = uri("https://repository.mingliqiye.com/repository/maven-public/") }
        mavenCentral()
    }
}

// app 模块
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))

    implementation("com.mingliqiye.document.format:json")
    implementation("com.mingliqiye.document.format:yaml")
    implementation("com.mingliqiye.document:serialization-kotlin")
}
```

## 扩展点

BOM 是纯版本约束，没有 SPI。需要扩展时：

1. **新增模块**：在 `bom/build.gradle.kts` 的 `constraints` 中加一行
   `api(project(":new-module"))`，并确保根 `build.gradle` 的发布过滤覆盖它；
2. **外部版本约束**：业务侧可以在自己的 platform/BOM 中补充依赖版本，Gradle 会按
   「更具体约束优先」解析；本 BOM 只约束本库模块；
3. **排除传递依赖**：BOM 不改变传递依赖，排除仍需在具体依赖上用
   `exclude(group = ..., module = ...)`。

## 限制

- BOM 只管理版本，不引入依赖；
- 不管理第三方依赖（如 Jackson、SnakeYAML、ion-java、protobuf-java 等），需要时请自行
  统一版本或用 `gradle/libs.versions.toml`；
- 不管理 `benchmarks` / `fuzz` / `codegen-test`；
- 使用 `platform(...)` 时 BOM 自身版本仍需显式指定（`2.0.0`）；
- 如果某模块在 BOM 中不存在（例如未来新增但未及时加入），不会报错，只会缺少约束；
- BOM 不解决版本冲突：冲突仍由 Gradle/Maven 的解析规则处理。

## 构建

```bash
gradle :bom:build
gradle :bom:publishToMavenLocal
gradle :bom:ktlintCheck   # bom 无 Kotlin 源码，通常为空任务
```

相关文档：

- [模块总览](./index.md)：模块坐标与依赖关系
- [安装指南](../guide/installation.md)：站点安装章节
- [迁移指南](../guide/migration.md)：1.0.0 → 2.0.0 坐标升级
