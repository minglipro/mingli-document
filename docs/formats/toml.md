---
title: TOML
description: format-toml 模块：TomlMapper、表/数组读写、内联表写出、日期时间字符串化与 TOML 边界
---

# TOML

`format-toml` 提供 TOML 读写，核心类型是 `TomlMapper`。

TOML 文档根必须是表（table），因此 TOML 的根节点固定为 `ObjectNode`；日期时间类型在 Node 模型中没有对应节点，统一映射为 ISO-8601 字符串。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:toml:2.3.0")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `toml` |
| 版本 | `2.3.0` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `org.tomlj:tomlj:1.0.0` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.toml` |

## 特性

- **标准 TOML 表与数组**：读取标准表、数组表、内联表、内联数组；
- **嵌套表**：任意层级表结构映射为嵌套 `ObjectNode`；
- **内联写出**：写出统一使用内联 table / 内联数组（`user = { name = "小于", age = 18 }`）；
- **日期时间字符串化**：`OffsetDateTime` / `LocalDateTime` / `LocalDate` / `LocalTime` 读取为 ISO-8601 字符串；
- **原生 Token 读取**：`parse(String)` 通过内部 `TomlTokenReader` 直接遍历 tomlj 结构生成 Token 流；
- **错误定位**：解析错误信息包含行列号；
- **文件/流读写**：文本 UTF-8（可改 `fileCharset`）；
- **无专属配置类**：使用通用 `DocumentMapperConfig`。

## 配置

TOML 模块没有专属 Config 类（源码中不存在 `TomlConfig`），直接使用 `DocumentMapperConfig`：

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.format.toml.TomlMapper

val mapper = TomlMapper(
    DocumentMapperConfig.builder()
        .maxNodeDepth(64)
        .fileCharset(Charsets.UTF_8)
        .atomicFileWrite(true)
        .build(),
)
```

通用配置项与其它格式一致：`missingFieldPolicy`、`failOnUnknownFields`、`writeNulls`、`namingStrategy`、`fileCharset`、`atomicFileWrite`、`createParentDirectories`、`maxInputSize`、`maxStringLength`、`maxNodeDepth`、`maxNodeCount`、`resolveReferences` / `writeReferences`、`remoteReferenceResolver`。

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `TomlMapper()` | 默认配置 |
| `TomlMapper(config: DocumentMapperConfig)` | 通用配置 |

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
- `OffsetDateTime` / `LocalDateTime` / `LocalDate` / `LocalTime` → `StringNode`（ISO-8601 文本）。

### Token 路径

`TomlMapper.parse(String)` 使用内部 `TomlTokenReader`：

- 直接遍历 tomlj 的 `TomlTable` / `TomlArray` 生成 Token 流，不构建完整 Node 树；
- 日期时间同样输出为 `STRING` Token（`toString()`）；
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
- 对象中值为 `NullNode` 的字段被省略；数组中不允许 `null`，出现即抛 `TomlException`；
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
| `StringNode` | 字符串；日期时间也读成字符串 | 双引号字符串 |
| `NumberNode` | `Long` → 整数；`Double` → 浮点（保留 tomlj 解码文本） | 按 `raw` 输出整数或浮点 |
| `BooleanNode` | `true` / `false` | `true` / `false` |
| `NullNode` | 不会从 TOML 读出（TOML 无 null 类型） | 对象中省略；数组中抛异常 |
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

### 7. null 行为

```kotlin
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.toml.exception.TomlException

// 对象字段 null：省略
val node = ObjectNode.of("name" to StringNode.of("小于"), "nick" to NullNode.INSTANCE)
println(mapper.format(node))    // name = "小于"

// 数组元素 null：报错
val arrayNode = ObjectNode.of(
    "tags" to com.mingliqiye.document.core.ArrayNode.of(NullNode.INSTANCE),
)
val error = runCatching { mapper.format(arrayNode) }.exceptionOrNull()
check(error is TomlException)
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

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 根不是表 | `TomlParseException`（读取）；`TomlException`（写出） |
| TOML 语法错误 | `TomlParseException`，带 line/column |
| 日期时间 | 统一映射为 ISO-8601 字符串，不保留 TOML 类型 |
| TOML 没有 null | 读取永远不会产生 `NullNode`；写出时对象字段省略、数组元素报错 |
| NaN / Infinity | 读取抛 `TomlParseException`；`NumberNode` 也无法表示 |
| 超出 64 位有符号整数范围的十进制整数 | 写出时转成十进制浮点（可能使用科学计数法），不再是整数 |
| 嵌套空数组 | 可以写出（`[]` / `{ a = [] }`），但 tomlj 1.0.0 **无法读回**内联 table / 数组中的空数组 |
| `formatPretty` | 与 `format` 输出一致（TOML 写出只有内联一种风格） |
| 二进制节点 | 写成 Base64 字符串，读回 `StringNode`（不保型） |
| key 非法字符 | 写出时加双引号；读取按 TOML 规则还原 |
| `maxNodeDepth` 等限制 | Token 路径回退 Node 路径后生效 |

## 与通用 `DocumentMapper` 的差异

- 没有格式专属 Config 类（源码中不存在 `TomlConfig`），直接构造 `TomlMapper(DocumentMapperConfig)`；
- `parse(String)` 默认走内部 `TomlTokenReader`（不构建 Node 树），配置 Node 限制时回退；
- `formatPretty` 与 `format` 相同；
- 写出统一使用内联 table / 内联数组，不生成 `[table]` 段落形式；
- 根节点固定为 `ObjectNode`；
- TOML 专属：`TomlReader`、`String.toTomlNode()`、`Node.toTomlString()`。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [format-toml 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-toml/README.md)
