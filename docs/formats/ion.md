---
title: Ion
description: format-ion 模块：IonMapper、文本/二进制 Ion、BLOB/CLOB、类型保真与深度限制
---

# Ion

`format-ion` 提供 Amazon Ion 的文本与二进制读写，核心类型是 `IonMapper`。

Ion 是自描述格式，既有 `blob` / `clob` / `timestamp` / `symbol` 等 Node 模型无法原生区分的类型，模块默认把这些类型归一化，并提供可选的类型保真模式。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:ion:2.0.0")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `ion` |
| 版本 | `2.0.0` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `com.amazon.ion:ion-java:1.11.9` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.ion` |

## 特性

- **文本 Ion**：`parse(String)` / `format(...)`；
- **二进制 Ion**：`parse(ByteArray)` / `formatBytes(...)`；
- **单文档单根**：文本与二进制都要求恰好一个顶层值；
- **Token 直读/直写**：默认路径直接解码 Bean / 直接写 Token 流，不构建完整 Node 树；
- **`BinaryNode` ↔ Ion blob**：二进制值无损往返；
- **深度限制**：`IonReader` / `IonBinaryReader` 的 `maxDepth`，以及 Mapper 的 `maxNodeDepth`；
- **可选的 Ion 类型保真**：`IonConfig.preserveTypes` 保留 timestamp / symbol / clob / blob 类型；
- **文件/流读写**：Mapper 文件/流入口使用文本 Ion（详见“读取/写出 API”）；
- **注解忽略**：Ion annotation 不保留。

## 配置：`IonConfig`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `preserveTypes` | `Boolean` | `false` | 是否保留 Ion 专属类型（timestamp / symbol / clob / blob） |

`preserveTypes = false`（默认）时：

- timestamp / symbol → `StringNode`；
- blob / clob → `BinaryNode`；
- 写出时 `StringNode` 一律写为 Ion string、`BinaryNode` 一律写为 Ion blob；
- 读取/写出可以走 Token 快路径。

`preserveTypes = true` 时：

- Node 路径用包装对象表示类型（见下文“类型保真”）；
- Token 直读/直写被禁用，统一走 Node 树以保证保真语义一致；
- Bean 映射看到的是包装对象结构（timestamp / symbol 不再是普通字符串）。

`IonConfigBuilder` 方法：

| 方法 | 说明 |
|---|---|
| `preserveTypes(Boolean)` | 开启/关闭类型保真 |
| `field(Class, String, DocumentBaseConverter)` | 字段转换器 |
| `missingFieldPolicy` / `failOnUnknownFields` / `writeNulls` | 通用 Bean 映射选项 |
| `namingStrategy(PropertyNamingStrategy)` | 字段命名策略 |
| `fileCharset(Charset)` | 文本文件/流字符集 |
| `atomicFileWrite` / `createParentDirectories` | 文件写入选项 |
| `maxInputSize(Long)` | 输入字节上限 |
| `maxNodeDepth(Int)` / `maxNodeCount(Long)` | Node 树限制 |
| `resolveReferences` / `writeReferences` | 本地 `$ref` |
| `remoteReferenceResolver(RemoteReferenceResolver?)` | 远程 `$ref` 解析器 |
| `subtype(...)` / `typeProperty(...)` | 多态子类型 |
| `build()` | 构建 `IonConfig` |

```kotlin
import com.mingliqiye.document.format.ion.IonConfig
import com.mingliqiye.document.format.ion.IonMapper

val mapper = IonMapper(
    IonConfig.builder()
        .preserveTypes(true)
        .maxNodeDepth(64)
        .build(),
)
```

> `IonConfigBuilder` 没有暴露 `maxStringLength`；需要该限制时直接使用 `DocumentMapperConfig`（此时 `preserveTypes` 回退为 `false`）。

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `IonMapper()` | 默认：不保真类型，Token 快路径 |
| `IonMapper(config: IonConfig)` | Ion 配置 |
| `IonMapper(config: DocumentMapperConfig)` | 通用配置（`preserveTypes` 回退默认） |

```kotlin
import com.mingliqiye.document.format.ion.IonMapper

val mapper = IonMapper.builder()
    .config(IonConfig.builder().preserveTypes(false).build())
    .build()
```

## 读取 API

Ion 同时有文本与二进制两种形态，入口区分如下：

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType)` | **文本 Ion** → 实体；`IonReader` 解析 |
| `parse(source: ByteArray, targetType)` | 默认走内部 `IonTokenReader`（按 Ion 版本标记自动识别文本/二进制）；`preserveTypes=true` 时回退 Node 路径（`IonBinaryReader`，同样支持自动识别文本/二进制与 gzip） |
| `parse(file / path, targetType)` | 按文本 Ion 读取：字节按 `fileCharset` 解码成 `String` 后解析 |
| `parse(input: InputStream, targetType)` | 按文本 Ion 读取（读尽后按 `fileCharset` 解码） |
| `parse(reader: Reader, targetType)` | 按文本 Ion 读取 |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 Token 读取器 → 实体 |

> 注意：`IonMapper` 在 Mapper 层按“文本格式”语义处理文件/流（`usesTextFormat = true`）。**二进制 Ion 请使用 `parse(ByteArray)`**，需要文件/流时自行读写字节。这是 Ion 与 CBOR / Smile 等纯二进制格式的重要差异。

### 独立读取器

| 类/函数 | 说明 |
|---|---|
| `IonReader(source, maxDepth = 0, preserveTypes = false)` | 文本 Ion 读取器，`read(): Node` |
| `IonReader.parse(source)` | 静态文本解析 |
| `String.toIonNode()` | 文本 Ion 字符串 → Node |
| `IonBinaryReader(source, maxDepth = 0, preserveTypes = false)` | 二进制（也支持文本）Ion 读取器，`read(): Node` |
| `IonBinaryReader.parse(source: ByteArray)` | 静态二进制解析 |
| `ByteArray.toIonNode()` | 二进制 Ion 字节 → Node |

```kotlin
import com.mingliqiye.document.format.ion.IonBinaryReader
import com.mingliqiye.document.format.ion.IonReader

val textNode = IonReader("""{name: "小于", age: 18}""").read()
val binaryNode = IonBinaryReader(mapper.formatBytes(mapOf("name" to "小于"))).read()

val guarded = IonReader("""{a: {b: 1}}""", maxDepth = 1)
val error = runCatching { guarded.read() }.exceptionOrNull()
check(error is com.mingliqiye.document.format.ion.exception.IonParseException)
```

读取规则：

- 输入必须恰好一个顶层值：没有值或多于一个值都抛 `IonParseException`；
- 解析完成会关闭底层 ion-java reader；
- 深度检查作用于容器节点（list/sexp/struct），`maxDepth > 0` 时超限抛 `IonParseException`；
- 文本 `IonReader` 与二进制 `IonBinaryReader` 共用同一套值映射与 `readIonNode` 实现；
- 二进制读取通过 `IonSystem.newReader(InputStream)` 自动识别文本/二进制，并支持 GZIP 解压。

### Token 路径

默认模式（`preserveTypes=false`）下：

- `parse(ByteArray)` 优先用内部 `IonTokenReader` 直读 Bean，不构建 Node 树；Token 快路径不执行通用 Node 树限制（`maxNodeDepth` 等）；
- `parse(String)`（文本）走 `IonReader(source, maxNodeDepth, ...)` Node 路径，`maxNodeDepth` 生效；
- `preserveTypes=true` 时 `createTokenReader` 返回 `null`，字节解析回退 Node 路径（`IonBinaryReader`，携带 `maxNodeDepth`）；
- 需要强制深度限制的二进制读取，可直接使用 `IonBinaryReader(bytes, maxDepth = ...)`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → 文本 Ion（紧凑，无版本标记） |
| `formatPretty(value)` | 实体 → 文本 Ion（pretty） |
| `formatBytes(value)` | 实体 → 二进制 Ion；默认 `IonBinaryTokenWriter` 直写，`preserveTypes=true` 时 `IonBinaryWriter` Node 写出 |
| `format(value, file / path)` | 写文本 Ion 文件（按 `fileCharset`） |
| `formatPretty(value, file / path)` | 写 pretty 文本文件 |
| `format(value, output: OutputStream)` | 文本 Ion 流式写出（`IonTextTokenWriter`） |
| `formatPretty(value, output: OutputStream)` | 文本 Ion pretty 流式写出 |
| `format(value, writer: Writer)` / `formatPretty(value, writer)` | 文本 Ion 字符流 |
| `Node.toIonString(pretty: Boolean = false)` | Node → 文本 Ion |
| `Node.toIonBytes()` | Node → 二进制 Ion（含 `$ion_1_0` 二进制版本标记） |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `formatBytes` | ✅ | 默认 `IonBinaryTokenWriter` 直接生成二进制 Ion |
| `format(value, OutputStream)` | ✅ | `IonTextTokenWriter` 文本流式写出 |
| `format(value, File/Path)` | ❌ | 文本：先构造 `String` 再写文件 |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

写出规则：

- 文本写出使用 ion-java 文本写出器（minimum system data，**不输出** `$ion_1_0` 文本版本标记）；
- 二进制写出使用 `IonSystem.newBinaryWriter`，输出以 `$ion_1_0` 二进制版本标记开头；
- 数字按 `NumberNode.raw` 区分：不含小数点/指数的写为 Ion int，其余写为 Ion decimal；
- `BinaryNode` 写为 Ion blob；
- `StringNode` 一律写为 Ion string（默认模式下 timestamp/symbol 信息不可恢复）；
- Ion 注解不写出。

## 类型映射

默认（`preserveTypes = false`）：

| Ion 类型 | Node |
|---|---|
| `null`（含 `null.int` 等类型化 null） | `NullNode` |
| `bool` | `BooleanNode` |
| `int` | `NumberNode`（`raw` 为十进制文本） |
| `float` | `NumberNode`（NaN / Infinity 拒绝） |
| `decimal` | `NumberNode`（精确 `BigDecimal`） |
| `string` | `StringNode` |
| `symbol` | `StringNode` |
| `timestamp` | `StringNode`（Ion 时间戳文本） |
| `blob` | `BinaryNode` |
| `clob` | `BinaryNode` |
| `list` / `sexp` | `ArrayNode` |
| `struct` | `ObjectNode` |

写出反向：

| Node | 文本 Ion / 二进制 Ion |
|---|---|
| `ObjectNode` | struct |
| `ArrayNode` | list |
| `StringNode` | string |
| `NumberNode` | int 或 decimal（按 `raw` 判定） |
| `BooleanNode` | bool |
| `NullNode` | null |
| `BinaryNode` | blob |

NaN / Infinity：Ion float 可以是 `nan` / `+inf`，但 `NumberNode` 只保存有限十进制值，读取时抛 `IonParseException("Ion float NaN and Infinity are not supported")`。

## 类型保真（`preserveTypes`）

开启后 Node 路径用包装对象保留 Node 模型无法区分的 Ion 类型：

```json
{"$ionType":"timestamp","value":"2026-11-28T00:00:00Z"}
{"$ionType":"symbol","value":"name"}
{"$ionType":"clob","value":<BinaryNode>}
{"$ionType":"blob","value":<BinaryNode>}
```

- 普通 `string` 仍是 `StringNode`，不会被包装；
- 写出时只识别“恰好两个字段、`$ionType` 为已知类型、`value` 类型匹配”的对象，其余按普通对象处理；
- `timestamp` 包装的 `value` 必须是合法 Ion 时间戳文本，否则写抛 `IonException`；
- 开启后 Token 快路径被禁用（构建完整 Node 树），Bean 映射按包装对象结构进行。

```kotlin
import com.mingliqiye.document.format.ion.IonConfig
import com.mingliqiye.document.format.ion.IonMapper

val mapper = IonMapper(IonConfig.builder().preserveTypes(true).build())

val text = """{ts: 2024-01-02T03:04:05Z, sym: abc, data: {{ aGVsbG8= }}}"""
val node = mapper.parse(text, com.mingliqiye.document.core.Node::class.java)

val obj = node.asObject()
println(obj["ts"]?.asObject()?.get("\$ionType")?.asString())   // timestamp
println(obj["sym"]?.asObject()?.get("\$ionType")?.asString())  // symbol

// 写回时自动还原 Ion timestamp / symbol / blob
println(mapper.format(node))
```

## 示例

### 1. 文本 Ion ↔ Bean

```kotlin
import com.mingliqiye.document.format.ion.IonMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = IonMapper()
val user = mapper.parse("""{name: "小于", age: 18}""", User::class.java)
val text = mapper.format(user)
```

### 2. 二进制 Ion ↔ Bean

```kotlin
val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
check(restored.age == 18)
```

### 3. 文本 Ion → Node

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse("""{name: "小于", tags: ["a", "b"]}""", Node::class.java)
println(node.asObject()["name"]?.asString())
println(node.asObject()["tags"]?.asArray()?.size)
```

### 4. 二进制 Ion → Node

```kotlin
val node = IonBinaryReader(mapper.formatBytes(user)).read()
println(node.asObject()["name"]?.asString())
```

### 5. blob / clob

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.ObjectNode

val node = ObjectNode.of("data" to BinaryNode.of(byteArrayOf(1, 2, 3)))
val bytes = mapper.formatBytes(node)             // 二进制 Ion，blob
val text = mapper.format(node)                   // 文本 Ion：data: {{ AQID }}

val restored = mapper.parse(text, Node::class.java)
check(restored.asObject()["data"]?.isBinary == true)
```

### 6. timestamp / symbol 默认归一为字符串

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse(
    """{ts: 2024-01-02T03:04:05Z, sym: abc}""",
    Node::class.java,
)
val obj = node.asObject()
check(obj["ts"]?.isString == true)    // timestamp -> StringNode
check(obj["sym"]?.isString == true)   // symbol -> StringNode
println(obj["ts"]?.asString())        // 2024-01-02T03:04:05Z
```

### 7. 类型保真往返

```kotlin
import com.mingliqiye.document.format.ion.IonConfig

val preserving = IonMapper(IonConfig.builder().preserveTypes(true).build())
val node = preserving.parse("""{ts: 2024-01-02T03:04:05Z, data: {{ aGVsbG8= }}}""", Node::class.java)

val text = preserving.format(node)
println(text)   // ts: 2024-01-02T03:04:05Z, data: {{aGVsbG8=}}（保持 timestamp / blob）

// 关闭保真时同一个输入会变成普通字符串 / blob
val plain = mapper.parse("""{ts: 2024-01-02T03:04:05Z}""", Node::class.java)
check(plain.asObject()["ts"]?.isString == true)
```

### 8. pretty 文本输出

```kotlin
println(mapper.formatPretty(user))

import com.mingliqiye.document.format.ion.toIonString
val node = mapper.toNode(user)
println(node.toIonString(pretty = true))
```

### 9. 文本文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

// Mapper 的文件入口使用文本 Ion
mapper.format(user, File("build/user.ion"))
val fromFile = mapper.parse(File("build/user.ion"), User::class.java)

mapper.formatPretty(user, Paths.get("build/user-pretty.ion"))
val fromPath = mapper.parse(Paths.get("build/user-pretty.ion"), User::class.java)
```

### 10. 二进制文件：手动写读字节

```kotlin
import java.io.File

val bytes = mapper.formatBytes(user)
File("build/user.bin.ion").writeBytes(bytes)

val restored = mapper.parse(File("build/user.bin.ion").readBytes(), User::class.java)
check(restored.name == "小于")
```

### 11. 流式文本写出

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(user, output)          // 文本 Ion 流式写出
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 12. 深度限制

```kotlin
import com.mingliqiye.document.format.ion.IonBinaryReader
import com.mingliqiye.document.format.ion.IonConfig
import com.mingliqiye.document.format.ion.IonMapper
import com.mingliqiye.document.format.ion.IonReader
import com.mingliqiye.document.format.ion.exception.IonParseException

// 读取器级别
val readerError = runCatching { IonReader("""{a: {b: {c: 1}}}""", maxDepth = 1).read() }.exceptionOrNull()
check(readerError is IonParseException)

// Mapper 级别（Node 路径生效）
val guarded = IonMapper(IonConfig.builder().maxNodeDepth(1).build())
val mapperError = runCatching {
    guarded.parse("""{a: {b: {c: 1}}}""", com.mingliqiye.document.core.Node::class.java)
}.exceptionOrNull()
println(mapperError?.message)

// 二进制读取器级别
val binaryError = runCatching {
    IonBinaryReader(mapper.formatBytes(mapOf("a" to mapOf("b" to 1))), maxDepth = 1).read()
}.exceptionOrNull()
check(binaryError is IonParseException)
```

### 13. 多个顶层值被拒绝

```kotlin
import com.mingliqiye.document.format.ion.IonReader
import com.mingliqiye.document.format.ion.exception.IonParseException

val error = runCatching { IonReader("1 2").read() }.exceptionOrNull()
check(error is IonParseException)
```

### 14. Java 示例

```java
import com.mingliqiye.document.format.ion.IonMapper;

public class IonDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        IonMapper mapper = new IonMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        // 文本 Ion
        String text = mapper.format(user);
        User fromText = mapper.parse(text, User.class);

        // 二进制 Ion
        byte[] bytes = mapper.formatBytes(user);
        User fromBinary = mapper.parse(bytes, User.class);

        System.out.println(fromText.name + ":" + fromBinary.age);
    }
}
```

### 15. 注解被忽略

```kotlin
val node = mapper.parse("""'my.annotation'::{name: "小于"}""", Node::class.java)
// Ion annotation 不保留，解析结果只包含 struct 字段
println(node.asObject()["name"]?.asString())   // 小于
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 无顶层值 | `IonParseException("Ion document has no top-level value")` |
| 多个顶层值 | `IonParseException("Ion document must contain exactly one top-level value")` |
| Ion 注解 | 忽略，不保留 |
| timestamp / symbol（默认模式） | 读成字符串；写回为 string，类型信息丢失 |
| CLOB（默认模式） | 读成 `BinaryNode`，写回为 blob，clob 类型信息丢失 |
| NaN / Infinity float | `IonParseException` |
| 递归深度超过 `maxDepth` | `IonParseException` |
| `preserveTypes` 下的 malformed 包装对象 | 按普通对象处理（timestamp 包装且文本非法时写出抛 `IonException`） |
| 二进制 Ion 用 `parse(file)` / `parse(InputStream)` | 会按文本解码，可能失败；请用 `parse(ByteArray)` |
| 文本 Ion 的 `$ion_1_0` 标记 | 文本写出不输出；解析器可接受显式版本标记 |
| `preserveTypes=true` | Token 快路径禁用，Bean 映射看到包装对象结构 |

## 与通用 `DocumentMapper` 的差异

- 构造：`IonConfig` 专属配置类与 `IonMapper.builder()`；
- 同时实现文本与二进制编解码：`parse(String)` 文本、`parse(ByteArray)` 二进制（自动识别）；
- `formatBytes`：默认通过内部 `IonBinaryTokenWriter` 直写二进制 Ion（含版本标记）；
- `format(value, OutputStream)`：覆写为文本 Ion 流式写出；
- Mapper 的文件/流入口按文本 Ion 语义（`usesTextFormat = true`），二进制文件需自行处理字节；
- Ion 专属：`IonReader`、`IonBinaryReader`、`IonConfig.preserveTypes`、`String.toIonNode()` / `ByteArray.toIonNode()` / `Node.toIonString()` / `Node.toIonBytes()`。

## 相关链接

- [格式总览](/formats/)
- [核心概念：Node 树](/guide/core-concepts.html)
- [文件与流 IO](/guide/io.html)
- [API 参考](/reference/api.html)
- [异常参考](/reference/exceptions.html)
- [format-ion 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-ion/README.md)
