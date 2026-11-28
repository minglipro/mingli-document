---
title: Token 流式 API
---

# Token 流式 API

> Node 树要求先把整份文档加载成树，再映射成实体。Token 流把「解析」和「映射」合并成
> 一次遍历：格式产生 `START_OBJECT`、`FIELD_NAME`、`STRING` 等事件，Mapper 边读边填充
> 对象。本页讲清 Token API 的接口、覆盖范围、限制与自定义扩展方式。

## 目录

- [Token 是什么](#token-是什么)
- [Token 类型](#token-类型)
- [DocumentTokenReader](#documenttokenreader)
- [DocumentTokenWriter](#documenttokenwriter)
- [DocumentToken 工具类](#documenttoken-工具类)
- [从 Token 反序列化](#从-token-反序列化)
- [流式写出 writeTo](#流式写出-writeto)
- [NodeTokenReader 适配器](#nodetokenreader-适配器)
- [各格式 Token 覆盖范围](#各格式-token-覆盖范围)
- [Token 路径的限制](#token-路径的限制)
- [自定义 Token Writer](#自定义-token-writer)
- [自定义 Token Reader](#自定义-token-reader)
- [调试 Token 流](#调试-token-流)
- [性能建议](#性能建议)

## Token 是什么

```text
整棵加载：
  文本/字节 ──解析──▶ Node 树 ──映射──▶ 实体

Token 流：
  文本/字节 ──Tokenizer──▶ Token 事件 ──映射──▶ 实体（不构建完整 Node 树）
```

好处：

- 少一次完整的树对象分配，大对象 / 大数组时内存与 GC 压力更小；
- 二进制格式（CBOR、Smile、Ion）可以直接从字节流填充 Bean；
- 可以自己驱动 Token，实现「只读某个字段」「跳过未知大字段」等逻辑。

代价：

- Token 路径只实现「实体相关」的语义，`$ref` 解析、merge、Node 树限制等依赖 Node
  的能力不可用（详见下文限制表）；
- 自定义 Token Reader 需要自己处理格式细节。

## Token 类型

`com.mingliqiye.document.serialization.DocumentTokenType`：

| Token | 说明 | 关联值 |
|---|---|---|
| `START_OBJECT` | 开始对象 | — |
| `END_OBJECT` | 结束对象 | — |
| `START_ARRAY` | 开始数组 | — |
| `END_ARRAY` | 结束数组 | — |
| `FIELD_NAME` | 对象字段名 | `textValue` |
| `STRING` | 字符串 | `textValue` |
| `NUMBER` | 数字 | `textValue`（原始数字文本） |
| `BOOLEAN` | 布尔 | `booleanValue` |
| `NULL` | null | — |
| `BINARY` | 二进制 | `binaryValue` |

注意 `NUMBER` 保存的是**原始文本**（如 `"1.50"`、`"1e3"`），需要数值时通过
`intValue()` / `longValue()` / `doubleValue()` / `decimalValue()` 转换。

## DocumentTokenReader

```kotlin
public interface DocumentTokenReader {
    val currentToken: DocumentTokenType?   // 尚未读取或已结束为 null
    val textValue: String?                 // FIELD_NAME / STRING / NUMBER
    val booleanValue: Boolean?             // BOOLEAN
    val binaryValue: ByteArray?            // BINARY

    fun intValue(): Int
    fun longValue(): Long
    fun doubleValue(): Double
    fun decimalValue(): BigDecimal

    fun nextToken(): DocumentTokenType?    // 结束返回 null
}
```

使用模式：

```kotlin
var token = reader.nextToken()
while (token != null) {
    when (token) {
        DocumentTokenType.FIELD_NAME -> println("field: ${reader.textValue}")
        DocumentTokenType.NUMBER -> println("number: ${reader.decimalValue()}")
        DocumentTokenType.BINARY -> println("binary: ${reader.binaryValue?.size} bytes")
        else -> println(token)
    }
    token = reader.nextToken()
}
```

约定：

- 初始 `currentToken` 为 `null`，第一次 `nextToken()` 才移动到第一个 Token；
- `FIELD_NAME` 之后必然是值 Token；
- 数字 Token 的 `intValue()` 内部用 `textValue.toIntOrNull()`，非法时抛
  `IllegalStateException`。

## DocumentTokenWriter

写出接口分两层：

```kotlin
public interface DocumentValueWriter {
    fun writeNull()
    fun writeString(value: String)
    fun writeNumber(raw: String)
    fun writeInt(value: Int)
    fun writeLong(value: Long)
    fun writeDouble(value: Double)
    fun writeBoolean(value: Boolean)
    fun startObject()
    fun endObject()
    fun startArray()
    fun endArray()
    fun fieldName(name: String)
    fun writeNode(node: Node)
}

public interface DocumentTokenWriter : DocumentValueWriter {
    override fun writeNode(node: Node) { /* 按 Node 类型分派到上面的方法 */ }
    fun writeBinary(value: ByteArray)
    fun flush() { }
    override fun writeInt(value: Int) { writeNumber(value.toString()) }
    override fun writeLong(value: Long) { writeNumber(value.toString()) }
    override fun writeDouble(value: Double) { writeNumber(value.toString()) }
}
```

实现 `DocumentTokenWriter` 只需实现 10 个方法：`writeNull`、`writeString`、
`writeNumber`、`writeBoolean`、`startObject`、`endObject`、`startArray`、`endArray`、
`fieldName`、`writeBinary`。其余都有默认实现。

`DocumentMapper.writeTo(value, writer)` 是驱动入口：它遍历实体并调用 writer，
不构建完整 Node 树。

## DocumentToken 工具类

`DocumentToken` 是不可变 Token 值对象，工厂方法：

```kotlin
import com.mingliqiye.document.serialization.DocumentToken

DocumentToken.startObject()
DocumentToken.endObject()
DocumentToken.startArray()
DocumentToken.endArray()
DocumentToken.fieldName("name")
DocumentToken.string("小于")
DocumentToken.number("18")          // 原始数字文本
DocumentToken.boolean(true)
DocumentToken.nullValue()
DocumentToken.binary(byteArrayOf(1, 2, 3)) // 内部复制
```

字段与 Token 类型对应：

```text
FIELD_NAME / STRING / NUMBER  -> text
BOOLEAN                       -> booleanValue
BINARY                        -> binaryValue
```

`NodeTokenReader` 就是基于 `DocumentToken` 列表实现的适配器，可作为自定义 Reader 的
参考实现。

## 从 Token 反序列化

`DocumentMapper` 提供三个 `fromTokens` 重载：

```kotlin
public fun <T> fromTokens(reader: DocumentTokenReader, targetType: Class<T>): T
public fun <T> fromTokens(reader: DocumentTokenReader, targetType: DocumentTypeReference<T>): T
public fun fromTokens(reader: DocumentTokenReader, targetType: Type): Any?
```

语义：

1. 先调用 `reader.nextToken()`，空流抛 `DocumentSerializationException("Empty token stream")`；
2. 按目标类型消费当前值；
3. 消费完后如果还有 Token，抛 `DocumentSerializationException("Unexpected trailing token content")`。

也就是说：**一次 `fromTokens` 只反序列化一个值，且必须消费完整个流**。

```kotlin
val reader = NodeTokenReader(node)
val user = mapper.fromTokens(reader, User::class.java)
```

### readFrom

如果需要自己提供「格式级流式读取器」，用 `DocumentMapper.readFrom`：

```kotlin
public fun <T> readFrom(reader: DocumentValueReader, targetType: DocumentTypeReference<T>): T
```

`DocumentValueReader` 是比 Token 更直接的接口（`readInt()`、`beginObject()`、
`hasNextField()`、`readFieldName()`、`skipValue()`、`readNode()` 等），供格式实现
直接向 Mapper 提供字段游标。自定义格式时可以实现它来对接 Mapper。

## 流式写出 writeTo

```kotlin
public fun writeTo(value: Any?, writer: DocumentValueWriter)
```

示例（自定义 writer 记录事件）：

```kotlin
class PrintTokenWriter : DocumentTokenWriter {
    override fun writeNull() = println("NULL")
    override fun writeString(value: String) = println("STRING $value")
    override fun writeNumber(raw: String) = println("NUMBER $raw")
    override fun writeBoolean(value: Boolean) = println("BOOLEAN $value")
    override fun startObject() = println("START_OBJECT")
    override fun endObject() = println("END_OBJECT")
    override fun startArray() = println("START_ARRAY")
    override fun endArray() = println("END_ARRAY")
    override fun fieldName(name: String) = println("FIELD_NAME $name")
    override fun writeBinary(value: ByteArray) = println("BINARY ${value.size}")
}

mapper.writeTo(user, PrintTokenWriter())
```

`writeTo` 的语义与 `toNode` 一致的部分：

- `@DocumentName`、命名策略、`writeNulls`、`@DocumentIgnore`、转换器全部生效；
- 多态类型属性照常写出。

不生效的部分：

- `writeReferences`：流式路径不做对象身份引用，循环引用仍抛
  `DocumentSerializationException`；
- Node 树限制（没有树可校验）。

## NodeTokenReader 适配器

`NodeTokenReader(node)` 把一个已有 Node 树适配成 Token 流，构造时一次性生成
`List<DocumentToken>`（会占用内存）：

```kotlin
import com.mingliqiye.document.serialization.NodeTokenReader

val reader = NodeTokenReader(mapper.toNode(user))
val restored = mapper.fromTokens(reader, User::class.java)
```

用途：

- 给没有原生 Token Reader 的文本格式提供统一 Token 解码路径；
- 测试/调试 Token 消费逻辑；
- 自定义格式接入前先复用现有树路径。

各格式的 Token 回退大量使用它（见下一节）。

## 各格式 Token 覆盖范围

| 格式 | 读取 Token 路径 | 写出流式路径 | 备注 |
|---|---|---|---|
| JSON | ✅ `JsonTokenReader` | ✅ `JsonUtf8StreamWriter` | `parse(String)` / `parse(ByteArray)` 总是 Token 路径 |
| YAML | ✅ `YamlTokenReader` | 直接 Writer（Node 路径） | 配置 Node 限制时回退 `NodeTokenReader` |
| XML | ✅ `XmlTokenReader` | 直接 Writer（Node 路径） | 仅默认读取配置；属性/命名空间/有序混合内容/限制/`resolveReferences` 时回退 |
| Properties | ✅ `PropertiesTokenReader` | 回退完整 String | Reader 自身校验 Node 限制 |
| TOML | ✅ `TomlTokenReader` | 回退完整 String | 配置 Node 限制时回退 Node |
| CSV | ✅ `CsvTokenReader` | 回退完整 String | 配置 Schema / `nestedMode` 时回退 Node |
| Ion | ✅ `IonTokenReader`（二进制） | ✅ `IonTextTokenWriter`（文本流） | `preserveTypes` 时回退 Node |
| CBOR | ✅ `CborTokenReader` | ✅ `CborTokenWriter` | `preserveTags` 或 `maxNodeDepth > 0` 时回退 Node |
| Smile | ✅ `SmileTokenReader` | ✅ `SmileTokenWriter` | `formatPretty` 回退 |
| Avro | ❌ | ❌ | 必须 Schema，走 Node + wire |
| Protobuf | ❌ | ❌ | 必须 Descriptor，走 Node + wire |

::: tip 如何判断实际走了哪条路
- 二进制格式：有 Token Reader 的（CBOR/Smile/Ion）`parse(ByteArray)` 默认直读；
- 文本格式：JSON 永远 Token；YAML/XML/TOML/CBOR 在配置了 Node 限制时回退；
- 需要强制 Node 语义（例如 `$ref`）时，显式先 `parse(text, Node::class.java)`，
  再 `fromNode(...)`。
:::

## Token 路径的限制

Token 快路径不构建完整 Node 树，因此以下能力**只保证在 Node 路径可用**：

| 能力 | Node 路径 | Token 路径 |
|---|---|---|
| `maxNodeDepth` / `maxNodeCount` / `maxStringLength` | ✅ 校验 Node 树 | ⚠️ 多数格式不校验（Properties 例外；YAML/XML/TOML/CBOR 会回退 Node） |
| `resolveReferences`（本地/远程 `$ref`） | ✅ | ❌ 不做引用展开 |
| `writeReferences`（对象身份 `$ref`） | ✅（`toNode` / `format`） | ❌（`writeTo` / 流式写出） |
| `merge` | ✅ | ❌ 没有 Token 合并 API |
| 自定义转换器 | ✅ | ✅（必要时内部构建局部 Node 树） |
| `@DocumentCreator` / record / data class | ✅ | ✅（支持的格式直接按 Token 构造） |
| 多态类型 | ✅ | ✅（类型属性仍会读取） |

::: warning JSON 的 `$ref` 陷阱
`JsonMapper.parse(String)` 固定走 Token 快路径，**不会**展开 `$ref`，
即使配置了 `resolveReferences(true)`。需要解析引用时：

```kotlin
val node = json.parse(text, Node::class.java)        // 先得到含 $ref 的树
val bean = json.fromNode(node, User::class.java)     // 这一步会展开 $ref
```
:::

关于安全限制的实践建议：

- 对不可信 JSON，用 `JsonConfig.maxDepth`（词法嵌套限制）+ `maxInputSize`；
- 需要 `maxNodeCount` / `maxStringLength` 强约束时，先解析到 Node 再 `fromNode`
  （Node 路径会校验），或者使用 YAML/XML/TOML/CBOR（配置限制后自动回退 Node）；
- 各格式自带的深度限制也要开启：CBOR/Smile/Ion/Avro/Protobuf 的 `maxDepth` 构造参数。

## 自定义 Token Writer

为自定义格式（例如某种私有协议）实现 `DocumentTokenWriter`，然后复用整个实体映射：

```kotlin
import com.mingliqiye.document.serialization.DocumentTokenWriter
import com.mingliqiye.document.format.json.JsonMapper

/** 把实体遍历过程记录成自定义文本协议：{name=小于,age=18} */
class MyFormatTokenWriter : DocumentTokenWriter {
    private val out = StringBuilder()
    private val first = ArrayDeque<Boolean>()

    override fun writeNull() { out.append("null") }
    override fun writeString(value: String) { out.append(value) }
    override fun writeNumber(raw: String) { out.append(raw) }
    override fun writeBoolean(value: Boolean) { out.append(value) }

    override fun startObject() {
        out.append('{')
        first.addLast(true)
    }

    override fun endObject() {
        first.removeLast()
        out.append('}')
    }

    override fun startArray() {
        out.append('[')
        first.addLast(true)
    }

    override fun endArray() {
        first.removeLast()
        out.append(']')
    }

    override fun fieldName(name: String) {
        if (first.isNotEmpty() && first.last()) {
            first.removeLast()
            first.addLast(false)
        } else {
            out.append(',')
        }
        out.append(name).append('=')
    }

    override fun writeBinary(value: ByteArray) {
        out.append("HEX:").append(value.joinToString("") { "%02x".format(it) })
    }

    fun result(): String = out.toString()
}

fun main() {
    val mapper = JsonMapper()
    val user = User().apply {
        name = "小于"
        age = 18
    }
    val writer = MyFormatTokenWriter()
    mapper.writeTo(user, writer)
    println(writer.result())
}
```

要点：数组元素的逗号、对象字段的逗号、二进制的表示都由你决定；Mapper 只负责
按实体结构调用这些方法。`writeTo` 不校验格式合法性，写坏由实现负责。

## 自定义 Token Reader

实现 `DocumentTokenReader` 后可以直接调用 `fromTokens`：

```kotlin
import com.mingliqiye.document.serialization.DocumentToken
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.format.json.JsonMapper

class ListTokenReader(private val tokens: List<DocumentToken>) : DocumentTokenReader {
    private var index = -1
    private var current: DocumentToken? = null

    override val currentToken: DocumentTokenType? get() = current?.type
    override val textValue: String? get() = current?.text
    override val booleanValue: Boolean? get() = current?.booleanValue
    override val binaryValue: ByteArray? get() = current?.binaryValue

    override fun nextToken(): DocumentTokenType? {
        index++
        if (index >= tokens.size) {
            current = null
            return null
        }
        current = tokens[index]
        return tokens[index].type
    }
}

fun main() {
    val mapper = JsonMapper()
    val reader = ListTokenReader(
        listOf(
            DocumentToken.startObject(),
            DocumentToken.fieldName("name"),
            DocumentToken.string("小于"),
            DocumentToken.fieldName("age"),
            DocumentToken.number("18"),
            DocumentToken.endObject(),
        ),
    )
    val user = mapper.fromTokens(reader, User::class.java)
    println(user.name) // 小于
}
```

真实格式实现时，在 `nextToken()` 内部做词法分析或委托给第三方 parser，
并按需填充 `textValue` / `booleanValue` / `binaryValue`。

## 调试 Token 流

把 Node 树打印成 Token 序列，是排查映射问题最快的方法：

```kotlin
import com.mingliqiye.document.serialization.NodeTokenReader

val node = JsonMapper().toNode(user)
val reader = NodeTokenReader(node)

var token = reader.nextToken()
while (token != null) {
    val detail = when (token) {
        com.mingliqiye.document.serialization.DocumentTokenType.FIELD_NAME,
        com.mingliqiye.document.serialization.DocumentTokenType.STRING,
        com.mingliqiye.document.serialization.DocumentTokenType.NUMBER,
        -> reader.textValue
        com.mingliqiye.document.serialization.DocumentTokenType.BOOLEAN -> reader.booleanValue
        com.mingliqiye.document.serialization.DocumentTokenType.BINARY -> reader.binaryValue?.size
        else -> null
    }
    println("$token $detail")
    token = reader.nextToken()
}
```

如果「实体字段没被填充」，先看 Token 流里 `FIELD_NAME` 的拼写是否与你的
`@DocumentName`/命名策略一致，再看类型是否匹配。

## 性能建议

1. **优先用内置 Token 路径**：JSON、CBOR、Smile、Ion 二进制默认已走 Token 直读，
   不需要额外配置。
2. **不要为了「可能更快」手写 Token 逻辑**：先测 JMH（`gradle :benchmarks:jmh`）。
3. **Token 路径省内存不省 CPU**：它减少中间树分配，但每字段仍然要走类型分派
   与转换器查找；Mapper 会缓存元数据，所以**复用 Mapper** 比省树更关键。
4. **写完 flush**：`DocumentTokenWriter.flush()` 默认空实现，内置流式 writer 会
   覆盖；如果自己持有缓冲，记得在 `writeTo` 后 `flush()`。
5. **流式写出与 `writeReferences` 互斥**：需要对象身份引用时请走 `format`（Node 路径）。

下一步：[代码生成](/guide/codegen.html) 学习如何把枚举与 Bean 映射进一步下沉到
编译期/运行期生成代码。
