---
title: Smile
description: format-smile 模块：SmileMapper、Smile 二进制、SmileConfig 读写特性（对齐 参考实现）、embedded binary、多消息流与深度限制
---

# Smile

`format-smile` 提供 Smile（参考实现 二进制 JSON 格式）读写，核心类型是 `SmileMapper`。

Smile 是纯二进制格式，Mapper 只实现字节 API：`parse(String)` / `format(...)` 等文本入口会抛 `DocumentSerializationException`。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:smile:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `smile` |
| 版本 | `2.5.1` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `第三方底层库坐标.dataformat:Smile 底层库:2.15.3` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.smile` |

## 特性

- **Smile 二进制**：对象 / 数组 / 字符串 / 数字 / 布尔 / null；
- **参考实现 读写特性对齐**：`SmileConfig` 提供 `SmileReadFeature` / `SmileWriteFeature`，覆盖 Smile 底层库 的 `SmileParser.Feature` / `SmileGenerator.Feature` 全部选项及 `STRICT_DUPLICATE_DETECTION`，默认值与 参考实现 一致；
- **`BinaryNode` 往返**：使用 参考实现 Smile 的 embedded binary 扩展承载二进制内容，不做 Base64 字符串降级；
- **Token 直读/直写**：默认路径直接从 Smile 事件解码 Bean / 直接写 Token 流，不构建完整 Node 树；
- **多消息流**：Smile 字节流允许连续多个根值，提供 `parseAll` / `formatAll` 与独立 sequence API；
- **深度限制**：`maxNodeDepth` / `SmileReader(maxDepth)` / `maxItems`；
- **文件/流读写**：二进制语义，`OutputStream` 可直接流式写。

## 配置：`SmileConfig`

Smile 专属配置类，读写特性与 Smile 底层库 对齐（默认值一致），并提供
`enable` / `disable` / `configure` 与 参考实现 同名选项；同时继承
`DocumentMapperConfig`，可配置通用字段/读写行为。

```kotlin
import com.mingliqiye.document.format.smile.SmileConfig
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.smile.SmileReadFeature
import com.mingliqiye.document.format.smile.SmileWriteFeature

val config = SmileConfig.builder()
    .writeNulls(false)
    .maxNodeDepth(64)
    .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
    .disable(SmileReadFeature.REQUIRE_HEADER)
    .build()
val mapper = SmileMapper(config)
```

### 参考实现 映射与默认值

读取特性（`SmileReadFeature`）：

| SmileConfig 方法 | 参考实现 对应 | 默认 | 行为 |
|---|---|---|---|
| `enable/disable/configure(SmileReadFeature.REQUIRE_HEADER)` | `SmileParser.Feature.REQUIRE_HEADER` | 开 | 输入必须以 Smile 4 字节头部开头；关闭后允许无头部输入 |
| `enable/disable/configure(SmileReadFeature.STRICT_DUPLICATE_DETECTION)` | `JsonParser.Feature.STRICT_DUPLICATE_DETECTION` | 关 | 对象重复字段名抛 `SmileParseException`；默认以后出现的值为准 |

写出特性（`SmileWriteFeature`）：

| SmileConfig 方法 | 参考实现 对应 | 默认 | 行为 |
|---|---|---|---|
| `enable/disable/configure(SmileWriteFeature.WRITE_HEADER)` | `SmileGenerator.Feature.WRITE_HEADER` | 开 | 输出以 4 字节 Smile 头部开头；关闭后无头部 |
| `enable/disable/configure(SmileWriteFeature.WRITE_END_MARKER)` | `SmileGenerator.Feature.WRITE_END_MARKER` | 关 | 关闭写出器时追加 `0xFF` 结束标记（读取器视为 EOF） |
| `enable/disable/configure(SmileWriteFeature.ENCODE_BINARY_AS_7BIT)` | `SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT` | 开 | 二进制 7-bit 编码，输出不含 `0xFF`；关闭后写原始二进制并在头部标记 HAS_RAW_BINARY |
| `enable/disable/configure(SmileWriteFeature.CHECK_SHARED_NAMES)` | `SmileGenerator.Feature.CHECK_SHARED_NAMES` | 开 | 重复字段名使用反向引用，输出更紧凑 |
| `enable/disable/configure(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)` | `SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES` | 关 | 重复短字符串使用反向引用，输出更紧凑 |
| `enable/disable/configure(SmileWriteFeature.LENIENT_UTF_ENCODING)` | `SmileGenerator.Feature.LENIENT_UTF_ENCODING` | 关 | 非法代理项替换为 U+FFFD；关闭时写出抛 `SmileException` |
| `enable/disable/configure(SmileWriteFeature.STRICT_DUPLICATE_DETECTION)` | `JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION` | 关 | Token 写出重复字段名时抛 `SmileException` |

查询使用 `config.isEnabled(feature)`；默认集合见 `SmileConfig.DEFAULT_READ_FEATURES` /
`SmileConfig.DEFAULT_WRITE_FEATURES`。

### 特性交互与校验

- 关闭 `WRITE_HEADER` 时，`CHECK_SHARED_STRING_VALUES` 必须关闭且
  `ENCODE_BINARY_AS_7BIT` 必须开启（无头部时解析方无法得知这些设置，参考实现 生成器
  同样拒绝此组合）；违反时 `SmileConfigBuilder.build()` 抛 `IllegalArgumentException`；
- 关闭 `REQUIRE_HEADER` 的读取器可读取有头部和无头部两种输入；
- `WRITE_END_MARKER` 只在会关闭 参考实现 写出器的路径生效（`formatBytes`、`formatAll`、
  `SmileWriter`、`SmileSequenceWriter`）；流式 `format(value, OutputStream)` 只 flush
  不关闭，不追加结束标记。

### 通用配置便捷方法

`SmileConfigBuilder` 直接委托 `DocumentMapperConfig` 构建器，提供：
`field`、`missingFieldPolicy`、`failOnUnknownFields`、`writeNulls`、`namingStrategy`、
`fileCharset`（对二进制编解码无影响）、`atomicFileWrite`、`createParentDirectories`、
`maxInputSize`、`maxStringLength`、`maxNodeDepth`、`maxNodeCount`、
`resolveReferences` / `writeReferences`、`remoteReferenceResolver`、`beanValidator`、
`subtype` / `typeProperty`、反序列化特性 `enable` / `disable` / `configure` 与
`failOnNullForPrimitives`。

只传 `DocumentMapperConfig`（非 `SmileConfig`）时使用 参考实现 默认读写特性。

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `SmileMapper()` | 默认配置（参考实现 默认读写特性），Token 快路径 |
| `SmileMapper(config: SmileConfig)` | Smile 读写特性 + 通用配置 |
| `SmileMapper(config: DocumentMapperConfig)` | 仅通用配置（读写特性取 参考实现 默认值） |

```kotlin
import com.mingliqiye.document.format.smile.SmileConfig
import com.mingliqiye.document.format.smile.SmileMapper

val mapper = SmileMapper.builder()
    .config(SmileConfig.builder().maxNodeDepth(64).build())
    .build()
```

## 读取 API

### 二进制入口

| 方法 | 说明 |
|---|---|
| `parse(source: ByteArray, targetType)` | 单值解析；通过内部 `SmileTokenReader` 直读 Bean，Node 路径作为基类 fallback；读取特性取自 `SmileConfig` |
| `parse(file: File, targetType)` / `parse(path: Path, targetType)` | 二进制文件 → 实体 |
| `parse(input: InputStream, targetType)` | 二进制流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 Token 读取器 → 实体 |

### 不支持的文本入口

| 方法 | 行为 |
|---|---|
| `parse(source: String, targetType)` | 抛 `DocumentSerializationException("smile mapper only supports binary input")` |
| `parse(reader: Reader, targetType)` | 抛 `DocumentSerializationException("... does not support character streams")` |

### 独立读取器

| 类/函数 | 说明 |
|---|---|
| `SmileReader(source, maxDepth = 0, config = null)` | 单值读取器，`read(): Node`；`config` 控制读取特性 |
| `ByteArray.toSmileNode()` | 扩展函数：Smile 字节 → Node（参考实现 默认特性） |
| `SmileSequenceReader(source, maxDepth = 0, maxItems = 0, config = null)` | 多消息流读取器，`readAll(): List<Node>` |
| `SmileSequenceReader.parseAll(source, maxDepth, maxItems, config)` | 静态多值解析 |
| `ByteArray.toSmileNodes()` | 扩展函数：多消息流字节 → `List<Node>`（参考实现 默认特性） |

```kotlin
import com.mingliqiye.document.format.smile.SmileConfig
import com.mingliqiye.document.format.smile.SmileReadFeature
import com.mingliqiye.document.format.smile.SmileReader

val relaxed = SmileConfig.builder()
    .disable(SmileReadFeature.REQUIRE_HEADER)
    .build()
val node = SmileReader(headerlessBytes, config = relaxed).read()
```

读取规则：

- 单值入口要求输入恰好一个顶层值：无值或多值都抛 `SmileParseException`；
- 默认重复字段名以后出现的值为准；开启 `STRICT_DUPLICATE_DETECTION` 后抛
  `SmileParseException`；
- 类型映射：
  - `null` → `NullNode`；
  - `true`/`false` → `BooleanNode`；
  - 整数 / 浮点 / 大数 → `NumberNode`（保留 `NumberNode.raw`）；
  - 文本 → `StringNode`；
  - Smile 二进制值（embedded object）→ `BinaryNode`；
  - 数组 → `ArrayNode`；
  - 对象 → `ObjectNode`；
- 非有限浮点（NaN / Infinity）无法由 `NumberNode` 表达，抛 `SmileParseException`；
- 深度限制在容器进入时检查，超过 `maxDepth` 抛 `SmileParseException`；
- 读取完成关闭底层 parser。

### 多消息流（多根值）

Smile 字节流允许连续出现多个根值（多消息流）。单值 API 语义不变，多值读写使用独立 API。

`SmileSequenceReader` 边界约定：

- 空输入是合法的零消息流：`readAll()` 返回空列表；
- 只有一个根值时返回长度为 1 的列表；
- 任一消息格式非法或数据不完整时整体抛 `SmileParseException`，不返回部分结果；
- 每个根值独立解析，深度从 1 重新计数；
- `maxItems > 0` 时消息数超过上限抛 `SmileParseException`；
- 整段解析，暂未提供逐项/流式增量 API；
- 读取特性（如 `REQUIRE_HEADER`、`STRICT_DUPLICATE_DETECTION`）通过 `config` 传入，
  `SmileMapper.parseAllToNodes` / `parseAll` 使用 Mapper 自身的 `SmileConfig`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `formatBytes(value)` | 实体 → Smile 字节；通过内部 `SmileTokenWriter` 直写 Token 流，不构建 Node 树；写出特性取自 `SmileConfig` |
| `format(value, file / path)` | 写二进制文件 |
| `format(value, output: OutputStream)` | 二进制输出；非 pretty 模式直接流式写 Token（只 flush，不写结束标记） |
| `formatAll(values: Iterable<Any?>): ByteArray` | 多个实体 → Smile 多消息流字节 |
| `SmileWriter(config = null).write(node)` | 公开的 Node → Smile 字节写出器 |
| `SmileSequenceWriter(config = null).writeAll(nodes)` | 公开的多消息流 Node 写出器 |
| `Collection<Node>.toSmileSequenceBytes()` | 扩展函数：Node 集合 → 多消息流字节（参考实现 默认特性） |

不支持：

| 方法 | 行为 |
|---|---|
| `format(value)` / `formatPretty(value)` | 抛 `DocumentSerializationException("smile mapper only supports binary output")` |
| `format(value, writer: Writer)` | 抛异常（二进制格式不支持字符流） |
| `formatPretty(value, file / path / output)` | 抛异常（二进制格式不支持 pretty） |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `formatBytes` | ✅ | `SmileTokenWriter` 直接写 Token 流 |
| `format(value, OutputStream)` | ✅ | `createStreamingTokenWriter` 返回 `SmileTokenWriter`（非 pretty 时） |
| `format(value, File/Path)` | ❌ | 先得到 `ByteArray` 再写文件 |
| `formatAll` | ✅ | 一次 `SmileTokenWriter` 内顺序写多个根值，Smile 头部只写一次 |

写出细节：

- 使用 参考实现 Smile 写出器，自描述 Smile 文档可直接读回；头部、结束标记、共享字段名/
  字符串值、二进制 7-bit 编码、宽松 UTF 编码、严格重复字段名均由 `SmileConfig`
  开关控制，默认与 参考实现 一致（见上文特性矩阵）；
- 数字按 `NumberNode.raw` 分类：纯整数写为 Smile 整数（int / long / big integer），含小数点或指数的写为 big decimal；读取后按数值比较与原始树等价；
- `BinaryNode` 使用 参考实现 Smile 的 embedded binary 扩展令牌（7-bit 编码的 `0xE8` / 原始 `0xFD`）承载，读取时给出 `VALUE_EMBEDDED_OBJECT` 并映射回 `BinaryNode`；
- `SmileWriter` 的多消息写出中 Smile 头部只在流开头写一次，各根值首尾相接；
- 流式 `format(value, OutputStream)` 与 `formatBytes` 使用同一 `SmileConfig`，字节内容一致；
  但流式路径只 flush 不关闭，因此 `WRITE_END_MARKER` 不会追加。

## 类型映射

| Node | Smile 读取 | Smile 写出 |
|---|---|---|
| `ObjectNode` | 对象（重复字段后者覆盖） | 对象 |
| `ArrayNode` | 数组 | 数组 |
| `StringNode` | 文本 | 文本 |
| `NumberNode` | 整数 / 浮点 / 大数，保留 `raw` | 纯整数 → Smile 整数；含小数点/指数 → big decimal |
| `BooleanNode` | `true` / `false` | `true` / `false` |
| `NullNode` | `null` | `null` |
| `BinaryNode` | Smile embedded binary | 参考实现 Smile embedded binary 扩展 |

约束：NaN / Infinity 无法表示（`NumberNode` 只保存有限十进制值）。

## 示例

### 1. Bean ↔ Smile 字节

```kotlin
import com.mingliqiye.document.format.smile.SmileMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = SmileMapper()
val user = User().apply {
    name = "小于"
    age = 18
}

val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
```

### 2. Node 读写

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse(mapper.formatBytes(user), Node::class.java)
println(node.asObject()["name"]?.asString())
```

### 3. 二进制值往返

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.ObjectNode

val node = ObjectNode.of("data" to BinaryNode.of(byteArrayOf(0, 1, 2, -1)))
val bytes = mapper.formatBytes(node)

val restored = mapper.parse(bytes, Node::class.java)
check(restored.asObject()["data"]?.isBinary == true)
check(restored.asObject()["data"]?.asBinary()?.contentEquals(byteArrayOf(0, 1, 2, -1)) == true)
```

### 4. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.smile"))
val fromFile = mapper.parse(File("build/user.smile"), User::class.java)

mapper.format(user, Paths.get("build/user2.smile"))
val fromPath = mapper.parse(Paths.get("build/user2.smile"), User::class.java)
```

### 5. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(user, output)          // 二进制流式写
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 6. 多消息流基本读写

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.smile.toSmileNodes

val a = User().apply { name = "a"; age = 1 }
val b = User().apply { name = "b"; age = 2 }

val stream: ByteArray = mapper.formatAll(listOf(a, b))
val nodes: List<Node> = mapper.parseAllToNodes(stream)
check(nodes.size == 2)

val users: List<User> = mapper.parseAll(stream, User::class.java)
check(users[1].name == "b")

val nodes2 = stream.toSmileNodes()
check(nodes2.size == 2)
```

### 7. 空多消息流

```kotlin
val empty = mapper.formatAll(emptyList())
check(empty.isEmpty())
check(mapper.parseAllToNodes(empty).isEmpty())
check(mapper.parseAll(empty, User::class.java).isEmpty())
```

### 8. maxItems 限制

```kotlin
import com.mingliqiye.document.format.smile.exception.SmileParseException

val stream = mapper.formatAll(listOf(a, b, a))
val error = runCatching { mapper.parseAllToNodes(stream, maxItems = 2) }.exceptionOrNull()
check(error is SmileParseException)
```

### 9. 单值入口拒绝多根值

```kotlin
import com.mingliqiye.document.format.smile.exception.SmileParseException

val stream = mapper.formatAll(listOf(a, b))
val error = runCatching { mapper.parse(stream, User::class.java) }.exceptionOrNull()
check(error is SmileParseException)
```

### 10. 深度限制

```kotlin
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.smile.SmileReader
import com.mingliqiye.document.format.smile.exception.SmileParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig

val bytes = mapper.formatBytes(mapOf("a" to mapOf("b" to 1)))

// 独立读取器：深度限制在容器进入时检查
val readerError = runCatching { SmileReader(bytes, maxDepth = 1).read() }.exceptionOrNull()
check(readerError is SmileParseException)

// Mapper 的 maxNodeDepth 作用于 Node 回退路径与多消息流 API；
// 默认单值 Token 快路径（parse(ByteArray)）不构建 Node 树，不执行该限制
val guarded = SmileMapper(DocumentMapperConfig.builder().maxNodeDepth(1).build())
val sequenceError = runCatching { guarded.parseAllToNodes(bytes) }.exceptionOrNull()
println(sequenceError?.message)
```

### 11. 文本 API 不支持

```kotlin
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

val error = runCatching { mapper.parse("""{"name":"x"}""", User::class.java) }.exceptionOrNull()
check(error is DocumentSerializationException)
```

### 12. Node 集合 → 多消息流

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.smile.toSmileSequenceBytes

val nodes: List<Node> = listOf(mapper.toNode(a), mapper.toNode(b))
val bytes = nodes.toSmileSequenceBytes()
check(mapper.parseAllToNodes(bytes).size == 2)
```

### 13. 公开的 Node 写出器

```kotlin
import com.mingliqiye.document.format.smile.SmileSequenceWriter
import com.mingliqiye.document.format.smile.SmileWriter

val single = SmileWriter().write(mapper.toNode(user))
val multi = SmileSequenceWriter().writeAll(listOf(mapper.toNode(a), mapper.toNode(b)))

check(mapper.parse(single, User::class.java).name == "小于")
check(mapper.parseAllToNodes(multi).size == 2)
```

### 14. Java 示例

```java
import com.mingliqiye.document.format.smile.SmileMapper;

public class SmileDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        SmileMapper mapper = new SmileMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        byte[] bytes = mapper.formatBytes(user);
        User restored = mapper.parse(bytes, User.class);
        System.out.println(restored.name + ":" + restored.age);

        mapper.format(user, new java.io.File("build/user.smile"));
        User fromFile = mapper.parse(new java.io.File("build/user.smile"), User.class);
        System.out.println(fromFile.age);
    }
}
```

### 15. 多消息流头信息只写一次

```kotlin
// formatAll 在一个 Token writer 内顺序写多个根值，Smile 头部只出现一次
val stream = mapper.formatAll(listOf(a, b))
check(mapper.parseAllToNodes(stream).size == 2)

// 逐个 formatBytes 得到的每个字节数组各自带一个 Smile 头部；
// 需要多消息流时请使用 formatAll / SmileSequenceWriter，而不是拼接多个单值字节数组
val single = mapper.formatBytes(a)
check(single.isNotEmpty())
```

### 16. 无头部流

```kotlin
import com.mingliqiye.document.format.smile.SmileConfig
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.smile.SmileReadFeature
import com.mingliqiye.document.format.smile.SmileWriteFeature

val headerlessConfig = SmileConfig.builder()
    .disable(SmileWriteFeature.WRITE_HEADER)   // 不写 4 字节头部
    .disable(SmileReadFeature.REQUIRE_HEADER)  // 读取无头部输入
    .build()
val headerlessMapper = SmileMapper(headerlessConfig)
val bytes = headerlessMapper.formatBytes(user)
check((bytes[0].toInt() and 0xFF) != 0x3A)
check(headerlessMapper.parse(bytes, User::class.java).name == "小于")
```

### 17. 共享字符串值与严格重复字段名

```kotlin
import com.mingliqiye.document.format.smile.SmileConfig
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.smile.SmileReadFeature
import com.mingliqiye.document.format.smile.SmileWriteFeature

val sharedConfig = SmileConfig.builder()
    .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES) // 重复短字符串反向引用
    .enable(SmileReadFeature.STRICT_DUPLICATE_DETECTION)  // 重复字段名报错
    .build()
val sharedMapper = SmileMapper(sharedConfig)

val sharedBytes = sharedMapper.formatBytes(listOf(user, user, user))
val restored = sharedMapper.parse(sharedBytes, com.mingliqiye.document.core.Node::class.java)
check(restored.isArray && restored.asArray().size == 3)
```

### 18. 结束标记与宽松 UTF 编码

```kotlin
import com.mingliqiye.document.format.smile.SmileConfig
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.smile.SmileWriteFeature

val markedMapper = SmileMapper(
    SmileConfig.builder()
        .enable(SmileWriteFeature.WRITE_END_MARKER)   // 关闭写出器时追加 0xFF
        .enable(SmileWriteFeature.LENIENT_UTF_ENCODING) // 非法代理项替换为 U+FFFD
        .build(),
)
val marked = markedMapper.formatBytes(user)
check(marked.last() == 0xFF.toByte())
check(markedMapper.parse(marked, User::class.java).name == "小于")
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 文本 API（`parse(String)` / `format`） | `DocumentSerializationException`（只支持二进制） |
| `formatPretty` | `DocumentSerializationException`（二进制格式不支持 pretty） |
| 单值入口遇到多个根值 | `SmileParseException`（请用 `parseAll`） |
| 无根值 | `SmileParseException` |
| 多消息流任一项非法 | 整体 `SmileParseException`，不返回部分结果 |
| NaN / Infinity | `SmileParseException`；`NumberNode` 也无法表示 |
| `BinaryNode` | 依赖 参考实现 Smile 扩展，不能降级为 Base64 字符串 |
| Smile 头部 | 默认写出/要求头部；`WRITE_HEADER` / `REQUIRE_HEADER` 可关闭以读写无头部流 |
| 对象重复字段名 | 默认以后出现的值为准；`STRICT_DUPLICATE_DETECTION` 开启时抛 `SmileParseException` |
| 结束标记 | `WRITE_END_MARKER` 开启时在字节末尾追加 `0xFF`，读取器视为 EOF |
| 非法 UTF-16 代理项 | 默认写出抛 `SmileException`；`LENIENT_UTF_ENCODING` 开启时替换为 U+FFFD |
| 配置一致性 | 关闭 `WRITE_HEADER` 时要求 `CHECK_SHARED_STRING_VALUES` 关闭、`ENCODE_BINARY_AS_7BIT` 开启，否则 `build()` 抛 `IllegalArgumentException` |
| 流式写出与结束标记 | `format(value, OutputStream)` 只 flush 不 close，`WRITE_END_MARKER` 仅在 `formatBytes` / `formatAll` / Node 写出路径生效 |
| 通用 `maxNodeDepth` | Node 回退路径、`SmileReader`、`parseAll*` 生效；默认单值 Token 快路径不构建 Node 树，不执行该限制 |
| 多消息流 | 整段解析，无逐项/流式增量 API |
| 未支持的 参考实现 特性 | 仅暴露 Smile 专属 `SmileParser.Feature` / `SmileGenerator.Feature` 与 `STRICT_DUPLICATE_DETECTION`；面向 JSON 文本的通用特性（注释、引号/转义、`WRITE_NUMBERS_AS_STRINGS`、`WRITE_BIGDECIMAL_AS_PLAIN` 等）不适用于 Smile 二进制 |
| 自定义 Factory | 暂不提供注入自定义 `SmileFactory` 的入口，如需扩展请通过 `SmileConfig` |

## 与通用 `DocumentMapper` 的差异

- 继承 `BinaryDocumentMapper`：仅二进制 API，文本/字符流 API 抛 `DocumentSerializationException`；
- `parse(ByteArray)` / `formatBytes`：默认 Token 直读/直写，不构建 Node 树；
- 多消息流：`parseAllToNodes` / `parseAll` / `formatAll`、`SmileSequenceReader` / `SmileSequenceWriter`、`ByteArray.toSmileNodes()` / `Collection<Node>.toSmileSequenceBytes()`；
- 公开的 Node 级工具：`SmileReader`、`SmileWriter`；
- 格式专属配置类 `SmileConfig` / `SmileConfigBuilder`：读写特性对齐 Smile 底层库，默认值一致；传入普通 `DocumentMapperConfig` 时使用 参考实现 默认特性。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-smile 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-smile/README.md)
