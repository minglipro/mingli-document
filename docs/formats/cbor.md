---
title: CBOR
description: format-cbor 模块：CborMapper、canonical 编码、tag 保真与 CBOR sequence 多值读写
---

# CBOR

`format-cbor` 提供 CBOR（RFC 8949）二进制读写，核心类型是 `CborMapper`。

CBOR 是纯二进制格式，Mapper 只实现字节 API：`parse(String)` / `format(...)` 等文本入口会抛 `DocumentSerializationException`。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:cbor:2.1.0")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `cbor` |
| 版本 | `2.1.0` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.15.3` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.cbor` |

## 特性

- **CBOR 二进制**：map / array / 文本字符串 / 字节字符串 / 整数 / 浮点 / 十进制分数 / null；
- **`BinaryNode` ↔ CBOR byte string**：字节串与文本串严格区分，Java `byte[]` 可无损往返；
- **Token 直读/直写**：默认路径直接从 CBOR 事件解码 Bean / 直接写 Token 流；
- **整数最短编码**：能放进 `Int` / `Long` 时用最短形式，更大的整数写成 bignum（tag 2/3）；
- **十进制精度**：小数写成 CBOR 十进制分数（tag 4），保留 `BigDecimal` 精度；
- **canonical 编码**：对象 key 按 UTF-8 长度升序、同长度按字节序排序；数组保持原顺序；
- **可选 tag 保真**：`CborConfig.preserveTags` 保留 CBOR tag；
- **CBOR sequence**：读写多个自描述数据项（RFC 8742）；
- **深度限制**：`maxNodeDepth` / `CborReader(maxDepth)` / `maxItems`；
- **文件/流读写**：二进制语义，`OutputStream` 可直接流式写。

## 配置：`CborConfig`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `preserveTags` | `Boolean` | `false` | 是否保留 CBOR tag（包装对象协议） |

常量：

| 常量 | 值 | 含义 |
|---|---|---|
| `CborConfig.TAG_FIELD` | `"$cborTag"` | tag 包装对象中保存 tag 整数的字段名 |
| `CborConfig.VALUE_FIELD` | `"value"` | tag 包装对象中保存实际值的字段名 |

`CborConfigBuilder` 方法（与其它格式的 Builder 不同，它只有两个配置方法）：

| 方法 | 说明 |
|---|---|
| `base(DocumentMapperConfig)` | 复用通用配置（字段转换器、缺字段策略、深度限制等） |
| `preserveTags(Boolean)` | 是否保留 CBOR tag |
| `build()` | 构建 `CborConfig` |

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig

val mapper = CborMapper(
    CborConfig.builder()
        .base(DocumentMapperConfig.builder().maxNodeDepth(64).build())
        .preserveTags(true)
        .build(),
)
```

> `CborConfigBuilder` 没有独立的 `field(...)`、`maxNodeDepth(...)` 等方法；通用选项通过 `base(DocumentMapperConfig)` 提供。

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `CborMapper()` | 默认：不带 tag 保真、Token 快路径 |
| `CborMapper(config: DocumentMapperConfig)` | 通用配置 |
| `CborMapper(config: CborConfig)` | CBOR 配置，可开启 tag 保真 |

`CborMapper.builder()` 返回 `CborMapperBuilder`，用法与其他格式一致（`addSerializer` / `addDeserializer` / `addConverter` / `addModule` / `addModules` / `config` / `objectInstantiator` / `build`）。

## 读取 API

### 二进制入口

| 方法 | 说明 |
|---|---|
| `parse(source: ByteArray, targetType)` | 单值解析；默认路径用内部 `CborTokenReader` 直读 Bean；`preserveTags=true` 或 `maxNodeDepth>0` 时回退 `CborReader` |
| `parse(file: File, targetType)` / `parse(path: Path, targetType)` | 二进制文件 → 实体 |
| `parse(input: InputStream, targetType)` | 二进制流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 Token 读取器 → 实体 |

### 不支持的文本入口

| 方法 | 行为 |
|---|---|
| `parse(source: String, targetType)` | 抛 `DocumentSerializationException("cbor mapper only supports binary input")` |
| `parse(reader: Reader, targetType)` | 抛 `DocumentSerializationException("... does not support character streams")` |

### 独立读取器

| 类/函数 | 说明 |
|---|---|
| `CborReader(source, maxDepth = 0, preserveTags = false)` | 单值读取器，`read(): Node` |
| `CborReader(source, ...)` 的 `read()` | 单值入口要求输入恰好一个顶层数据项 |
| `CborSequenceReader(source, maxDepth = 0, maxItems = 0, preserveTags = false)` | sequence 读取器，`readAll(): List<Node>` |
| `CborSequenceReader.parseAll(source, maxDepth, maxItems, preserveTags)` | 静态多值解析 |
| `ByteArray.toCborNodes()` | 扩展函数：sequence 字节 → `List<Node>` |

```kotlin
import com.mingliqiye.document.format.cbor.CborReader

val node = CborReader(mapper.formatBytes(user)).read()
```

读取类型映射：

| CBOR 类型 | Node |
|---|---|
| `null` / `undefined` | `NullNode` |
| `false` / `true` | `BooleanNode` |
| 整数（含 bignum） | `NumberNode`，保留十进制文本 `raw` |
| 半精度 / 单精度 / 双精度浮点 | `NumberNode`（Jackson 解码后的文本；NaN/Infinity 拒绝） |
| 十进制分数（tag 4） | `NumberNode`（精确 `BigDecimal`） |
| 文本字符串（含 tag 0 日期文本） | `StringNode` |
| 字节字符串 | `BinaryNode`（不会被当成文本） |
| 数组（含不定长） | `ArrayNode` |
| map（含不定长） | `ObjectNode` |

补充说明：

- 默认不保留 tag；tag 2/3（bignum）、tag 4（十进制分数）由 Jackson 语义解码；
- NaN / Infinity 无法由 `NumberNode` 表示，抛 `CborParseException`；
- CBOR map 的 key：文本 key 原样使用，整数 key 转为十进制字符串；其他类型 key 不在模块支持范围内，Jackson 解析失败时抛 `CborParseException`；
- 单值入口要求恰好一个顶层数据项：无值、多值都抛 `CborParseException`。

### Sequence 读取边界

- 空输入是合法的零项 sequence：`readAll()` 返回空列表；
- 只有一个数据项时返回长度为 1 的列表；
- 任一项格式非法或数据不完整时整体抛 `CborParseException`，不返回部分结果；
- 每个数据项独立解析，深度从 1 重新计数；
- `maxItems > 0` 时项数超过上限抛 `CborParseException`；
- 整段解析，暂未提供逐项/流式增量 API。

## 写出 API

| 方法 | 说明 |
|---|---|
| `formatBytes(value)` | 实体 → CBOR 字节；默认走内部 `CborTokenWriter` 直写，`preserveTags=true` 时走 `CborWriter` Node 写出 |
| `format(value, file / path)` | 写二进制文件 |
| `format(value, output: OutputStream)` | 二进制输出；非 pretty 模式直接流式写 Token |
| `formatCanonicalBytes(value)` | 实体 → canonical CBOR 字节（先构建 Node 树，再按 canonical key 顺序写出） |
| `formatAll(values: Iterable<Any?>): ByteArray` | 多个实体 → CBOR sequence 字节 |
| `parseAllToNodes(source, maxItems = 0)` / `parseAll(source, targetType, maxItems = 0)` | sequence 多值读取 |
| `CborWriter(canonical = false, preserveTags = false).write(node)` | 公开的 Node → CBOR 单值写出器 |
| `Node.toCborBytes(preserveTags = false)` | 扩展函数：Node → CBOR 字节 |
| `Node.toCanonicalCborBytes(preserveTags = false)` | 扩展函数：Node → canonical CBOR 字节 |
| `CborSequenceWriter(canonical = false, preserveTags = false).writeAll(nodes)` | 公开的 sequence 写出器 |
| `Collection<Node>.toCborSequenceBytes(canonical = false, preserveTags = false)` | 扩展函数：Node 集合 → sequence 字节 |

不支持：

| 方法 | 行为 |
|---|---|
| `format(value)` / `formatPretty(value)` | 抛 `DocumentSerializationException("cbor mapper only supports binary output")` |
| `format(value, writer: Writer)` | 抛异常（二进制格式不支持字符流） |
| `formatPretty(value, file / path / output)` | 抛异常（二进制格式不支持 pretty） |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `formatBytes` | ✅ | `CborTokenWriter` 直接写 Token 流 |
| `format(value, OutputStream)` | ✅ | `createStreamingTokenWriter` 返回 `CborTokenWriter`（非 pretty、非 tag 保真时） |
| `format(value, File/Path)` | ❌ | 先得到 `ByteArray` 再写文件 |
| `formatCanonicalBytes` | ❌ | 先构建 Node 树再一次性写出 |

写出细节：

- 默认 Token 直写的对象/数组使用 CBOR **不定长**形式；`CborWriter` 使用**定长**形式。两者都合法且可互相读回；
- `CborWriter` 中对象/数组按实际元素数量写成定长 CBOR；
- 数字按 `NumberNode.raw`：
  - 不含小数点/指数标记 → CBOR 整数，能放进 `Int` / `Long` 时最短编码，更大的整数写 bignum（tag 2/3）；
  - 其余 → CBOR 十进制分数（tag 4），保留 `BigDecimal` 精度；
- `StringNode` → 文本字符串；`BinaryNode` → 字节字符串；`NullNode` → `null`；
- `preserveTags=true` 时识别 tag 包装对象，先写 tag 再写 value，支持对象/数组/标量与嵌套 tag。

## CBOR sequence（多值）

CBOR sequence（RFC 8742）是零个或多个自描述 CBOR 数据项首尾相接的字节流。单值 API（`CborReader` / `CborWriter` / `formatBytes` / `parse(ByteArray)`）语义不变，多值读写使用独立 API。

```kotlin
import com.mingliqiye.document.format.cbor.CborSequenceReader
import com.mingliqiye.document.format.cbor.CborSequenceWriter
import com.mingliqiye.document.format.cbor.toCborNodes
import com.mingliqiye.document.format.cbor.toCborSequenceBytes

val mapper = CborMapper()

// 写：多个实体按顺序写成 sequence（Token 直写，不构建 Node 树）
val bytes = mapper.formatAll(listOf(userA, userB))

// 读：List<Node>
val nodes = mapper.parseAllToNodes(bytes)
val nodes2 = CborSequenceReader.parseAll(bytes)
val nodes3 = bytes.toCborNodes()

// 读：List<Bean>（每个数据项反序列化为一个实体）
val users = mapper.parseAll(bytes, User::class.java)
val typed = mapper.parseAll<User>(bytes)   // reified 扩展

// 直接读写 Node 集合
val nodeBytes = listOf(nodeA, nodeB).toCborSequenceBytes()
val more = CborSequenceWriter(preserveTags = true).writeAll(listOf(nodeA, nodeB))
```

参数与边界：

- `CborSequenceReader(source, maxDepth, maxItems, preserveTags)`：`maxDepth` 限制单个数据项的 Node 树深度（0 = 不限制）；`maxItems` 限制数据项数量（0 = 不限制）；`preserveTags` 对每一项生效；
- `CborMapper.parseAllToNodes(source, maxItems)` / `parseAll(source, type, maxItems)` 的深度限制取自 Mapper 配置的 `maxNodeDepth`；
- `CborSequenceWriter(canonical, preserveTags)` / `toCborSequenceBytes(canonical, preserveTags)`；
- 空输入是合法的零项 sequence：`parseAll` 返回空列表，`formatAll(emptyList())` 返回空字节数组；
- 只有一个数据项时返回长度为 1 的列表；
- 任一项非法时整体抛 `CborParseException`，不返回部分结果。

## Tag 保真

默认关闭；开启后使用包装对象：

```json
{"$cborTag": 100, "value": "abc"}
```

- 只有对象**恰好**包含 `$cborTag` 与 `value` 两个字段，且 tag 是 `>= 0` 的整数值时，才视为包装对象；其他形态按普通对象处理；
- 写出时使用 `CBORGenerator.writeTag` 还原 tag，支持嵌套 tag；
- 保真模式下 `parse(ByteArray)` 禁用 Token 快路径（回退 Node 解析），`formatBytes` 改为 Node 写出（`CborWriter`）；
- Bean 映射会把包装对象当普通对象处理，因此保真模式下的 Bean 映射不感知 tag。

读取侧可见性（Jackson 2.15 行为）：

- tag 0/1/24 等普通 tag 在对应值上可见，会被包装保留；
- tag 2/3（bignum）、tag 4（十进制分数）已被 Jackson 语义解码，不会包装；写出时由数字策略重新编码等价 tag；
- 同一值上叠加多个 tag 时只能看到最外层第一个（Jackson 公有 API 限制），其余 tag 丢失；
- CBOR map key 上的 tag 不保留。

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper

val mapper = CborMapper(CborConfig.builder().preserveTags(true).build())

val bytes = mapper.formatBytes(mapOf("name" to "小于"))   // 普通 map，无 tag
val node = mapper.parse(bytes, com.mingliqiye.document.core.Node::class.java)

// 手工构造带 tag 的 Node 并写出
val tagged = com.mingliqiye.document.core.ObjectNode.of(
    CborConfig.TAG_FIELD to com.mingliqiye.document.core.NumberNode.of(100),
    CborConfig.VALUE_FIELD to com.mingliqiye.document.core.StringNode.of("abc"),
)
val taggedBytes = mapper.formatBytes(tagged)     // 等价于 tag(100) + "abc"

val restored = mapper.parse(taggedBytes, com.mingliqiye.document.core.Node::class.java)
// restored 是 {"$cborTag":100,"value":"abc"} 包装对象
```

配合 canonical：

```kotlin
val canonical = mapper.formatCanonicalBytes(tagged)
val canonicalNode = mapper.parse(canonical, com.mingliqiye.document.core.Node::class.java)
```

## Canonical CBOR

```kotlin
val bytes = mapper.formatCanonicalBytes(value)
val nodeBytes = node.toCanonicalCborBytes()
```

规则：

- 对象 key 按 UTF-8 编码长度升序，同长度按字节序排序；
- 数组保持原顺序；
- 整数使用最短编码形式；
- `preserveTags=true` 时 tag 包装对象也会被识别并写出。

注意：canonical 只保证 map key 顺序与整数最短编码，不覆盖所有 RFC 8949 确定性要求（例如浮点最短形式等），不是完整的确定性编码实现。

## 示例

### 1. Bean ↔ CBOR 字节

```kotlin
import com.mingliqiye.document.format.cbor.CborMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = CborMapper()
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

### 3. 二进制字节串往返

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.ObjectNode

val node = ObjectNode.of("data" to BinaryNode.of(byteArrayOf(0, 1, 2, -1)))
val bytes = mapper.formatBytes(node)

val restored = mapper.parse(bytes, Node::class.java)
check(restored.asObject()["data"]?.isBinary == true)
check(restored.asObject()["data"]?.asBinary()?.contentEquals(byteArrayOf(0, 1, 2, -1)) == true)
```

### 4. canonical 输出

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

val node = ObjectNode.of(
    "b" to StringNode.of("2"),
    "aa" to StringNode.of("1"),
    "a" to StringNode.of("0"),
)

val canonical = mapper.formatCanonicalBytes(node)
// key 顺序：a（长度 1）、b（长度 1，字节序 a<b）、aa（长度 2）
val restored = mapper.parse(canonical, Node::class.java)
```

### 5. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.cbor"))
val fromFile = mapper.parse(File("build/user.cbor"), User::class.java)

mapper.format(user, Paths.get("build/user2.cbor"))
val fromPath = mapper.parse(Paths.get("build/user2.cbor"), User::class.java)
```

### 6. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(user, output)          // 二进制流式写
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 7. CBOR sequence 基本读写

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.cbor.toCborNodes

val a = User().apply { name = "a"; age = 1 }
val b = User().apply { name = "b"; age = 2 }

val seq: ByteArray = mapper.formatAll(listOf(a, b))
val nodes: List<Node> = mapper.parseAllToNodes(seq)
check(nodes.size == 2)

val users: List<User> = mapper.parseAll(seq, User::class.java)
check(users[1].name == "b")

val nodes2 = seq.toCborNodes()
check(nodes2.size == 2)
```

### 8. 空 sequence

```kotlin
val empty = mapper.formatAll(emptyList())
check(empty.isEmpty())
check(mapper.parseAllToNodes(empty).isEmpty())
check(mapper.parseAll(empty, User::class.java).isEmpty())
```

### 9. sequence 中任一项非法整体失败

```kotlin
import com.mingliqiye.document.format.cbor.exception.CborParseException

val valid = mapper.formatBytes(user)
val broken = valid.copyOf(valid.size - 1)     // 截断最后一项
val error = runCatching { mapper.parseAllToNodes(valid + broken) }.exceptionOrNull()
check(error is CborParseException)
```

### 10. maxItems 限制

```kotlin
import com.mingliqiye.document.format.cbor.exception.CborParseException

val seq = mapper.formatAll(listOf(a, b, a))
val error = runCatching { mapper.parseAllToNodes(seq, maxItems = 2) }.exceptionOrNull()
check(error is CborParseException)
```

### 11. Node 集合 → sequence 字节

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.cbor.toCborSequenceBytes

val nodes: List<Node> = listOf(mapper.toNode(a), mapper.toNode(b))
val bytes = nodes.toCborSequenceBytes()
check(mapper.parseAllToNodes(bytes).size == 2)
```

### 12. 深度限制

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.cbor.CborReader
import com.mingliqiye.document.format.cbor.exception.CborParseException

val nested = mapOf("a" to mapOf("b" to 1))
val bytes = mapper.formatBytes(nested)

val readerError = runCatching { CborReader(bytes, maxDepth = 1).read() }.exceptionOrNull()
check(readerError is CborParseException)

// Mapper 配置 maxNodeDepth > 0 时禁用 Token 快路径，走 Node 路径限制深度
val guarded = CborMapper(CborConfig.builder()
    .base(com.mingliqiye.document.serialization.DocumentMapperConfig.builder().maxNodeDepth(1).build())
    .build())
val mapperError = runCatching { guarded.parse(bytes, Node::class.java) }.exceptionOrNull()
println(mapperError?.message)
```

### 13. 文本 API 不支持

```kotlin
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

val error = runCatching { mapper.parse("""{"name":"x"}""", User::class.java) }.exceptionOrNull()
check(error is DocumentSerializationException)
```

### 14. Java 示例

```java
import com.mingliqiye.document.format.cbor.CborMapper;

public class CborDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        CborMapper mapper = new CborMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        byte[] bytes = mapper.formatBytes(user);
        User restored = mapper.parse(bytes, User.class);
        System.out.println(restored.name + ":" + restored.age);

        // 文件/流
        mapper.format(user, new java.io.File("build/user.cbor"));
        User fromFile = mapper.parse(new java.io.File("build/user.cbor"), User.class);
        System.out.println(fromFile.age);
    }
}
```

### 15. 不定长与定长写法

```kotlin
import com.mingliqiye.document.format.cbor.CborWriter
import com.mingliqiye.document.format.cbor.toCborBytes

// Token 直写：不定长 map/array
val tokenBytes = mapper.formatBytes(user)

// CborWriter：定长 map/array
val definiteBytes = CborWriter().write(mapper.toNode(user))
val definiteBytes2 = mapper.toNode(user).toCborBytes()

// 两者都能被读回
check(mapper.parse(tokenBytes, User::class.java).name == "小于")
check(mapper.parse(definiteBytes, User::class.java).name == "小于")
check(mapper.parse(definiteBytes2, Node::class.java) == mapper.parse(tokenBytes, Node::class.java))
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 文本 API（`parse(String)` / `format`） | `DocumentSerializationException`（只支持二进制） |
| `formatPretty` | `DocumentSerializationException`（二进制格式不支持 pretty） |
| NaN / Infinity | 读取抛 `CborParseException`；`NumberNode` 也无法表示 |
| 非字符串 map key | 文本 key 原样、整数 key 转十进制字符串；其他类型失败 |
| 无值 / 多个顶层值（单值入口） | `CborParseException` |
| 单值入口输入 sequence | `CborParseException`（请用 `CborSequenceReader`） |
| `preserveTags=false` | tag 不保留；tag 2/3/4 由 Jackson 语义解码，tag 0 日期读成字符串 |
| `preserveTags=true` | Token 快路径禁用；Bean 映射看不到 tag；多个嵌套 tag 只能保留最外层 |
| canonical 编码 | 只保证 map key 顺序与整数最短编码，不是完整 RFC 8949 确定性要求 |
| sequence 解析 | 整段解析，任一项失败整体失败；无逐项/流式增量 API |
| `CborConfigBuilder` | 只提供 `base` 与 `preserveTags`，其余通过 `base(DocumentMapperConfig)` |
| `format(value, File/Path)` | 先得到完整 `ByteArray` 再写文件 |

## 与通用 `DocumentMapper` 的差异

- 继承 `BinaryDocumentMapper`：仅二进制 API，文本/字符流 API 抛 `DocumentSerializationException`；
- 构造：`CborConfig` 专属配置类与 `CborMapper.builder()`；
- `parse(ByteArray)` / `formatBytes`：默认 Token 直读/直写（不定长 CBOR），可选 Node 路径（定长）；
- 额外能力：`formatCanonicalBytes`、CBOR sequence（`parseAllToNodes` / `parseAll` / `formatAll`、`CborSequenceReader` / `CborSequenceWriter`）、tag 保真；
- 公开的 Node 级工具：`CborReader`、`CborWriter`、`CborSequenceReader` / `CborSequenceWriter`、`Node.toCborBytes` / `Node.toCanonicalCborBytes` / `ByteArray.toCborNodes` / `Collection<Node>.toCborSequenceBytes`。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-cbor 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-cbor/README.md)
