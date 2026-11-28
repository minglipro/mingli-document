# Token 参考

Token 是 `serialization` 暴露的**流式文档协议**：把文档表示成一维的
`START_OBJECT` / `FIELD_NAME` / 标量 / `END_OBJECT` 序列，让 Mapper 可以在不构建完整
`Node` 树的情况下直接反序列化 Bean，或把 Bean 直接写进输出流。

- 接口位置：`com.mingliqiye.document.serialization`
- 相关类型：`DocumentTokenType`、`DocumentToken`、`DocumentTokenReader`、
  `DocumentTokenWriter`、`DocumentValueReader`、`DocumentValueWriter`、`NodeTokenReader`
- 适用场景：二进制格式直读 Bean、超大文档、流式输出、自定义格式接入

## 什么时候用 Token

| 场景 | 是否用 Token |
|---|---|
| 把 JSON/CBOR 解析成 Bean | 内置 Mapper 已自动选择 Token 路径（可用时） |
| 只要 `Node` 树 | 不需要，走 Node 路径 |
| 自定义格式想直接映射 Bean | 实现 `DocumentTokenReader`，调用 `mapper.fromTokens(...)` |
| 自定义写出器/接入别的 IO | 实现 `DocumentTokenWriter`，调用 `mapper.writeTo(...)` |
| 没有原生 Token 的文本格式 | 用 `NodeTokenReader` 适配已有 `Node` |
| 需要转换器/局部 Node 兼容路径 | Mapper 内部按需读取局部 `Node` |

## Token 模型

### DocumentTokenType

```kotlin
public enum class DocumentTokenType {
    START_OBJECT,
    END_OBJECT,
    START_ARRAY,
    END_ARRAY,
    FIELD_NAME,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    BINARY,
}
```

### DocumentToken

不可变 Token 对象，构造器 `private`，通过伴生工厂创建：

| 工厂 | 类型 | 有效载荷 |
|---|---|---|
| `DocumentToken.startObject()` | `START_OBJECT` | — |
| `DocumentToken.endObject()` | `END_OBJECT` | — |
| `DocumentToken.startArray()` | `START_ARRAY` | — |
| `DocumentToken.endArray()` | `END_ARRAY` | — |
| `DocumentToken.fieldName(name)` | `FIELD_NAME` | `text` |
| `DocumentToken.string(value)` | `STRING` | `text` |
| `DocumentToken.number(raw)` | `NUMBER` | `text`（原始数字文本） |
| `DocumentToken.boolean(value)` | `BOOLEAN` | `booleanValue` |
| `DocumentToken.nullValue()` | `NULL` | — |
| `DocumentToken.binary(value)` | `BINARY` | `binaryValue`（入参复制） |

公开属性：

```kotlin
public class DocumentToken {
    public val type: DocumentTokenType
    public val text: String?
    public val booleanValue: Boolean?
    public val binaryValue: ByteArray?
}
```

约定：

- `FIELD_NAME` / `STRING` / `NUMBER` 使用 `text`；
- `BOOLEAN` 使用 `booleanValue`；
- `BINARY` 使用 `binaryValue`（内部已复制，读取时每次可能返回同一数组，Mapper 不修改）；
- 其他类型的载荷字段为 `null`。

## DocumentTokenReader 协议

```kotlin
public interface DocumentTokenReader {
    public val currentToken: DocumentTokenType?
    public val textValue: String?
    public val booleanValue: Boolean?
    public val binaryValue: ByteArray?

    public fun intValue(): Int
    public fun longValue(): Long
    public fun doubleValue(): Double
    public fun decimalValue(): BigDecimal
    public fun nextToken(): DocumentTokenType?
}
```

### 状态机约定

```text
初始状态：currentToken == null（不要读值，先 nextToken）
nextToken() 返回下一个 Token 类型；结束返回 null
字段：FIELD_NAME token 之后的下一个 token 是该字段的值
对象：START_OBJECT ( FIELD_NAME 值 )* END_OBJECT
数组：START_ARRAY value* END_ARRAY
根的 Token 流必须恰好一个值
```

### 方法语义

| 成员 | 说明 |
|---|---|
| `currentToken` | 当前 Token；未开始或已结束为 `null` |
| `textValue` | `FIELD_NAME` / `STRING` / `NUMBER` 的文本；其他类型一般为 `null` |
| `booleanValue` | `BOOLEAN` 的值 |
| `binaryValue` | `BINARY` 的值 |
| `intValue()` / `longValue()` / `doubleValue()` / `decimalValue()` | 默认实现从 `textValue` 解析；解析失败抛 `IllegalStateException("Current token is not a valid ...")` |
| `nextToken()` | 前进并返回类型；结束返回 `null` |

### Reader 的职责边界

- Reader **只负责**产生 Token，不做 Bean 映射；
- 数字使用原始文本（`NUMBER` 的 `text`），Mapper 按目标类型调用
  `intValue()` / `decimalValue()` 等；
- 二进制值用 `BINARY`，Mapper 会转成 `ByteArray` 字段 / `BinaryNode`；
- Reader 是否需要支持重复读取由实现决定；Mapper 只顺序消费一次；
- 结束后的行为：`currentToken == null`，继续调用 `nextToken()` 应返回 `null`。

## DocumentValueWriter 与 DocumentTokenWriter

### DocumentValueWriter

Mapper 的 `writeTo(value, writer)` 直接驱动这个接口，不构建完整 `Node`：

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
```

### DocumentTokenWriter

```kotlin
public interface DocumentTokenWriter : DocumentValueWriter {
    public fun flush()
    public fun writeBinary(value: ByteArray)

    override fun writeInt(value: Int) { writeNumber(value.toString()) }
    override fun writeLong(value: Long) { writeNumber(value.toString()) }
    override fun writeDouble(value: Double) { writeNumber(value.toString()) }

    override fun writeNode(node: Node) { /* 递归展开 Node */ }
}
```

- `writeBinary` 是新增的抽象方法，用于二进制值；
- 默认 `writeInt/Long/Double` 转成 `writeNumber(toString())`，实现类只需实现
  `writeNumber`；
- 默认 `writeNode` 递归展开 `ObjectNode` / `ArrayNode` / 标量 / `BinaryNode`；
- `flush()` 默认空实现；缓冲型 writer 必须覆写，否则 `format(value, OutputStream)`
  可能拿不到完整输出；
- 写出顺序必须与 Reader 协议对称。

### 写出示例（自定义 writer）

实现 `DocumentTokenWriter` 时至少覆写 `writeNull/writeString/writeNumber/writeBoolean/
startObject/endObject/startArray/endArray/fieldName` 与 `writeBinary`；`writeInt/Long/Double`
默认转 `writeNumber`，`writeNode` 有默认递归实现。可参考 `JsonUtf8StreamWriter`。

## Mapper 集成

### 反序列化：Token 优先

`DocumentMapper.parse` 的流程（源码）：

```text
parse(String):
  checkInputSize
  createTextTokenReader(source)?.let { return fromTokens(it, targetType) }
  return fromNode(parseText(source), targetType)

parse(ByteArray):
  checkInputSize
  createTokenReader(source)?.let { return fromTokens(it, targetType) }
  return fromNode(parseBytes(source), targetType)
```

格式模块通过覆写 `protected open createTextTokenReader` / `createTokenReader` 接入。
返回 `null` 时回退 Node 路径。

### 手动使用 fromTokens

```kotlin
val reader: DocumentTokenReader = MyTokenReader(tokens)
val user: User = mapper.fromTokens(reader, User::class.java)
```

- `fromTokens` 先 `nextToken()`；空流抛 `DocumentSerializationException("Empty token stream")`；
- 读完后若还有 Token，抛 `DocumentSerializationException("Unexpected trailing token content")`；
- 支持 `Class` / `DocumentTypeReference` / `Type` 三种目标。

### 序列化：writeTo

```kotlin
val writer = JsonLikeWriter(StringBuilder())
mapper.writeTo(user, writer)
```

- 不构建完整 `Node` 树；
- **不支持对象身份 `$ref`**：即使配置了 `writeReferences`，`writeTo` 仍按原行为展开，
  循环引用继续抛 `DocumentSerializationException`；需要 `$ref` 时用 `toNode` 路径；
- 转换器、多态、命名策略、`writeNulls` 等语义与 Node 路径一致（转换器可能回退到
  读取局部 `Node`）。

### 流式写出：format(value, OutputStream)

`DocumentMapper.format`：

```text
createStreamingTokenWriter(output, prettyOutputEnabled())
  ├─ 非 null：writeTo(value, writer) → writer.flush() → output.flush()
  └─ null：output.write(outputBytes(value))   // 回退：先构造完整输出
```

`formatPretty(value, OutputStream)` 同逻辑，pretty 参数为 `true`。

- `OutputStream` 由调用方关闭，库只 `flush`；
- 二进制格式的 `formatBytes` 同样可能使用 Token 写出（如 Ion 默认模式、CBOR、Smile）。

## 格式支持矩阵

下表基于各格式 Mapper 对 `createTextTokenReader` / `createTokenReader` /
`createStreamingTokenWriter` 的覆写情况（源码逐一核对）。

### 读取（Token Reader）

| 格式 | 文本输入 | 字节输入 | 回退条件 |
|---|---|---|---|
| JSON | ✅ 原生 `JsonTokenReader`（`parse(String)` 直接走 Token） | ✅ 原生（严格 UTF-8 解码） | 无（重复 key / 深度限制在 Token 读取器内实现） |
| YAML | ✅ 原生 `YamlTokenReader` | —（文本格式） | 任一 `maxNodeDepth` / `maxNodeCount` / `maxStringLength` > 0 → `NodeTokenReader`（先 `YamlReader` + 限制校验） |
| XML | ✅ 原生 `XmlTokenReader` | — | 存在节点限制或 `resolveReferences`，或 `XmlConfig` 非默认读配置 → `NodeTokenReader`（先 `XmlReader`） |
| Properties | ✅ 原生 `PropertiesTokenReader` | — | 无（限制在 Token 生成时直接校验） |
| TOML | ✅ 原生 `TomlTokenReader` | — | 任一节点限制 > 0 → `NodeTokenReader` |
| CSV | ✅ 原生 `CsvTokenReader` | — | `CsvConfig.schema != null` 或 `nestedMode != null` → `NodeTokenReader` |
| Ion | 文本输入走 Node 路径 | ✅ 原生 `IonTokenReader`（`preserveTypes=false`） | `preserveTypes=true` → 回退 Node 路径 |
| CBOR | — | ✅ 原生 `CborTokenReader` | `preserveTags=true` 或 `maxNodeDepth>0` → 回退 Node 路径 |
| Smile | — | ✅ 原生 `SmileTokenReader` | 无 |
| Avro | — | ❌ 无 Token 路径（Node 路径） | — |
| Protobuf | — | ❌ 无 Token 路径（Node 路径） | — |

### 写出（Token Writer）

| 格式 | `format(value, OutputStream)` | 说明 |
|---|---|---|
| JSON | ✅ `JsonUtf8StreamWriter` | 紧凑/pretty 均支持 |
| Ion（文本） | ✅ `IonTextTokenWriter` | 使用 `fileCharset` |
| CBOR | ✅ `CborTokenWriter` | `pretty=true` 或 `preserveTags=true` 时回退 |
| Smile | ✅ `SmileTokenWriter` | `pretty=true` 时回退 |
| XML | ✅ 覆写 `format(OutputStream)`（内部 `XmlWriter` 直写 `Appendable`） | 非 TokenWriter 实现 |
| YAML | ✅ 覆写 `format(OutputStream)`（内部 `YamlCodec` 直写 writer） | 非 TokenWriter 实现 |
| CSV / Properties / TOML | ⚠️ 回退构造完整 String | 内部 writer 支持 `Appendable`，但 Mapper 未接入 Token 写出 |
| Avro / Protobuf | ✅ 二进制 `formatBytes`（Node/OCF 路径） | 不支持文本流 |

## 内置适配器

### NodeTokenReader

```kotlin
public class NodeTokenReader(node: Node) : DocumentTokenReader
```

- 把已有 `Node` 树展开为 Token 列表，再顺序消费；
- 用于暂时没有原生流式 Token 实现的文本格式回退；
- `buildTokens` 为 `O(节点数)` 内存开销，属于兼容路径，不是性能优化路径。

### 内部 TokenTreeReader

`serialization` 内部的 `TokenTreeReader` 提供 `readNode(reader)`（把当前值读成局部
`Node`）与 `skipValue(reader)`，只用于转换器、data class 等仍依赖 `Node` 的局部兼容
路径，不对外暴露。

## 自定义实现

### 1）实现 DocumentTokenReader

```kotlin
class MapTokenReader(private val document: Node) : DocumentTokenReader {
    private val tokens: List<DocumentToken> = buildList { append(document, this) }
    private var index = -1
    private var current: DocumentToken? = null

    override val currentToken get() = current?.type
    override val textValue get() = current?.text
    override val booleanValue get() = current?.booleanValue
    override val binaryValue get() = current?.binaryValue

    override fun nextToken(): DocumentTokenType? {
        index++
        current = if (index >= tokens.size) null else tokens[index]
        return current?.type
    }

    private fun append(node: Node, out: MutableList<DocumentToken>) {
        when (node) {
            is ObjectNode -> {
                out += DocumentToken.startObject()
                node.entries().forEach { (name, value) ->
                    out += DocumentToken.fieldName(name)
                    append(value, out)
                }
                out += DocumentToken.endObject()
            }
            is ArrayNode -> {
                out += DocumentToken.startArray()
                node.forEach { append(it, out) }
                out += DocumentToken.endArray()
            }
            is StringNode -> out += DocumentToken.string(node.value)
            is NumberNode -> out += DocumentToken.number(node.raw)
            is BooleanNode -> out += DocumentToken.boolean(node.value)
            is BinaryNode -> out += DocumentToken.binary(node.value)
            is NullNode -> out += DocumentToken.nullValue()
        }
    }
}

val user = JsonMapper().fromTokens(MapTokenReader(mapper.toNode(userValue)), User::class.java)
```

要输入：根 Token 流只能有一个值；数字用原始文本；`FIELD_NAME` 后必须紧跟值；出错时抛
`DocumentSerializationException`。

### 2）让格式 Mapper 使用自定义 Reader

`createTextTokenReader` / `createTokenReader` 是 `protected open`，只能在格式 Mapper
子类中覆写；`JsonMapper` 构造器为 `internal`，自定义格式应自行实现 `DocumentMapper`
子类并覆写钩子。

### 3）实现自定义 Token Writer

实现 `DocumentTokenWriter` 后调用 `mapper.writeTo(value, writer)` 即可；缓冲型 writer
必须实现 `flush()`，`writeNode` 可使用接口默认实现。

### 4）流式实例化（DocumentValueReader）

`ObjectInstantiator` 额外提供 `instantiate(DocumentValueReader, ...)` 与
`instantiate(DocumentTokenReader, ...)` 重载，可接管流式/Token 实例化；返回 `null` 表示
不处理，Mapper 回退。`mapper.readFrom(reader, type)` 可直接消费自定义流式读取器。

## 错误与边界

| 情况 | 异常 |
|---|---|
| 空 Token 流 / 末尾有多余 Token | `DocumentSerializationException("Empty token stream")` / `("Unexpected trailing token content")` |
| 字段名后没有值 / 对象/数组未闭合 | `DocumentSerializationException("Unexpected end of object/array")` |
| 值类型与目标不匹配 | `DocumentSerializationException`（含路径） |
| `intValue()` 等用于非数字 Token | `IllegalStateException` |
| 写出循环引用（`writeTo` 路径） | `DocumentSerializationException`（不支持 `$ref` 写出） |
| 写出深度超过 128 | `DocumentSerializationException("Maximum serialization depth exceeded")` |
| 节点限制 | Token 路径通常由 Mapper/格式层校验，超限抛 `DocumentSerializationException` |

## 性能与注意事项

- Token 路径避免构建完整 `Node` 树，适合大文档和二进制格式；文本格式在「有节点限制 /
  特殊配置」时自动回退 Node 路径；
- `NodeTokenReader` 仍是「先建树再展平」，只用于兼容，不是性能优化；
- Token Reader/Writer 通常**不是线程安全**的，每次解析/写出使用独立实例；
- 写出器必须正确实现 `flush()`，且 `format(value, OutputStream)` 不会关闭流；
- Token 协议没有版本号，属于库内 SPI，自定义实现应跟随当前 `DocumentTokenType` 语义。

## 示例：二进制直读 Bean

```kotlin
import com.mingliqiye.document.format.cbor.CborMapper

val mapper = CborMapper()
val bytes = mapper.formatBytes(user)                  // 内部走 Token 写出（默认模式）
val restored = mapper.parse(bytes, User::class.java)  // 内部走 Token 读取
```

需要手动控制时，可构造格式模块公开的 `CborTokenWriter` / `CborTokenReader`，再分别调用
`mapper.writeTo(...)` 与 `mapper.fromTokens(...)`（类位于 `format-cbor`，构造细节以模块
源码为准）。

相关页面：

- [api 参考](./api.md)：Token API 签名索引
- [serialization 模块](../modules/serialization.md)：Mapper 钩子与流式写出
- [格式章节](../formats/)：各格式的 Token 支持细节
