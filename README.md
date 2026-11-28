# mingli-document

一个格式无关的 Java / Kotlin 文档处理库。

> 完整文档站：<https://minglipro.github.io/mingli-document/>  
> 源码文档：<https://github.com/minglipro/mingli-document/tree/master/docs>

它用统一的文档树（`Node`）作为中间层，在 JSON、YAML、XML、Properties、TOML、CSV、Ion、CBOR、Smile、Avro、Protobuf 之间读写，并支持 Java Bean 与 Kotlin data class 映射。

```text
文本 / 二进制
      ↓
  DocumentMapper
      ↓
     Node 树
      ↓
Java Bean / Kotlin data class
```

## 特性

- **统一模型**：`ObjectNode`、`ArrayNode`、`StringNode`、`NumberNode`、`BooleanNode`、`BinaryNode`、`NullNode`
- **11 种格式**：JSON/JSONC、YAML、XML、Properties、TOML、CSV、Ion、CBOR、Smile、Avro、Protobuf
- **Bean 映射**：Java Bean、Kotlin data class、泛型集合、Map、嵌套对象
- **注解与转换器**：字段名、忽略字段、日期格式、数字格式、自定义转换器
- **文本 + 二进制 API**：`parse(String)`、`format`、`parse(ByteArray)`、`formatBytes`
- **Token API**：流式 Token 读取/写出，二进制格式直读 Bean
- **Schema 层**：CSV、Avro、Protobuf Schema
- **安全边界**：JSON 深度限制、XML XXE 防护、YAML SafeConstructor
- **开发者工具**：JMH 性能基准、fuzz 边界语料

## 环境要求

- JDK 8+
- Kotlin 2.0（使用 `serialization-kotlin` 时）
- Gradle 8.x（构建源码时）

## 安装

Maven 坐标：

- 普通模块：`com.mingliqiye.document:<module>`
- 格式模块：`com.mingliqiye.document.format:<格式名>`（如 `json`、`yaml`）

版本来自项目根 `gradle.properties` 的 `projectVersion`。

```kotlin
repositories {
    maven {
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    implementation("com.mingliqiye.document.format:json:2.1.0")

    // 需要 Kotlin data class 时
    implementation("com.mingliqiye.document:serialization-kotlin:2.1.0")
}
```

常用格式模块（artifactId）：

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

### 使用 BOM 统一版本

不想为每个模块写版本时，可以导入 BOM：

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.1.0"))

    implementation("com.mingliqiye.document:core")
    implementation("com.mingliqiye.document:serialization")
    implementation("com.mingliqiye.document.format:json")
    implementation("com.mingliqiye.document.format:yaml")
}
```

Maven：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.mingliqiye.document</groupId>
            <artifactId>mingli-document-bom</artifactId>
            <version>2.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 快速开始

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

class User {
    var name: String = ""
    var age: Int = 0
}

fun main() {
    val mapper = JsonMapper()

    val user = User().apply {
        name = "小于"
        age = 18
    }

    // 实体 -> 文档树
    val node = mapper.toNode(user)
    println(node.asObject()["name"]?.asString()) // 小于

    // 文档树 -> 实体
    val restored = mapper.fromNode(node, User::class.java)
    println("${restored.name}:${restored.age}") // 小于:18

    // 文本 -> 实体
    val parsed = mapper.parse("""{"name":"小于","age":18}""", User::class.java)
    println(parsed.age) // 18

    // 实体 -> 文本
    println(mapper.format(parsed))
    println(mapper.formatPretty(parsed))
}
```

### 换一种格式

格式 Mapper 的 API 保持一致：

```kotlin
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.csv.CsvMapper

val yaml = YamlMapper()
val xml = XmlMapper()
val csv = CsvMapper()

val yamlText = yaml.format(user)
val xmlText = xml.format(user)
```

### Kotlin data class

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class User(val name: String, val age: Int)

fun main() {
    val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()

    val json = mapper.format(User("小于", 18))
    val user = mapper.parse(json, User::class.java)

    println(user)
}
```

## 支持的格式

| 格式 | 读取 | 写出 | 说明 |
|---|---|---|---|
| JSON / JSONC | ✅ | ✅ | 支持注释、严格 UTF-8 byte API、重复 key 策略、深度限制 |
| YAML | ✅ | ✅ | 多文档、`!!binary`、Token 路径 |
| XML | ✅ | ✅ | `type`/`nil` 协议、属性、PREFIX/CLARK 命名空间、混合内容策略 |
| Properties | ✅ | ✅ | 扁平键值、`.` 路径、`[index]` 数组 |
| TOML | ✅ | ✅ | 标准表/数组，日期时间映射为字符串 |
| CSV | ✅ | ✅ | 平坦表、表头、类型推断、CSV Schema |
| Ion | ✅ | ✅ | 文本 + 二进制，BLOB/CLOB -> `BinaryNode` |
| CBOR | ✅ | ✅ | 二进制、Token 路径 |
| Smile | ✅ | ✅ | 二进制、Token 路径 |
| Avro | ✅ | ✅ | Schema + wire，OCF，schema evolution |
| Protobuf | ✅ | ✅ | Descriptor + DynamicMessage，嵌套/enum/map/oneof |

### 二进制格式

```kotlin
import com.mingliqiye.document.format.cbor.CborMapper

val cbor = CborMapper()
val bytes = cbor.formatBytes(user)
val restored = cbor.parse(bytes, User::class.java)
```

Avro/Protobuf 需要显式提供 Schema：

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper
import com.mingliqiye.document.format.protobuf.ProtobufMapper

val avro = AvroMapper(avroSchema)
val avroBytes = avro.formatBytes(value)

val protobuf = ProtobufMapper(descriptor)
val protobufBytes = protobuf.formatBytes(value)
```

## Node 与 Mapper API

### Node 类型

```text
ObjectNode
ArrayNode
StringNode
NumberNode
BooleanNode
BinaryNode
NullNode
```

`Node` 不可变；`BinaryNode` 会在输入/输出时复制字节数组。

### DocumentMapper 主要方法

| 方法 | 作用 |
|---|---|
| `toNode(value)` | 实体/集合/Map/数组 -> Node |
| `fromNode(node, Class)` / `fromNode(node, DocumentTypeReference)` | Node -> 实体 |
| `parse(source, Class)` / `parse(source, DocumentTypeReference)` | 文本 -> 实体 |
| `parse(bytes, Class)` / `parse(bytes, DocumentTypeReference)` | 二进制 -> 实体 |
| `format(value)` / `formatPretty(value)` | 实体 -> 文本 |
| `formatBytes(value)` | 实体 -> 二进制 |
| `parse(file, Class)` / `parse(path, Class)` | 文件/路径 -> 实体 |
| `parse(inputStream, Class)` / `parse(reader, Class)` | 流 -> 实体 |
| `format(value, file)` / `format(value, outputStream)` | 实体 -> 文件/流 |
| `fromTokens(reader, Class)` / `fromTokens(reader, DocumentTypeReference)` | Token -> 实体 |
| `convert(source, Class)` | 实体/Node -> 实体 |

## 文件与流

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val mapper = JsonMapper()
val user = User().apply {
    name = "小于"
    age = 18
}

// 文件
mapper.format(user, File("user.json"))
val fromFile = mapper.parse(File("user.json"), User::class.java)

// 路径
mapper.format(user, Paths.get("user.json"))
val fromPath = mapper.parse(Paths.get("user.json"), User::class.java)

// 字节流：文本格式使用 UTF-8
val output = ByteArrayOutputStream()
mapper.format(user, output)
val fromStream = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)

// 字符流
val writer = StringWriter()
mapper.format(user, writer)
val fromReader = mapper.parse(StringReader(writer.toString()), User::class.java)
```

二进制格式使用同样的 `File` / `Path` / `InputStream` / `OutputStream` 方法，内部自动走 `formatBytes` / `parse(ByteArray)`。

说明：

- 文本格式文件/流默认使用 UTF-8，可通过 `DocumentMapperConfig.fileCharset(...)` 修改
- 文件 API 会自己打开和关闭文件
- 调用方传入的 `InputStream` / `OutputStream` / `Reader` / `Writer` 不会被关闭
- 二进制格式不支持 `Reader` / `Writer`，会抛 `DocumentSerializationException`
- `atomicFileWrite(true)` 使用临时文件 + 原子替换
- `createParentDirectories(true)` 自动创建父目录
- `maxInputSize(n)` 限制单次输入最大字节数

## 注解

注解位于 `com.mingliqiye.document.annotation`。

| 注解 | 作用 |
|---|---|
| `@DocumentName("name")` | 指定文档字段名 |
| `@DocumentIgnore` | 忽略字段 |
| `@DocumentConverter(XxxConverter::class)` | 字段专用双向转换器 |
| `@DocumentSerializer(XxxSerializer::class)` | 字段专用序列化器 |
| `@DocumentDeserializer(XxxDeserializer::class)` | 字段专用反序列化器 |
| `@DocumentTypeInfo(property)` | 多态基类类型属性 |
| `@DocumentTypeName("name")` | 多态子类型名 |
| `@DocumentCreator` | 反序列化构造器 |
| `@DocumentBuilder` | Builder 静态工厂 |
| `@DocumentBuild` | Builder build 方法 |
| `@DocumentDateTimeFormat("yyyy-MM-dd")` | 日期/时间格式 |
| `@DocumentNumberFormat("#,##0.00")` | 数字格式 |
| `@DocumentRootName("user")` | XML 根元素名 |

示例：

```kotlin
class Profile {
    @field:DocumentName("display_name")
    var name: String = ""

    @field:DocumentIgnore
    var age: Int = 0
}
```

## 配置

通用 Mapper 配置：

```kotlin
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.DocumentMapperConfig

val config = DocumentMapperConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.FAIL)
    .failOnUnknownFields(true)
    .writeNulls(false)
    .build()
```

JSON 配置：

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper(
    JsonConfig.builder()
        .allowComments(true)
        .pretty(true)
        .duplicateKeyPolicy(DuplicateKeyPolicy.FAIL)
        .maxDepth(512)
        .build(),
)
```

XML 属性与命名空间：

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlNamespaceMode

val mapper = XmlMapper(
    XmlConfig.builder()
        .attributeMode(true)
        .namespaceMode(XmlNamespaceMode.CLARK)
        .build(),
)
```

CSV Schema：

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvSchema
import com.mingliqiye.document.schema.SchemaType

val schema = CsvSchema.builder("user")
    .field("name", SchemaType.STRING)
    .field("age", SchemaType.INT)
    .build()

val mapper = CsvMapper(schema)
```

## 进阶能力

### 自定义转换器

实现 `DocumentBaseSerializer` / `DocumentBaseDeserializer`，通过 Builder 注册或字段注解使用。

### Token 直读

二进制格式可以通过 `DocumentTokenReader` 直接解码 Bean，不构建完整 Node 树。

### Schema 层

- `schema` 模块提供 `DocumentSchema`、`SchemaField`、`SchemaType`
- `format-csv` 提供 `CsvSchema`
- `format-avro` 提供 `AvroSchema`
- `format-protobuf` 提供 `ProtobufSchema` / `Descriptor`

### 多文档 YAML

```kotlin
val nodes = YamlMapper().parseAllToNodes(text)
val text = YamlMapper().formatAll(nodes)
```

### XML 安全

- 禁用 DOCTYPE
- 禁用外部实体
- 禁用 XInclude
- 支持属性、命名空间和混合内容策略

## 性能

JMH 基准位于 `benchmarks` 模块：

```bash
gradle :benchmarks:jmh
```

当前 JSON/CBOR 的一组结果见 [benchmarks/README.md](benchmarks/README.md)。

## 项目结构

| 模块 | 职责 |
|---|---|
| `core` | Node 树、类型引用 |
| `annotation` | 格式无关注解 |
| `exception` | 异常基类 |
| `serialization` | DocumentMapper、Bean 映射、Token |
| `serialization-kotlin` | Kotlin data class 支持 |
| `codegen` | KSP 编译期代码生成（不参与常规依赖） |
| `openapi` | OpenAPI 3.0 文档生成 |
| `schema` | 格式无关 Schema |
| `schema-registry` | Schema Registry 适配 |
| `validation` | Jakarta Bean Validation 集成 |
| `format-*` | 各格式 Mapper |
| `bom` | Maven BOM：`mingli-document-bom` |
| `benchmarks` | JMH 性能基准（不发布） |
| `fuzz` | 解析器模糊测试（不发布） |

各模块的坐标、API 与示例见模块 README：

[annotation](annotation/README.md) ·
[exception](exception/README.md) ·
[core](core/README.md) ·
[schema](schema/README.md) ·
[schema-registry](schema-registry/README.md) ·
[validation](validation/README.md) ·
[serialization](serialization/README.md) ·
[serialization-kotlin](serialization-kotlin/README.md) ·
[codegen](codegen/README.md) ·
[openapi](openapi/README.md) ·
[json](format-json/README.md) ·
[yaml](format-yaml/README.md) ·
[xml](format-xml/README.md) ·
[properties](format-properties/README.md) ·
[toml](format-toml/README.md) ·
[csv](format-csv/README.md) ·
[ion](format-ion/README.md) ·
[cbor](format-cbor/README.md) ·
[smile](format-smile/README.md) ·
[avro](format-avro/README.md) ·
[protobuf](format-protobuf/README.md) ·
[bom](bom/README.md) ·
[benchmarks](benchmarks/README.md) ·
[fuzz](fuzz/README.md)

## 构建与测试

```bash
# 全量检查与构建（含测试、ktlint、Dokka）
gradle clean ktlintCheck build

# 单模块测试
gradle :format-json:test
gradle :serialization:test
gradle :fuzz:test

# 代码格式
gradle ktlintFormat
```

更详细的开发规范、提交约定和发布流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。安全边界见 [SECURITY.md](SECURITY.md)。

## 发布

```bash
# 本地
gradle publishToMavenLocal

# 远程：仓库地址与凭据由 Gradle init.gradle 提供
gradle publish

# Maven Central（需要环境变量与 GPG）
# MAVEN_CENTRAL_USERNAME / MAVEN_CENTRAL_PASSWORD
gradle publish
```

发布规则：

- `-SNAPSHOT` 版本只推送 snapshot 仓库
- 正式版本只推送 release 仓库
- Gradle Plugin 专用仓库不参与本库发布
- jar、pom、module metadata、javadoc、kotlindoc 均带 GPG `.asc` 签名

## 兼容性说明

- 目标字节码：JDK 8
- 文本 Token Reader：YAML/XML/Properties/TOML/CSV 通过 `NodeTokenReader` 适配
- CSV 只支持平坦表格
- TOML 日期时间映射为字符串
- Ion timestamp/symbol 映射为字符串，注解忽略
- Protobuf 不支持 extensions/group/MessageSet
- XML 混合内容不保留文本与子元素的相对顺序
- Avro OCF 支持默认 codec；不支持 `appendTo` 续写

## 许可证

Apache License, Version 2.0。

第三方依赖与许可证见 [NOTICE](NOTICE)，项目许可见 [LICENSE](LICENSE)。

## 变更记录

- [CHANGELOG.md](CHANGELOG.md)
- [1.0.0 -> 2.1.0 迁移指南](MIGRATION.md)

## 贡献

欢迎提交 Issue 和 Pull Request。

- 开发规范：[CONTRIBUTING.md](CONTRIBUTING.md)
- 待办与路线图：[TODO.md](TODO.md)
- 性能数据：[benchmarks/README.md](benchmarks/README.md)
