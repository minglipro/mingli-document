---
title: TOML
description: format-toml 模块：TomlMapper、表/数组读写、内联表写出、TomlConfig 配置对齐 参考实现 TOML、日期时间处理与 TOML 边界
---

# TOML

`format-toml` 提供 TOML 读写，核心类型是 `TomlMapper`。

TOML 文档根必须是表（table），因此 TOML 的根节点固定为 `ObjectNode`；日期时间类型在 Node 模型中没有对应节点，默认读取为 TOML 源码原文，`TomlReadFeature.PARSE_JAVA_TIME` 开启后读取为 `java.time` 规范化 ISO-8601 字符串。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:toml:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `toml` |
| 版本 | `2.5.1` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `org.tomlj:tomlj:1.0.0` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.toml` |

## 特性

- **标准 TOML 表与数组**：读取标准表、数组表、内联表、内联数组；
- **嵌套表**：任意层级表结构映射为嵌套 `ObjectNode`；
- **内联写出**：写出统一使用内联 table / 内联数组（`user = { name = "小于", age = 18 }`）；
- **日期时间字符串化**：默认读取为 TOML 源码原文；`TomlReadFeature.PARSE_JAVA_TIME` 开启后读取为 `java.time` 规范化 ISO-8601 字符串；
- **原生 Token 读取**：`parse(String)` 通过内部 `TomlTokenReader` 直接遍历 tomlj 结构生成 Token 流；
- **错误定位**：解析错误信息包含行列号；
- **文件/流读写**：文本 UTF-8（可改 `fileCharset`）；
- **配置对齐 参考实现 TOML**：`TomlConfig` / `TomlReadFeature` / `TomlWriteFeature`，见下方配置矩阵。

## 配置矩阵（对齐 TOML 底层库）

TOML 模块提供专属 `TomlConfig`（继承 `DocumentMapperConfig`），配置项与默认值对齐
TOML 底层库（2.15 / 3.x）。使用 `TomlConfig.builder()` 或
`TomlMapper.builder()` 构建。

### TOML 专属配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `maxDepth` | `512` | 解析最大嵌套深度（根表深度为 `1`）；`TomlMapper.builder().maxDepth(...)` 等价 |
| `readFeatures` | 空（全部关闭） | 见下方 `TomlReadFeature` |
| `writeFeatures` | 空（全部关闭） | 见下方 `TomlWriteFeature` |
| `commentConfig` | `TomlCommentConfig()` | 注释保留写出配置（`orphanCommentPolicy` / `preserveBlankLines` / `trailingNewline`） |

### TomlReadFeature

| 特性 | 参考实现 默认 | 本模块默认 | 行为 |
| --- | --- | --- | --- |
| `PARSE_JAVA_TIME` | 关闭 | 关闭 | 关闭：日期时间读取为源码原文（空格分隔符归一化为 `T`、`z` 归一化为 `Z`）；开启：`java.time` 规范化 ISO-8601 字符串。参考实现 以 `VALUE_EMBEDDED_OBJECT` 返回 `java.time` 对象，Node/Token 模型无 embedded object 类型，属明确替代 |
| `VALIDATE_DATE_TIME`（参考实现 3.2+） | 关闭 | 关闭 | 字符串模式下额外用 `java.time` 校验日期时间，非法值抛 `TomlParseException`；tomlj 本身已严格校验，正常输入结果一致 |

### TomlWriteFeature

| 特性 | 参考实现 默认 | 本模块默认 | 行为 |
| --- | --- | --- | --- |
| `FAIL_ON_NULL_WRITE` | 关闭 | 关闭 | 关闭：`null` 写成 `""`；开启：任何 `null` 写出抛 `TomlException` |

参考实现 TOML 还受 `StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN`（本模块固定 plain 输出，
等价开启）与 `StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER`（由 tomlj 决定）影响，
二者不是 TOML 专属特性，未提供开关。

### 常用组合

```kotlin
import com.mingliqiye.document.format.toml.*
import com.mingliqiye.document.serialization.MissingFieldPolicy

val mapper = TomlMapper.builder()
    .maxDepth(64)
    .enable(TomlReadFeature.PARSE_JAVA_TIME)
    .enable(TomlWriteFeature.FAIL_ON_NULL_WRITE)
    .trailingNewline(false)
    .preserveBlankLines(false)
    .build()

val config = TomlConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.FAIL)
    .maxNodeDepth(32)
    .enable(TomlReadFeature.VALIDATE_DATE_TIME)
    .disable(TomlReadFeature.PARSE_JAVA_TIME)
    .build()
val viaConfig = TomlMapper(config)
```

`TomlConfigBuilder` 转发全部通用 `DocumentMapperConfig` 便捷方法：`field` /
`missingFieldPolicy` / `failOnUnknownFields` / `writeNulls` / `namingStrategy` /
`fileCharset` / `atomicFileWrite` / `createParentDirectories` / `maxInputSize` /
`maxStringLength` / `maxNodeDepth` / `maxNodeCount` / `resolveReferences` /
`writeReferences` / `remoteReferenceResolver` / `beanValidator` / `subtype` /
`typeProperty` / `enable`/`disable`/`configure(DeserializationFeature)` /
`failOnNullForPrimitives`。

### 注释配置合并

`TomlCommentConfigBuilder` 保留并可与主配置合并：

```kotlin
val config = TomlConfig.builder()
    .orphanCommentPolicy(TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_FILE)
    .preserveBlankLines(false)
    .build()
val commentConfig = TomlCommentConfig.from(config)
val merged = TomlCommentConfig.builder().base(commentConfig).trailingNewline(true).build()
```

`TomlMapper.formatCommented(document)` 默认使用 Mapper 的 `TomlConfig.commentConfig` 与
`writeFeatures`。

### null 行为

- 默认：`null` 写成 `""`（对象字段、内联 table 字段、数组元素均适用）；
- `FAIL_ON_NULL_WRITE` 开启：任何 `null` 写出抛 `TomlException`；
- 需要省略 null 字段：通用配置 `writeNulls(false)`（等价 参考实现 `NON_NULL`）。

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `TomlMapper()` | 默认配置（TOML 默认特性全部关闭、深度 512） |
| `TomlMapper(config: TomlConfig)` | TOML 专属配置 |
| `TomlMapper(config: DocumentMapperConfig)` | 通用配置（TOML 默认特性/深度/注释配置） |
| `TomlMapper(config: DocumentMapperConfig, maxDepth: Int)` | 通用配置 + 显式解析深度 |

```kotlin
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.format.toml.TomlMapper

val mapper = TomlMapper.builder()
    .config(
        DocumentMapperConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.FAIL)
            .build(),
    )
    .build()
```

## 读取 API

### Mapper 入口

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType)` | 默认走原生 `TomlTokenReader`；`maxNodeDepth` / `maxNodeCount` / `maxStringLength` 非零时回退 Node 路径 |
| `parse(source: ByteArray, targetType)` | 按 `fileCharset` 解码后解析 |
| `parse(file / path, targetType)` | 文件 → 实体 |
| `parse(input: InputStream, targetType)` | 流 → 实体 |
| `parse(reader: Reader, targetType)` | 字符流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |

### 独立读取器

```kotlin
import com.mingliqiye.document.format.toml.TomlReader

val node = TomlReader("""
    name = "小于"
    age = 18
""".trimIndent()).read()

// 静态便捷方法
val node2 = TomlReader.parse("name = \"小于\"")

// 扩展函数
val node3 = "name = \"小于\"".toTomlNode()
```

| 类/函数 | 说明 |
|---|---|
| `TomlReader(source)` | `read(): Node`，根固定为 `ObjectNode` |
| `TomlReader.parse(source)` | 静态解析 |
| `String.toTomlNode()` | 字符串 → Node 扩展函数 |

读取规则：

- TOML 文档根必须是表；解析后根节点总是 `ObjectNode`，否则抛 `TomlParseException("TOML document root must be a table")`；
- 解析错误抛 `TomlParseException`，message 形如 `Invalid TOML at line L, column C: ...`；
- `TomlTable` → `ObjectNode`（保持 key 顺序）；
- `TomlArray` → `ArrayNode`；
- `String` → `StringNode`、`Boolean` → `BooleanNode`、`Long` → `NumberNode`、`Double` → `NumberNode`（NaN/Infinity 抛 `TomlParseException`）；
- `OffsetDateTime` / `LocalDateTime` / `LocalDate` / `LocalTime` → `StringNode`：默认是 TOML 源码原文（日期时间分隔符空格归一化为 `T`、小写 `z` 归一化为 `Z`）；开启 `TomlReadFeature.PARSE_JAVA_TIME` 后是 `java.time` 规范化 ISO-8601 字符串。

### Token 路径

`TomlMapper.parse(String)` 使用内部 `TomlTokenReader`：

- 直接遍历 tomlj 的 `TomlTable` / `TomlArray` 生成 Token 流，不构建完整 Node 树；
- 日期时间同样输出为 `STRING` Token：默认源码原文，`PARSE_JAVA_TIME` 开启时为 `java.time` 规范化字符串；
- NaN / Infinity 抛 `TomlParseException`；
- 配置了非零 `maxNodeDepth` / `maxNodeCount` / `maxStringLength` 时回退 `NodeTokenReader`，以保持 Node 树限制语义。

需要自定义 Token 来源时，实现 `DocumentTokenReader` 后调用 `DocumentMapper.fromTokens(...)`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → TOML 文本（内联 table / 内联数组） |
| `formatPretty(value)` | 与 `format` 输出相同（写出实现不区分 pretty） |
| `formatBytes(value)` | 实体 → 按 `fileCharset` 编码的字节 |
| `format(value, file / path)` | 写文件 |
| `format(value, output: OutputStream)` | 先编码为字节再写出（无流式 Token 写出器） |
| `format(value, writer: Writer)` | 直接写字符流 |
| `Node.toTomlString()` | 扩展函数：Node → TOML 文本 |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `format(value, OutputStream)` | ❌ | 先构造完整 `String` 再编码 |
| `format(value, File/Path)` | ❌ | 同上 |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

写出规则：

- 根节点必须是 `ObjectNode`，否则抛 `TomlException("TOML root must be an object but was ...")`；
- 对象写成内联 table：`user = { name = "小于", age = 18 }`；空对象写成 `{}`；
- 数组写成内联数组：`tags = [ "a", "b" ]`；空数组写成 `[]`；
- `null`（`NullNode`）默认写成 `""`（含对象字段、内联 table 字段与数组元素）；
  开启 `TomlWriteFeature.FAIL_ON_NULL_WRITE` 后任何 `null` 写出抛 `TomlException`；
- 需要省略 null 字段时使用通用配置 `writeNulls(false)`（等价 参考实现 `NON_NULL`）；
- 字符串按 TOML 规则转义（`\"`、`\\`、`\n` 等，控制字符 `\uXXXX`）；
- key 只允许 `[A-Za-z0-9_-]+` 裸写，其余用双引号包裹；
- 数字按 `NumberNode.raw` 写出：
  - 匹配 TOML 数字字面量且在 64 位有符号整数范围内 → 原样；
  - 其它情况转成十进制浮点文本；整数位数超过安全范围时使用科学计数法（如 `1E+20`），保证仍是合法 TOML 浮点。

```kotlin
import com.mingliqiye.document.format.toml.toTomlString

val node = mapper.parse("""
    [user]
    name = "小于"
    age = 18
""".trimIndent(), com.mingliqiye.document.core.Node::class.java)

println(node.toTomlString())
// user = { name = "小于", age = 18 }
```

## 类型映射

| Node | TOML 读取 | TOML 写出 |
|---|---|---|
| `ObjectNode` | 表 / 内联表 / 数组表元素 | 内联 table |
| `ArrayNode` | 数组 / 数组表 | 内联数组 |
| `StringNode` | 字符串；日期时间默认源码原文（`PARSE_JAVA_TIME` 开启时为规范化 ISO 字符串） | 双引号字符串 |
| `NumberNode` | `Long` → 整数；`Double` → 浮点（保留 tomlj 解码文本） | 按 `raw` 输出整数或浮点 |
| `BooleanNode` | `true` / `false` | `true` / `false` |
| `NullNode` | 不会从 TOML 读出（TOML 无 null 类型） | 默认写成 `""`；`FAIL_ON_NULL_WRITE` 开启时抛 `TomlException` |
| `BinaryNode` | 不会从 TOML 读出 | Base64 字符串（不保型） |

## 示例

### 1. 字符串 ↔ Bean

```kotlin
import com.mingliqiye.document.format.toml.TomlMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = TomlMapper()
val user = mapper.parse("""
    name = "小于"
    age = 18
""".trimIndent(), User::class.java)

println(mapper.format(user))
```

### 2. 嵌套表

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse("""
    [server]
    host = "127.0.0.1"
    port = 8080

    [server.tls]
    enabled = true
""".trimIndent(), Node::class.java)

val server = node.asObject()["server"]?.asObject()
println(server?.get("host")?.asString())                         // 127.0.0.1
println(server?.get("tls")?.asObject()?.get("enabled")?.asBoolean()) // true
```

### 3. 数组与数组表

```kotlin
val node = mapper.parse("""
    tags = ["a", "b"]

    [[users]]
    name = "小于"

    [[users]]
    name = "阿明"
""".trimIndent(), Node::class.java)

println(node.asObject()["tags"]?.asArray()?.size)        // 2
println(node.asObject()["users"]?.asArray()?.size)       // 2
println(node.asObject()["users"]?.asArray()?.get(1)?.asObject()?.get("name")?.asString()) // 阿明
```

### 4. 日期时间 → 字符串

```kotlin
val node = mapper.parse("""
    date = 2024-01-02
    time = 03:04:05
    datetime = 2024-01-02T03:04:05
    offset = 2024-01-02T03:04:05+08:00
""".trimIndent(), Node::class.java)

val obj = node.asObject()
// 全部为 StringNode
println(obj["date"]?.asString())      // 2024-01-02
println(obj["datetime"]?.asString())  // 2024-01-02T03:04:05
```

### 5. 内联表 / 内联数组读取

```kotlin
val node = mapper.parse("""
    user = { name = "小于", tags = ["a", "b"] }
""".trimIndent(), Node::class.java)

val user = node.asObject()["user"]?.asObject()
println(user?.get("tags")?.asArray()?.get(0)?.asString())  // a
```

### 6. 格式非法的行列定位

```kotlin
import com.mingliqiye.document.format.toml.exception.TomlParseException

val error = runCatching {
    TomlReader("name = \n").read()
}.exceptionOrNull()
check(error is TomlParseException)
println(error.message)   // Invalid TOML at line 1, column 8: ...
```

### 7. null 行为与 FAIL_ON_NULL_WRITE

```kotlin
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.toml.TomlWriteFeature
import com.mingliqiye.document.format.toml.exception.TomlException

// 默认（FAIL_ON_NULL_WRITE 关闭）：null 写成 ""
val node = ObjectNode.of("name" to StringNode.of("小于"), "nick" to NullNode.INSTANCE)
println(node.toTomlString())
// name = "小于"
// nick = ""

// 数组元素 null：同样写成 ""
val arrayNode = ObjectNode.of("tags" to ArrayNode.of(NullNode.INSTANCE))
println(arrayNode.toTomlString())   // tags = [ "" ]

// 开启 FAIL_ON_NULL_WRITE：任何 null 写出报错
val error = runCatching {
    node.toTomlString(setOf(TomlWriteFeature.FAIL_ON_NULL_WRITE))
}.exceptionOrNull()
check(error is TomlException)

// Bean 场景需要省略 null 字段时：通用配置 writeNulls(false)
val omitting = TomlMapper(
    com.mingliqiye.document.serialization.DocumentMapperConfig.builder().writeNulls(false).build(),
)
```

### 8. 二进制不保型

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.ObjectNode

val node = ObjectNode.of("data" to BinaryNode.of(byteArrayOf(1, 2, 3)))
val text = mapper.format(node)     // data = "AQID"
val restored = mapper.parse(text, com.mingliqiye.document.core.Node::class.java)
check(restored.asObject()["data"]?.isString == true)   // 读回 Base64 字符串
```

### 9. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.toml"))
val fromFile = mapper.parse(File("build/user.toml"), User::class.java)

mapper.formatPretty(user, Paths.get("build/user2.toml"))
val fromPath = mapper.parse(Paths.get("build/user2.toml"), User::class.java)
```

### 10. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(user, output)
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 11. 深度限制

```kotlin
import com.mingliqiye.document.format.toml.TomlMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig

val guarded = TomlMapper(
    DocumentMapperConfig.builder().maxNodeDepth(2).build(),
)

val error = runCatching {
    guarded.parse("""
        [a.b.c]
        value = 1
    """.trimIndent(), com.mingliqiye.document.core.Node::class.java)
}.exceptionOrNull()
println(error?.message)
```

### 12. Java 示例

```java
import com.mingliqiye.document.format.toml.TomlMapper;

public class TomlDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        TomlMapper mapper = new TomlMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        String toml = mapper.format(user);
        User restored = mapper.parse(toml, User.class);
        System.out.println(restored.name + ":" + restored.age);
    }
}
```

### 13. 数组表写出为内联数组

```kotlin
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

val node = ObjectNode.of(
    "users" to ArrayNode.of(
        ObjectNode.of("name" to StringNode.of("小于")),
        ObjectNode.of("name" to StringNode.of("阿明")),
    ),
)
println(mapper.format(node))
// users = [ { name = "小于" }, { name = "阿明" } ]
```

## 深度限制

```kotlin
val mapper = TomlMapper.builder()
    .maxDepth(512)
    .build()
```

默认 `512`，统计表/数组/内联 table 嵌套；超限抛 `TomlParseException`。
`TomlConfig.maxDepth`、`TomlMapper.builder().maxDepth(...)`、
`TomlMapper(config, maxDepth)` 三种入口等价；
`TomlReader` / `TomlTokenReader` / `TomlCommentPreservingReader` 也支持 `maxDepth`。

## 注释保留

```kotlin
val mapper = TomlMapper()
val document = mapper.parseCommented(source)

document.root // ObjectNode
document.comments // List<TomlComment>
document.toTomlString()
```

写出：

```kotlin
val text = mapper.formatCommented(document)
```

注释按表/键路径关联，支持表头注释、键前注释、行尾注释、文件头/尾注释；
完整模型和孤立注释策略见 `TomlCommentedDocument` / `TomlCommentConfig`。

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 根不是表 | `TomlParseException`（读取）；`TomlException`（写出） |
| TOML 语法错误 | `TomlParseException`，带 line/column |
| 日期时间 | 默认映射为源码原文；`PARSE_JAVA_TIME` 开启为 `java.time` 规范化 ISO-8601 字符串，均不保留 TOML 类型（Node 无 embedded object） |
| TOML 没有 null | 读取永远不会产生 `NullNode`；写出默认 `null` → `""`，`FAIL_ON_NULL_WRITE` 开启时报错，`writeNulls(false)` 时省略 Bean 字段 |
| 空字符串反序列化 | 参考实现 `TomlMapper` 默认开启 `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`（空串按目标类型转 null），本项目 `DocumentMapper` 暂无该反序列化特性，未对齐 |
| NaN / Infinity | 读取抛 `TomlParseException`；`NumberNode` 也无法表示 |
| 超出 64 位有符号整数范围的十进制整数 | 写出时转成十进制浮点（可能使用科学计数法），不再是整数 |
| 嵌套空数组 | 可以写出（`[]` / `{ a = [] }`），但 tomlj 1.0.0 **无法读回**内联 table / 数组中的空数组 |
| 数组中的日期时间 | tomlj 1.0.0 无法回读以逗号分隔的多个日期时间字面量（解析器限制，单个可以） |
| `formatPretty` | 与 `format` 输出一致（TOML 写出只有内联一种风格） |
| 二进制节点 | 写成 Base64 字符串，读回 `StringNode`（不保型） |
| key 非法字符 | 写出时加双引号；读取按 TOML 规则还原 |
| `maxNodeDepth` 等限制 | Token 路径回退 Node 路径后生效 |

## 与通用 `DocumentMapper` 的差异

- 提供格式专属 `TomlConfig`（对齐 参考实现 TOML 的读取/写出特性、解析深度与注释配置），同时保留 `TomlMapper(DocumentMapperConfig)` 构造器；
- `parse(String)` 默认走内部 `TomlTokenReader`（不构建 Node 树），配置 Node 限制时回退；
- `formatPretty` 与 `format` 相同；
- 写出统一使用内联 table / 内联数组，不生成 `[table]` 段落形式；
- 根节点固定为 `ObjectNode`；
- TOML 专属：`TomlReader`、`String.toTomlNode()`、`Node.toTomlString()`、
  `TomlReadFeature` / `TomlWriteFeature`。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [format-toml 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-toml/README.md)
