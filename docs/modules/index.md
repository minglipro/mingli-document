# 模块总览

`mingli-document` 是一个**格式无关**的 Java / Kotlin 文档处理库。它把文档统一抽象成一棵
`Node` 树，各格式模块只负责「文本 / 二进制 ⇄ `Node`」的编解码，`serialization` 负责
「`Node` ⇄ Java Bean / Kotlin data class」的映射。因此格式之间可以互相转换，Bean 映射也
只写一份。

```text
        文本 / 字节数组 / 文件 / 流
                   │
        ┌──────────▼──────────┐
        │   format-* (11 种)   │   JSON/YAML/XML/Properties/TOML/CSV/
        │  JsonMapper ...      │   Ion/CBOR/Smile/Avro/Protobuf
        └──────────┬──────────┘
                   │  Node 树（core）
        ┌──────────▼──────────┐
        │    serialization     │   DocumentMapper / DocumentMapperApi
        │  Bean•集合•Map•泛型  │   Token / 流式 / 注解 / 配置 / 多态
        └──────────┬──────────┘
                   │
         Java Bean / Kotlin data class
```

## 版本与环境

| 项目 | 值 |
|---|---|
| 当前版本 | `2.3.0`（根 `gradle.properties` 的 `projectVersion`） |
| 目标字节码 | JDK 8（`jvmVersion=8`） |
| 构建 Kotlin | 2.3.0（根 `build.gradle.kts`） |
| KSP | `2.0.0-1.0.21`（仅 `codegen` 与使用方需要） |
| 构建工具 | Gradle 8.x（仓库自带 wrapper） |
| 许可证 | Apache License 2.0 |
| 仓库 | `https://github.com/minglipro/mingli-document` |

> 下文所有 API、配置项、默认值均来自本仓库源码与各模块 README，与该版本一一对应。

## 模块地图

### 基础模块

| 模块 | Maven 坐标 | 职责 | 公开依赖 |
|---|---|---|---|
| [`annotation`](./annotation.md) | `com.mingliqiye.document:annotation` | 格式无关的注解与转换器标记接口 | 无 |
| [`core`](./core.md) | `com.mingliqiye.document:core` | `Node` 树、`NodeType`、`DocumentTypeReference` | `exception` |
| [`exception`](./exception.md) | `com.mingliqiye.document:exception` | `DocumentException` / `DocumentTypeException` | `com.mingliqiye.exception:mingli-exception` |
| [`schema`](./schema.md) | `com.mingliqiye.document:schema` | 格式无关的 `DocumentSchema` / `SchemaField` / `SchemaType` | `core` |

### 映射模块

| 模块 | Maven 坐标 | 职责 | 公开依赖 |
|---|---|---|---|
| [`serialization`](./serialization.md) | `com.mingliqiye.document:serialization` | `DocumentMapper`、Bean 映射、Token、配置、合并、`$ref` | `core`、`exception`、`annotation`、`alias-for` |
| [`serialization-kotlin`](./serialization-kotlin.md) | `com.mingliqiye.document:serialization-kotlin` | Kotlin data class / value class / sealed class 支持 | `serialization`（`kotlin-reflect` 为实现依赖） |

### 工具模块

| 模块 | Maven 坐标 | 职责 | 公开依赖 |
|---|---|---|---|
| [`codegen`](./codegen.md) | `com.mingliqiye.document:codegen` | KSP 编译期代码生成（枚举转换器 + `DocumentModule`） | KSP `symbol-processing-api`（实现依赖） |
| [`openapi`](./openapi.md) | `com.mingliqiye.document:openapi` | 由 `DocumentSchema` 生成 OpenAPI 3.0 文档 | `schema`、`core`、`exception` |
| [`bom`](./bom.md) | `com.mingliqiye.document:mingli-document-bom` | Maven BOM，统一依赖版本 | 无（`java-platform`） |

### 扩展模块

| 模块 | Maven 坐标 | 职责 | 公开依赖 |
|---|---|---|---|
| [`schema-registry`](./schema-registry.md) | `com.mingliqiye.document:schema-registry` | Schema 注册、版本、兼容性策略、远程 SPI | `schema`、`core`、`exception` |
| [`validation`](./validation.md) | `com.mingliqiye.document:validation` | Jakarta Bean Validation 集成 | `serialization`、`jakarta.validation-api` |
| [`jdbc`](./jdbc.md) | `com.mingliqiye.document:jdbc` | JDBC ResultSet / PreparedStatement 集成 | `core`、`serialization` |
| [`jpa`](./jpa.md) | `com.mingliqiye.document:jpa` | JPA / Hibernate 实体注解映射 | `core`、`serialization`、`jakarta.persistence-api` |

### 格式模块

`format-*` 模块属于另一个 group：`com.mingliqiye.document.format`，artifactId 去掉
`format-` 前缀。

| 源码模块 | Maven artifactId | `formatName` |
|---|---|---|
| `format-json` | `json` | `json` |
| `format-yaml` | `yaml` | `yaml` |
| `format-xml` | `xml` | `xml` |
| `format-properties` | `properties` | `properties` |
| `format-toml` | `toml` | `toml` |
| `format-csv` | `csv` | `csv` |
| `format-ion` | `ion` | `ion` |
| `format-cbor` | `cbor` | `cbor` |
| `format-smile` | `smile` | `smile` |
| `format-avro` | `avro` | `avro` |
| `format-protobuf` | `protobuf` | `protobuf` |

> 各格式的读写能力、配置与限制见站点「格式」章节。本「模块」章节只讲库的模块划分。

### 不参与发布的模块

| 模块 | 说明 |
|---|---|
| `benchmarks` | JMH 性能基准，不在 BOM 中，不发布 |
| `fuzz` | 解析器 fuzz 语料与 harness，不在 BOM 中，不发布 |
| `codegen-test` | 应用 KSP 的集成测试工程，用于验证生成的转换器/模块，不对外提供能力 |

`codegen` 与 `openapi` 虽然 README 的项目结构表把它们列为工具模块，但在
`bom/build.gradle.kts` 的 `constraints` 中均被纳入版本管理。

## 依赖方向

依赖是单向的，不存在环：

```text
exception
   ▲
   │ api
 core ◄──────────────┐
   ▲                 │
   │ api             │ api
 schema          serialization ◄── serialization-kotlin (api)
   ▲                 ▲   ▲
   │ api             │   │ api
 openapi             │   └── annotation (api)
                     │
 format-*  ──────────┘   （api: core、exception、serialization）
```

关键点：

- `annotation` 不依赖任何模块，只有注解与标记接口；`serialization` 通过 `api` 暴露它。
- `exception` 依赖外部的 `com.mingliqiye.exception:mingli-exception:1.1.1-Final`，
  所有库内异常最终都继承该体系。
- `serialization` 是唯一包含「实体映射」逻辑的模块，`format-*` 只做格式编解码。
- `schema` 只依赖 `core`，`openapi` 依赖 `schema` + `core` + `exception`。
- `codegen` 主代码只依赖 KSP API，不依赖本库其他模块（处理器按全限定名扫描
  `@DocumentEnumCodec`，生成代码引用 `annotation` / `serialization` / `core`）。

## 我该引入哪些模块

| 使用场景 | 需要的依赖 |
|---|---|
| 只手工构造 / 遍历文档树 | `core` |
| 解析 JSON 到 `Node` 并再用 Bean API | `format-json`（传递引入 `core`、`serialization`、`annotation`、`exception`） |
| Java Bean 与任意格式互转 | 对应 `format-*` 模块 |
| Kotlin data class / value class / sealed class | 额外加 `serialization-kotlin` |
| 编译期生成枚举转换器 | 额外加 `codegen` 并应用 KSP 插件 |
| 由 Schema 生成 OpenAPI 3.0 | `openapi`（`schema` 传递引入） |
| 统一管理所有依赖版本 | 导入 `mingli-document-bom` |

各格式 Mapper 的公共父类 `DocumentMapper` 位于 `serialization`，所以通常**只声明一个
格式模块**即可，其余依赖由 Gradle/Maven 传递。

## 坐标与版本

Maven 私服：

```kotlin
repositories {
    maven {
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    implementation("com.mingliqiye.document.format:json:2.3.0")
    implementation("com.mingliqiye.document:serialization-kotlin:2.3.0")
}
```

使用 BOM 时所有本库模块不写版本：

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.3.0"))

    implementation("com.mingliqiye.document:core")
    implementation("com.mingliqiye.document:serialization")
    implementation("com.mingliqiye.document.format:json")
    implementation("com.mingliqiye.document.format:yaml")
}
```

## 构建与发布（源码视角）

所有子模块统一由根 `build.gradle.kts` 配置：

- `group`：普通模块 `com.mingliqiye.document`，`format-*` 为
  `com.mingliqiye.document.format`；
- `version`：统一取 `projectVersion`；
- `artifactId`：普通模块用模块名，`format-*` 去掉前缀；
- 所有模块开启 `explicitApi()`、`jvmToolchain(8)`、Dokka、GPG 签名、ktlint；
- jar 内附带 `LICENSE` / `NOTICE`，并生成 javadoc、kotlindoc 附属 jar；
- `publishToMavenRepository` 按仓库名与 `-SNAPSHOT` 后缀过滤，Gradle Plugin 专用仓库
  永不推送。

常用命令：

```bash
gradle clean ktlintCheck build      # 全量检查、测试、构建
gradle :serialization:test          # 单模块测试
gradle publishToMavenLocal          # 本地仓库
gradle generateChecksums generateSbom
```

## 文档结构

- 本「模块」章节按模块逐个说明：职责、依赖、公开 API、配置、示例、扩展点、限制、坐标。
- 「参考」章节提供跨模块索引：[注解](../reference/annotations.md)、
  [API](../reference/api.md)、[异常](../reference/exceptions.md)、
  [Token](../reference/token.md)、[变更记录](../reference/changelog.md)、
  [路线图](../reference/roadmap.md)。
- 「指南」章节讲使用方式，「格式」章节讲每种格式的协议细节。

## 相关入口

- [annotation](./annotation.md)：全部注解与标记接口
- [core](./core.md)：`Node` 树与类型引用
- [exception](./exception.md)：异常基类体系
- [schema](./schema.md)：格式无关 Schema
- [serialization](./serialization.md)：Mapper、Bean 映射、Token、配置
- [serialization-kotlin](./serialization-kotlin.md)：Kotlin 类型支持
- [codegen](./codegen.md)：KSP 编译期代码生成
- [openapi](./openapi.md)：OpenAPI 3.0 生成
- [bom](./bom.md)：版本统一管理
