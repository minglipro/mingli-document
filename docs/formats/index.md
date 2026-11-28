---
title: 格式总览
description: mingli-document 支持的 11 种格式：能力矩阵、坐标、通用读写 API 与选型建议
---

# 格式总览

`mingli-document` 用统一的文档树（[`Node`](../reference/api.md)）作为中间层，把 11 种格式接进同一套 Mapper / Bean 映射体系：

```text
文本 / 二进制
      ↓
   DocumentMapper
      ↓
     Node 树（ObjectNode / ArrayNode / 标量节点）
      ↓
Java Bean / Kotlin data class
```

每个格式模块只负责“格式字节 ↔ `Node` 树”这一层；Bean 映射、注解、转换器、文件/流选项全部复用 `serialization` 模块的通用能力。

## 能力矩阵

| 格式 | 模块 artifact | 文本 | 二进制 | 专属配置类 | 原生 Token 路径 | 多值/多消息 | 第三方依赖 |
|---|---|---|---|---|---|---|---|
| JSON / JSONC | `com.mingliqiye.document.format:json` | ✅ | 字节 API（UTF-8） | `JsonConfig` | ✅ 读取、写出 | — | Jackson `jackson-core 2.15.3` |
| YAML | `com.mingliqiye.document.format:yaml` | ✅ | — | `YamlConfig` | ✅ 读取 | 多文档 `---` | SnakeYAML `2.6` |
| XML | `com.mingliqiye.document.format:xml` | ✅ | — | `XmlConfig` | ✅ 默认配置读取 | — | JDK DOM |
| Properties | `com.mingliqiye.document.format:properties` | ✅ | — | 无（用通用配置） | ✅ 读取 | — | 无 |
| TOML | `com.mingliqiye.document.format:toml` | ✅ | — | 无（用通用配置） | ✅ 读取 | — | tomlj `1.0.0` |
| CSV | `com.mingliqiye.document.format:csv` | ✅ | — | `CsvConfig` | ✅ 读取 | 多行表格 | commons-csv `1.10.0` |
| Ion | `com.mingliqiye.document.format:ion` | ✅ | ✅ | `IonConfig` | ✅ 读取、直写 | — | ion-java `1.11.9` |
| CBOR | `com.mingliqiye.document.format:cbor` | — | ✅ | `CborConfig` | ✅ 读取、直写 | CBOR sequence | jackson-dataformat-cbor `2.15.3` |
| Smile | `com.mingliqiye.document.format:smile` | — | ✅ | 无（用通用配置） | ✅ 读取、直写 | 多消息流 | jackson-dataformat-smile `2.15.3` |
| Avro | `com.mingliqiye.document.format:avro` | — | ✅ | 无（用通用配置） | —（Schema 驱动） | OCF 容器 | Apache Avro `1.11.3`、snappy-java |
| Protobuf | `com.mingliqiye.document.format:protobuf` | — | ✅ | 无（构造参数） | —（Descriptor 驱动） | MessageSet | protobuf-java `3.25.5` |

## Maven 坐标

格式模块统一使用 `com.mingliqiye.document.format` 分组，artifactId 去掉 `format-` 前缀：

```kotlin
repositories {
    maven {
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    implementation("com.mingliqiye.document.format:json:2.3.0")
    implementation("com.mingliqiye.document.format:yaml:2.3.0")
}
```

Maven：

```xml
<dependency>
    <groupId>com.mingliqiye.document.format</groupId>
    <artifactId>json</artifactId>
    <version>2.3.0</version>
</dependency>
```

每个格式模块都传递依赖 `core`、`exception`、`serialization`（CSV / Avro / Protobuf 还传递依赖 `schema`，XML 额外传递 `annotation`），所以通常不需要单独声明这些模块。

使用 BOM 统一版本：

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.3.0"))
    implementation("com.mingliqiye.document.format:json")
    implementation("com.mingliqiye.document.format:cbor")
}
```

环境要求：

- 目标字节码 JDK 8（`jvmToolchain(8)`）
- Kotlin 2.0（仅使用 `serialization-kotlin` 的 Kotlin data class 支持时必要）

## 两种性质不同的 Mapper

虽然所有 Mapper 都实现同一套 `DocumentMapperApi`，但内部语义分成两类：

### 文本格式 Mapper

JSON、YAML、XML、Properties、TOML、CSV 继承 `DocumentMapper`：

- `parse(String)` / `format(...)` 是主路径；
- `parse(ByteArray)` / `formatBytes(...)` 也会工作：按 `fileCharset`（默认 UTF-8）解码/编码文本（JSON 的 `parse(ByteArray)` 是例外，使用严格 UTF-8 解码）；
- `Reader` / `Writer` 字符流 API 可用；
- `pretty` 通过格式自身的配置或 `formatPretty` 控制。

### 二进制格式 Mapper

CBOR、Smile、Avro、Protobuf 继承 `BinaryDocumentMapper`：

- `parse(ByteArray)` / `formatBytes(...)` 是主路径；
- `File` / `Path` / `InputStream` / `OutputStream` 自动按字节语义工作；
- `parse(String)` / `format(...)` / `formatPretty` / `Reader` / `Writer` 会抛
  `DocumentSerializationException`（不支持）。

Ion 是特例：它同时支持文本与二进制，但 Mapper 的字符串/文件/流入口默认按“文本 Ion”工作，二进制读写走 `parse(ByteArray)` / `formatBytes(...)`。详见 [Ion](./ion.md)。

## 通用读取 API

`DocumentMapper`（`serialization` 模块）为所有格式提供同一组入口：

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType: Class<T>)` | 文本 → 实体 |
| `parse(source: String, targetType: DocumentTypeReference<T>)` | 文本 → 泛型实体（`List<User>`、`Map<String, User>` 等） |
| `parse(source: String, Node::class.java)` | 文本 → `Node` 树（任意 `Node` 子类型也可作为目标） |
| `parse(source: ByteArray, targetType)` | 字节 → 实体（文本格式按 `fileCharset` 解码，二进制格式直接解析） |
| `parse(file: File / path: Path, targetType)` | 文件 → 实体；文件由 Mapper 打开和关闭 |
| `parse(input: InputStream, targetType)` | 流 → 实体；调用方负责关闭流 |
| `parse(reader: Reader, targetType)` | 字符流 → 实体；仅文本格式支持 |
| `fromTokens(reader: DocumentTokenReader, targetType)` | 从 Token 流直接反序列化（供自定义 Token 实现使用） |
| `fromNode(node: Node, targetType)` | 树 → 实体 |
| `toNode(value: Any?)` | 实体 / Map / 集合 / `Node` → 树 |
| `convert(source, destinationClass)` | 实体或树 → 另一种类型 |

泛型目标类型用 `DocumentTypeReference`：

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper()
val users = mapper.parse(json, DocumentTypeReference.listType(User::class.java))
val userMap = mapper.parse(
    json,
    DocumentTypeReference.mapType(String::class.java, User::class.java),
)
```

## 通用写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → 文本（是否 pretty 由配置决定） |
| `formatPretty(value)` | 实体 → pretty 文本 |
| `formatBytes(value)` | 实体 → 字节（文本格式按 `fileCharset` 编码，二进制格式直接序列化） |
| `format(value, file: File / path: Path)` | 写文件（非 pretty 默认样式） |
| `formatPretty(value, file / path)` | 写 pretty 文件 |
| `format(value, output: OutputStream)` | 写输出流；调用方负责关闭 |
| `formatPretty(value, output: OutputStream)` | 写 pretty 输出流 |
| `format(value, writer: Writer)` | 写字符流；仅文本格式支持 |
| `formatPretty(value, writer: Writer)` | 写 pretty 字符流；仅文本格式支持 |
| `writeTo(value, writer: DocumentValueWriter)` | 流式写出到自定义 `DocumentValueWriter` / Token 写出器 |

文件与流语义（所有格式一致）：

- 文件 API 由 Mapper 自己打开和关闭文件；
- 调用方传入的 `InputStream` / `OutputStream` / `Reader` / `Writer` 不会被关闭，Mapper 只 flush；
- `fileCharset(...)` 只影响文本格式的文件/流编解码；
- `atomicFileWrite(true)`：临时文件 + 原子替换；
- `createParentDirectories(true)`：写文件时自动创建父目录；
- `maxInputSize(n)`：限制单次输入字节数，超过抛 `DocumentSerializationException`。

## 通用配置（`DocumentMapperConfig`）

没有专属配置类的格式，或者需要通用选项时，用 `DocumentMapperConfig.builder()`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `missingFieldPolicy` | `KEEP_DEFAULT` | 缺少 Bean 字段时：保留默认值 / 置 null / 置空 / 抛异常 |
| `failOnUnknownFields` | `false` | 文档中出现 Bean 未声明字段时是否抛异常 |
| `writeNulls` | `true` | Bean 字段为 null 时是否写出 |
| `namingStrategy` | `AS_IS` | 未标注 `@DocumentName` 的字段命名策略（SNAKE_CASE 等） |
| `fileCharset` | UTF-8 | 文本文件/流编解码字符集 |
| `atomicFileWrite` | `false` | 写文件是否原子替换 |
| `createParentDirectories` | `false` | 写文件是否自动创建父目录 |
| `maxInputSize` | `0` | 单次输入最大字节数，0 = 不限制 |
| `maxStringLength` | `0` | Node 树中单个字符串最大长度，0 = 不限制 |
| `maxNodeDepth` | `0` | Node 树最大深度，0 = 不限制 |
| `maxNodeCount` | `0` | Node 树最大节点数，0 = 不限制 |
| `resolveReferences` | `false` | 反序列化前是否解析本地 `$ref` |
| `writeReferences` | `false` | 序列化时是否按对象身份写出本地 `$ref` |
| `remoteReferenceResolver` | `null` | 可选远程 `$ref` 解析器 |

各格式模块的专属配置类（`JsonConfig`、`YamlConfig`、`XmlConfig`、`CsvConfig`、`IonConfig`、`CborConfig`）都继承 `DocumentMapperConfig`，可以直接传给 Mapper 构造器；没有专属配置类的格式直接把 `DocumentMapperConfig` 传给构造器。

## 同一份数据在格式间转换

因为所有格式共享 `Node` 树，格式间转换 = “格式 A 解析成树 → 树写成格式 B”：

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.yaml.YamlMapper

val json = JsonMapper()
val yaml = YamlMapper()

val node = json.parse("""{"name":"小于","age":18}""", com.mingliqiye.document.core.Node::class.java)
val yamlText = yaml.format(node)
```

注意各格式的表达能力不同（例如 CSV 根节点固定为数组、XML 需要根元素、TOML 没有 null），跨格式转换要对目标格式的限制有预期，详见各格式页的“边界与限制”。

## 二进制格式专项速览

| 能力 | 格式 | 入口 |
|---|---|---|
| 多值 sequence | CBOR（RFC 8742） | `CborMapper.parseAll` / `formatAll`、`CborSequenceReader` / `CborSequenceWriter` |
| 多消息流 | Smile | `SmileMapper.parseAll` / `formatAll`、`SmileSequenceReader` / `SmileSequenceWriter` |
| tag 保真 | CBOR | `CborConfig.preserveTags`、`{"$cborTag": tag, "value": node}` |
| 容器文件（OCF） | Avro | `formatContainerBytes` / `parseContainer*` / `appendContainer*` |
| Schema 演进 | Avro | `AvroMapper(writerSchema, readerSchema)` |
| Schema → Descriptor | Protobuf | `ProtobufSchema.toDescriptor()` |
| extension / group / MessageSet | Protobuf | `ProtobufExtensionRegistry`、`ProtobufType.GROUP`、`messageSetWireFormat` |
| 未知字段保留 | Protobuf | `ProtobufUnknownFieldPolicy.PRESERVE` + `$unknownFields` |
| 类型保真 | Ion | `IonConfig.preserveTypes` |

## 各格式页面

| 页面 | 内容摘要 |
|---|---|
| [JSON](./json.md) | JSONC、严格 UTF-8 字节 API、重复 key 策略、深度限制、JSON Schema 校验 |
| [YAML](./yaml.md) | 多文档、`!!binary`、自定义 tag、alias 限制、原生 Token 读取 |
| [XML](./xml.md) | `type`/`nil` 协议、属性映射、PREFIX/CLARK 命名空间、混合内容策略、XXE 防护 |
| [Properties](./properties.md) | 扁平 key、`.` 路径、`[index]` 数组、百分号转义 |
| [TOML](./toml.md) | 表/数组、内联表写出、日期时间字符串化、无 null |
| [CSV](./csv.md) | 表头/无表头、类型推断、CSV Schema、嵌套 JSON 单元格/点分列名 |
| [Ion](./ion.md) | 文本/二进制 Ion、BLOB/CLOB、类型保真、深度限制 |
| [CBOR](./cbor.md) | 二进制、canonical 编码、tag 保真、sequence 多值读写 |
| [Smile](./smile.md) | 二进制、embedded binary、多消息流 |
| [Avro](./avro.md) | Schema、wire、OCF、codec、schema evolution、append |
| [Protobuf](./protobuf.md) | Descriptor/DynamicMessage、Schema builder、enum/map/oneof、extension、group、MessageSet、未知字段策略 |

## 相关链接

- [快速开始](../guide/getting-started.md)
- [安装与坐标](../guide/installation.md)
- [核心概念](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [配置](../guide/configuration.md)
- [Token 与流式读写](../guide/token-stream.md)
- [API 参考](../reference/api.md)
- [根 README（GitHub）](https://github.com/minglipro/mingli-document/blob/dev/README.md)
