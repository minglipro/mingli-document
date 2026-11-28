---
title: 核心概念
---

# 核心概念

> 本页讲清五件事：**Node 树**、**DocumentMapper**、**Node 与 Bean 的分工**、
> **序列化/反序列化路径**、**Token 流**。读完应当能准确判断一个需求该用哪种 API。

## 目录

- [三层模型](#三层模型)
- [Node 类型总览](#node-类型总览)
- [Node 接口能力](#node-接口能力)
- [各节点类型详解](#各节点类型详解)
- [Node 的不可变性](#node-的不可变性)
- [Node 与 Bean 如何选择](#node-与-bean-如何选择)
- [DocumentMapper 是什么](#documentmapper-是什么)
- [Mapper 类层次](#mapper-类层次)
- [文本格式与二进制格式](#文本格式与二进制格式)
- [Mapper 的生命周期与线程安全](#mapper-的生命周期与线程安全)
- [DocumentTypeReference 泛型类型](#documenttypereference-泛型类型)
- [一次解析发生了什么](#一次解析发生了什么)
- [Token 流概览](#token-流概览)
- [异常体系](#异常体系)
- [综合示例](#综合示例)

## 三层模型

```text
┌────────────────────────────────────────────────────┐
│ 1. 格式层：JsonMapper / YamlMapper / CborMapper ... │  文本 / 字节
└────────────────────────────────────────────────────┘
                       ↕ parse / format
┌────────────────────────────────────────────────────┐
│ 2. 文档树层：Node（ObjectNode / ArrayNode / 标量）  │  格式无关
└────────────────────────────────────────────────────┘
                       ↕ toNode / fromNode
┌────────────────────────────────────────────────────┐
│ 3. 类型层：Java Bean / Kotlin data class / Map ...  │  业务对象
└────────────────────────────────────────────────────┘
```

关键点：

- **Node 树是唯一的中间表示**。所有格式最终都解析成 Node，所有写出都从 Node 开始。
- **格式 Mapper 之间可互换**：同一个 Node 树可以写成 JSON、YAML、XML、CBOR……
- **实体映射与格式无关**：`serialization` 模块只负责「实体 ↔ Node」，不关心格式。

因此同一套注解、同一套配置语义可以作用于所有格式。

## Node 类型总览

`Node` 是密封接口（`sealed interface`），实现只有七种：

| 接口 | `NodeType` | 内容 | 创建方式 |
|---|---|---|---|
| `ObjectNode` | `OBJECT` | 有序字段集合 `Map<String, Node>` | `ObjectNode.of(...)` / `ObjectNode.from(map)` |
| `ArrayNode` | `ARRAY` | 有序元素列表 | `ArrayNode.of(...)` / `ArrayNode.from(iterable)` |
| `StringNode` | `STRING` | 字符串 | `StringNode.of("x")` |
| `NumberNode` | `NUMBER` | 原始数字文本 + `BigDecimal` | `NumberNode.of(1)` / `ofRaw("1.50")` |
| `BooleanNode` | `BOOLEAN` | 布尔值 | `BooleanNode.of(true)` |
| `BinaryNode` | `BINARY` | 字节数组 | `BinaryNode.of(bytes)` |
| `NullNode` | `NULL` | null | `NullNode.INSTANCE` |

`NodeType` 枚举值：

```kotlin
OBJECT, ARRAY, NUMBER, STRING, BOOLEAN, BINARY, NULL
```

不同格式映射到 Node 的方式：

| 格式概念 | Node 表示 |
|---|---|
| JSON/YAML/TOML 对象、XML 元素、Protobuf message、Avro record | `ObjectNode` |
| JSON/YAML 数组、CSV 行集合、Avro array、Protobuf repeated | `ArrayNode` |
| 字符串、日期时间（TOML/Ion timestamp） | `StringNode` |
| 整数 / 浮点 / Decimal | `NumberNode` |
| 布尔 | `BooleanNode` |
| YAML `!!binary`、CBOR byte string、Ion blob/clob、`byte[]` | `BinaryNode` |
| null | `NullNode` |

## Node 接口能力

```kotlin
public sealed interface Node {
    val type: NodeType
    val isObject: Boolean
    val isArray: Boolean
    val isNumber: Boolean
    val isString: Boolean
    val isBoolean: Boolean
    val isBinary: Boolean
    val isNull: Boolean

    fun asObject(): ObjectNode   // 类型不符抛 DocumentTypeException
    fun asArray(): ArrayNode
    fun asNumber(): NumberNode
    fun asString(): String
    fun asBoolean(): Boolean
    fun asBinary(): ByteArray

    fun asObjectOrNull(): ObjectNode?
    fun asArrayOrNull(): ArrayNode?
    fun asNumberOrNull(): NumberNode?
    fun asStringOrNull(): String?
    fun asBooleanOrNull(): Boolean?
    fun asBinaryOrNull(): ByteArray?
}
```

两种风格：

```kotlin
// 断言式：类型不符抛 DocumentTypeException
val name: String = node.asObject()["name"]!!.asString()

// 探测式：类型不符返回 null，适合处理异构数据
val age: Int? = node.asObjectOrNull()?.get("age")?.asNumberOrNull()?.toInt()
```

::: tip 异常类型
`asX()` 抛的是 `com.mingliqiye.document.exception.DocumentTypeException`，
不是 `DocumentSerializationException`。捕获时注意区分。
:::

## 各节点类型详解

### ObjectNode

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.core.NumberNode

val user = ObjectNode.of(
    "name" to StringNode.of("小于"),
    "age" to NumberNode.of(18),
)

user.size              // 2
user.keys              // [name, age]（保持插入顺序）
user.containsKey("age") // true
user["name"]           // StringNode("小于")
user.entries()         // Set<Map.Entry<String, Node>>
user.asMap()           // 不可变 Map

// 从已有 Map 创建（复制一份，保持 Map 的迭代顺序）
val copy = ObjectNode.from(user.asMap())
```

细节：

- `keys` 保持插入顺序（内部 `LinkedHashMap`）。
- `ObjectNode.of("a" to ..., "a" to ...)` 重复 key **以后出现的值为准**。
- `get` 返回 `Node?`；注意区分「key 不存在」与「key 存在但值是 `NullNode`」，
  后者请用 `containsKey` 判断。
- `ObjectNode.EMPTY` 是空对象单例。

### ArrayNode

```kotlin
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NumberNode

val numbers = ArrayNode.of(NumberNode.of(1), NumberNode.of(2))

numbers.size      // 2
numbers[0]        // NumberNode(1)
numbers.asList()  // List<Node>（不可变）
for (node in numbers) { /* ArrayNode 实现 Iterable<Node> */ }

ArrayNode.EMPTY
```

`get(index)` 越界抛 `IndexOutOfBoundsException`。

### StringNode

```kotlin
val s = StringNode.of("hello")
s.value      // "hello"
s.asString() // "hello"
```

### NumberNode

数字用 `BigDecimal` 保存精确值，同时保留**原始文本**（`raw`）：

```kotlin
import com.mingliqiye.document.core.NumberNode

val n1 = NumberNode.of(18)          // raw = "18"
val n2 = NumberNode.of(1.5)         // raw = "1.5"
val n3 = NumberNode.ofRaw("1.50")   // raw = "1.50"，value = 1.50

n3.raw            // "1.50"（写回时保留原始写法）
n3.value          // BigDecimal("1.50")
n3.toInt()        // 1（精度丢失时抛 ArithmeticException）
n3.toDouble()     // 1.5
n3.toBigDecimal() // 按原值返回
```

全部转换方法：

```text
toByte     Byte
toShort    Short
toInt      Int
toLong     Long
toBigInteger  BigInteger
toFloat    Float      可能丢失精度
toDouble   Double     可能丢失精度
toBigDecimal  BigDecimal
```

约束与语义：

- `NaN` / `Infinity` 无法表示：`NumberNode.of(Double.NaN)` 会抛 `IllegalArgumentException`，
  各二进制格式（CBOR/Smile/Avro/Smile/Protobuf）也拒绝非有限浮点。
- `equals` 按**数值**比较：`NumberNode.ofRaw("1.0") == NumberNode.ofRaw("1.00")` 为 true；
  `hashCode` 基于 `stripTrailingZeros`。
- `value` 惰性解析：解析阶段只保存 `raw`，真正需要 `BigDecimal` 时才计算。
- JSON 的 Double 写出使用最短表示（Schubfach），数字解析不丢精度。

### BooleanNode

```kotlin
val b = BooleanNode.of(true)
b.value      // true
b.asBoolean() // true
```

### BinaryNode

```kotlin
import com.mingliqiye.document.core.BinaryNode

val bytes = byteArrayOf(1, 2, 3)
val node = BinaryNode.of(bytes) // 内部复制传入数组

node.value // 每次访问都返回一份副本
node.asBinary()
```

::: warning 复制语义
`BinaryNode` 在创建时复制入参，读取 `value` 时再复制一次，避免外部修改。
大二进制对象频繁读写会有复制开销，性能敏感场景注意缓存结果。
:::

### NullNode

```kotlin
val nul = NullNode.INSTANCE // 全局单例
nul.isNull                  // true
```

JSON/Node 中「字段值为 null」与「字段不存在」是两种状态，分别用 `NullNode` 和
`get()` 返回 `null` 表示。

## Node 的不可变性

`Node` 是不可变对象：

- `ObjectNode` / `ArrayNode` 的 `asMap()` / `asList()` 返回不可修改视图；
- `BinaryNode` 输入输出都复制字节；
- 没有 `put` / `add` / `remove` 之类修改 API。

要「修改」一棵树，只能重建受影响的部分：

```kotlin
val updated = ObjectNode.from(
    node.asObject().asMap() + mapOf("age" to NumberNode.of(19)),
)
```

这带来两个好处：Node 树可以安全地跨线程共享、可以放心缓存；代价是局部更新需要
拷贝路径上的节点。

::: tip 循环引用
Node 树本身允许构造环（例如同一个 `ObjectNode` 放进自己的字段），
`toNode` / `fromNode` / `merge` 会用 `IdentityHashMap` 检测环并抛
`DocumentSerializationException`；开启 `writeReferences(true)` 时改为写出
`$ref`，不再报错。
:::

## Node 与 Bean 如何选择

| 场景 | 建议 |
|---|---|
| 结构固定、有明确业务类型 | Bean / Kotlin data class |
| 结构未知、动态字段、插件式数据 | `Node` / `Map` / `List` |
| 多格式转换、中间处理 | `Node` |
| 需要校验（JSON Schema）、脱敏、裁剪 | 解析为 `Node` 后操作 |
| 跨服务传输、强类型契约 | Bean / Protobuf / Avro |
| 只取一两个字段 | `Node` 或直接 `Map` |

同一个 Mapper 可以同时做两件事，随时互转：

```kotlin
val mapper = JsonMapper()

val node: Node = mapper.toNode(user)             // Bean -> Node
val again: User = mapper.fromNode(node, User::class.java) // Node -> Bean
val other: Order = mapper.convert(user, Order::class.java) // Bean -> Bean
```

`convert` 的实现是 `fromNode(toNode(source))`，属于「先树后类型」的转换。

## DocumentMapper 是什么

`DocumentMapperApi` 是与格式无关的映射 API；`DocumentMapper` 是它的抽象基类，所有
格式 Mapper 都继承它。可以把它理解为「面向 Node 树的 ObjectMapper」：

| 方法 | 作用 |
|---|---|
| `toNode(value)` | 实体 / 集合 / Map / 数组 / 已有 Node -> `Node` |
| `fromNode(node, Class)` | `Node` -> 实体 |
| `fromNode(node, DocumentTypeReference)` | `Node` -> 泛型实体 |
| `parse(source: String, Class)` | 格式文本 -> 实体 |
| `parse(source: String, DocumentTypeReference)` | 格式文本 -> 泛型实体 |
| `parse(source: ByteArray, Class)` | 格式字节 -> 实体 |
| `format(value): String` | 实体 -> 格式文本 |
| `formatPretty(value): String` | 实体 -> 格式化文本 |
| `formatBytes(value): ByteArray` | 实体 -> 格式字节 |
| `parse(file/path/inputStream/reader, Class)` | 文件/流 -> 实体 |
| `format(value, file/path/outputStream/writer)` | 实体 -> 文件/流 |
| `formatPretty(value, file/path/outputStream/writer)` | 实体 -> 格式化文件/流 |
| `convert(source, Class)` | 实体或 Node -> 另一种类型 |
| `merge(node, target)` / `merge(node, target, policy)` | 文档树合并进已有对象 |
| `fromTokens(reader, Class)` | Token 流 -> 实体 |
| `readFrom(valueReader, DocumentTypeReference)` | 格式流 -> 实体 |
| `formatName` | 格式名，如 `"json"`、`"yaml"` |

另外每个格式 Mapper 都有 `builder()`，返回该格式的 Builder，用于注册模块、转换器、
配置与实例化策略。

## Mapper 类层次

```text
DocumentMapperApi（接口）
      ↑
DocumentMapper（抽象类，实体 ↔ Node，文本/二进制钩子）
      ↑
 ├── JsonMapper          （文本）
 ├── YamlMapper          （文本，多文档）
 ├── XmlMapper           （文本）
 ├── PropertiesMapper    （文本）
 ├── TomlMapper          （文本）
 ├── CsvMapper           （文本，根为数组）
 ├── IonMapper           （文本 + 二进制）
 ├── CborMapper          （BinaryDocumentMapper）
 ├── SmileMapper         （BinaryDocumentMapper）
 ├── AvroMapper          （BinaryDocumentMapper，需要 Schema）
 └── ProtobufMapper      （BinaryDocumentMapper，需要 Descriptor）
```

`BinaryDocumentMapper` 在基类之上把文本 `parse(String)` / `format` 直接定义为
「不支持」，并接入字节编解码器。

## 文本格式与二进制格式

| | 文本格式 | 二进制格式 |
|---|---|---|
| 代表 | JSON、YAML、XML、Properties、TOML、CSV、Ion 文本 | CBOR、Smile、Ion 二进制、Avro、Protobuf |
| `parse(String)` / `format()` | ✅ | ❌ 抛 `DocumentSerializationException` |
| `parse(ByteArray)` / `formatBytes()` | ✅（按字符集解码/编码） | ✅ |
| `Reader` / `Writer` | ✅ | ❌ 抛 `DocumentSerializationException` |
| 文件/流 | ✅ 文本语义 | ✅ 字节语义 |

混合格式 Ion 同时支持文本与二进制：`format()` 写文本 Ion、`formatBytes()` 写二进制 Ion。

具体判断依据是 Mapper 的 `usesTextFormat`（`DocumentMapper` 默认 `true`，
`BinaryDocumentMapper` 覆盖为 `false`）。

## Mapper 的生命周期与线程安全

推荐一个应用内**按格式复用同一个 Mapper 实例**：

```kotlin
// 顶层单例，或交给依赖注入容器
val jsonMapper: JsonMapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .config(
        DocumentMapperConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .writeNulls(false)
            .build(),
    )
    .build()
```

- 配置对象（`DocumentMapperConfig` / 各格式 Config）构建后不可变；
- Mapper 内部使用 `ConcurrentHashMap` 缓存属性描述、构造器、转换器等元数据，
  多线程并发调用是安全的；
- 自定义转换器会在 Mapper 内缓存，**必须无状态**；
- 不要在多线程间共享「被序列化的实体对象」的可变状态——这是业务侧责任。

## DocumentTypeReference 泛型类型

Java 泛型在运行时被擦除，`List<User>` 的信息需要显式保留：

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference

// reified 捕获（Kotlin）
val listType = DocumentTypeReference.of<List<User>>()
val mapType = DocumentTypeReference.of<Map<String, List<User>>>()

// 显式构造
val users = DocumentTypeReference.listType(User::class.java)
val index = DocumentTypeReference.mapType(String::class.java, User::class.java)

// 从 Class / Type 构造
val ref = DocumentTypeReference.of(User::class.java)
val ref2 = DocumentTypeReference.of(someParameterizedType)
```

Java 侧没有 reified，使用静态工厂：

```java
DocumentTypeReference<List<User>> users =
        DocumentTypeReference.listType(User.class);
mapper.parse(json, users);
```

`parse` / `fromNode` / `convert` 都有接受 `DocumentTypeReference` 的重载；
Kotlin 还提供了 reified 扩展（位于 `DocumentMapperApi.Companion`），需要时导入。

## 一次解析发生了什么

以 `mapper.parse(text, User::class.java)` 为例：

1. **输入大小检查**：`maxInputSize > 0` 且文本长度超限时抛
   `DocumentSerializationException`。
2. **选择解析路径**：
   - 优先使用格式的**原生 Token Reader**（例如 `JsonTokenReader`）直读实体；
   - 格式没有 Token Reader、或配置要求 Node 语义时，先解析成 `Node` 再 `fromNode`。
3. **反序列化**：
   - 解析 `Node` 路径时，会先做 Node 树限制校验（深度/节点数/字符串长度）；
   - `resolveReferences(true)` 时先展开本地 `$ref`；
   - 按目标类型选择：标量转换、集合/Map 递归、Bean 属性/构造参数注入、
     `@DocumentCreator` / Builder / record / Kotlin data class 实例化、多态分派。
4. **错误包装**：字段级失败会带 JSON Pointer 风格路径（如 `$.address.city`），
   抛 `DocumentSerializationException`。

写出方向：

```text
format(value)
  → toNode(value)                 // 实体 -> Node
  → NodeLimits.validate(...)      // 深度/节点数/字符串长度
  → 格式写出（可 Token 流式）
```

`writeNulls(false)`、命名策略、注解等都在 **toNode** 阶段生效，因此对所有格式一致。

## Token 流概览

Node 树是「先把整份文档加载进内存」。为了减少中间对象，库还提供 Token 流：

```text
文本/字节 → DocumentTokenReader → 实体
实体 → DocumentTokenWriter → 文本/字节
```

`DocumentTokenType` 共 10 种：

```text
START_OBJECT END_OBJECT START_ARRAY END_ARRAY
FIELD_NAME STRING NUMBER BOOLEAN NULL BINARY
```

- 原生 Token Reader：JSON、YAML、XML（默认配置）、Properties、TOML、CSV、Ion、CBOR、Smile；
- 文本格式暂时没有原生实现时，用 `NodeTokenReader` 把 Node 树适配成 Token 流；
- 二进制格式（CBOR/Smile/Ion）的 Token 路径可以跳过完整 Node 树，直接映射 Bean。

详见 [Token 流式 API](/guide/token-stream.html)。

## 异常体系

```text
com.mingliqiye.exception（外部异常根，社区异常体系）
   ↑
DocumentException                com.mingliqiye.document.exception
   ├── DocumentTypeException      Node 类型断言失败
   └── 各格式异常基类（如 format-json 的 JsonException）
          ├── JsonParseException
          └── JsonSchemaException

DocumentSerializationException    com.mingliqiye.document.serialization.exception
                                  Bean 映射、限制、IO 语义错误
```

典型归属：

| 场景 | 异常 |
|---|---|
| `node.asString()` 但节点是数字 | `DocumentTypeException` |
| JSON 语法错误 | `JsonParseException`（继承 `JsonException` -> `DocumentException`） |
| 缺字段且 `MissingFieldPolicy.FAIL` | `DocumentSerializationException` |
| 超过 `maxInputSize` / `maxNodeDepth` | `DocumentSerializationException` |
| 二进制格式调用 `parse(String)` | `DocumentSerializationException` |
| XML XXE / 非法结构 | `XmlParseException` |

## 综合示例

解析 → 用 Node 操作 → 再写回另一种格式：

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.yaml.YamlMapper

val json = JsonMapper()

// 1. 解析为通用文档树
val root: Node = json.parse(
    """
    {
      "user": {"name": "小于", "age": 18},
      "tags": ["a", "b"]
    }
    """.trimIndent(),
    Node::class.java,
)

// 2. 用 Node API 读取与构造新树
val user = root.asObject()["user"]!!.asObject()
val summary = ObjectNode.of(
    "title" to root.asObject()["tags"]!!,
    "adult" to com.mingliqiye.document.core.BooleanNode.of(user["age"]!!.asNumber().toInt() >= 18),
    "ageInTenYears" to NumberNode.of(user["age"]!!.asNumber().toInt() + 10),
)

// 3. 换格式写出
val yaml = YamlMapper().formatPretty(summary)
println(yaml)
```

下一步：读 [Bean 序列化](/guide/serialization.html) 掌握实体映射的全部规则。
