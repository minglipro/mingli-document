# core 模块

`core` 是格式无关的文档树核心模块，定义整个库的中间模型：`Node` 及其子类型、
`NodeType`、以及保留泛型信息的 `DocumentTypeReference`。所有 `format-*` Mapper 都读写
这棵树，`serialization` 在这棵树与实体之间双向映射。

- 坐标：`com.mingliqiye.document:core`
- 当前版本：`2.5.0`
- 包名：`com.mingliqiye.document.core`
- 目标字节码：JDK 8
- 公开依赖：`com.mingliqiye.document:exception`（`api`）

## 职责

| 能力 | 类型 |
|---|---|
| 统一的文档节点模型 | `Node`、`ObjectNode`、`ArrayNode`、`StringNode`、`NumberNode`、`BooleanNode`、`BinaryNode`、`NullNode` |
| 节点类型判别 | `NodeType` |
| 安全的类型转换 | `Node.asXxx()` / `Node.asXxxOrNull()` |
| 泛型目标类型引用 | `DocumentTypeReference`、`TypeCapture` |
| 节点工厂 | 各节点的 `companion object` 工厂方法 |

`core` 不包含任何格式代码，也不做 Bean 映射；它只回答「这棵树长什么样、怎么安全地取值」。

## 依赖关系

```text
exception  ◄── api ──  core  ◄── api ──  schema
                        ▲
                        │ api
                 serialization
                        ▲
                        │ api
             serialization-kotlin / openapi / format-*
```

- `core` 只依赖 `exception`，用于 `Node.asObject()` 等类型不匹配时抛
  `DocumentTypeException`。
- 所有 `Node` 实现类都是模块内部的 `internal` 类（`DefaultObjectNode`、
  `DefaultArrayNode`、`DefaultStringNode`、`DefaultNumberNode`、`DefaultBooleanNode`、
  `DefaultBinaryNode`、`DefaultNullNode`），使用方只能通过接口 + 工厂方法创建节点。
- `Node` 声明为 `sealed interface`，因此 `when (node)` 可以穷尽匹配；但实现类不公开，
  外部无法新增节点类型。

## 节点类型

`NodeType` 是带字符串值的枚举，`Node.type` 返回它：

| 枚举值 | `value` | 对应节点 |
|---|---|---|
| `OBJECT` | `"object"` | `ObjectNode` |
| `ARRAY` | `"array"` | `ArrayNode` |
| `NUMBER` | `"number"` | `NumberNode` |
| `STRING` | `"string"` | `StringNode` |
| `BOOLEAN` | `"boolean"` | `BooleanNode` |
| `BINARY` | `"binary"` | `BinaryNode` |
| `NULL` | `"null"` | `NullNode` |

## Node 公共 API

`Node` 是所有节点的父接口：

| 成员 | 类型 | 说明 |
|---|---|---|
| `type` | `NodeType` | 当前节点类型 |
| `isObject` / `isArray` / `isNumber` / `isString` / `isBoolean` / `isBinary` / `isNull` | `Boolean` | 便捷类型判断（默认实现比较 `type`） |
| `asObject()` | `ObjectNode` | 非对象抛 `DocumentTypeException` |
| `asObjectOrNull()` | `ObjectNode?` | 不匹配返回 `null` |
| `asArray()` | `ArrayNode` | 非数组抛 `DocumentTypeException` |
| `asArrayOrNull()` | `ArrayNode?` | 不匹配返回 `null` |
| `asNumber()` | `NumberNode` | 非数字抛 `DocumentTypeException` |
| `asNumberOrNull()` | `NumberNode?` | 不匹配返回 `null` |
| `asString()` | `String` | 非字符串抛 `DocumentTypeException` |
| `asStringOrNull()` | `String?` | 不匹配返回 `null` |
| `asBoolean()` | `Boolean` | 非布尔抛 `DocumentTypeException` |
| `asBooleanOrNull()` | `Boolean?` | 不匹配返回 `null` |
| `asBinary()` | `ByteArray` | 非二进制抛 `DocumentTypeException` |
| `asBinaryOrNull()` | `ByteArray?` | 不匹配返回 `null` |

异常消息是固定的英文模板：

```text
Expected object but was array
Expected number but was string
```

`Node` 本身没有 `value` 属性；需要原始值时必须先转换到具体节点类型。

## ObjectNode

对象节点，字段保持**插入顺序**（底层 `LinkedHashMap`）。

| 成员 | 类型 | 说明 |
|---|---|---|
| `size` | `Int` | 字段数量 |
| `keys` | `Set<String>` | 只读、保持插入顺序 |
| `containsKey(key)` | `Boolean` | 字段存在即 `true`，值为 `null` 节点也一样 |
| `get(key)` | `Node?` | 不存在返回 `null`（注意与 `NullNode` 区分） |
| `entries()` | `Set<Map.Entry<String, Node>>` | 保持插入顺序 |
| `asMap()` | `Map<String, Node>` | 只读 Map 视图 |
| `ObjectNode.EMPTY` | `ObjectNode` | 全局空对象 |
| `ObjectNode.of(vararg Pair<String, Node>)` | `ObjectNode` | 工厂；重复 key **后出现的值覆盖** |
| `ObjectNode.from(map: Map<String, Node>)` | `ObjectNode` | 复制 Map 并保持迭代顺序 |

```kotlin
val user = ObjectNode.of(
    "name" to StringNode.of("小于"),
    "age" to NumberNode.of(18),
)

println(user["name"]?.asString())   // 小于
println(user.size)                  // 2
println(user.keys)                  // [name, age]
println("name" in user.keys)        // true
```

遍历：

```kotlin
for ((name, value) in user.entries()) {
    println("$name -> $value")
}
```

## ArrayNode

数组节点，`ArrayNode` 实现 `Iterable<Node>`。

| 成员 | 类型 | 说明 |
|---|---|---|
| `size` | `Int` | 元素数量 |
| `get(index)` | `Node` | 下标越界抛 `IndexOutOfBoundsException` |
| `asList()` | `List<Node>` | 只读 List |
| `iterator()` | `Iterator<Node>` | 支持 `for (element in array)` |
| `ArrayNode.EMPTY` | `ArrayNode` | 全局空数组 |
| `ArrayNode.of(vararg elements: Node)` | `ArrayNode` | 工厂 |
| `ArrayNode.from(elements: Iterable<Node>)` | `ArrayNode` | 复制元素并保持顺序 |

```kotlin
val tags = ArrayNode.of(StringNode.of("a"), StringNode.of("b"))
println(tags.size)          // 2
println(tags[0].asString()) // a
println(tags.map { it.asString() }) // [a, b]
```

## 标量节点

### StringNode

| 成员 | 类型 | 说明 |
|---|---|---|
| `value` | `String` | 字符串值 |
| `StringNode.of(value: String)` | `StringNode` | 工厂 |
| `asString()` | `String` | 直接返回 `value` |

### NumberNode

数字以 `BigDecimal` 保存精确值，同时用 `raw` 保留原始文本（解析阶段不立即创建
`BigDecimal`，首次访问 `value` 时才解析并缓存）。

| 成员 | 类型 | 说明 |
|---|---|---|
| `raw` | `String` | 原始数字文本，例如 `"1.50"`、`"1e3"` |
| `value` | `BigDecimal` | 精确十进制值（惰性解析） |
| `toByte()` / `toShort()` / `toInt()` / `toLong()` | 原生数值 | 优先按 `raw` 解析；溢出抛 `ArithmeticException` |
| `toBigInteger()` | `BigInteger` | 精确转换 |
| `toFloat()` / `toDouble()` | 浮点 | 可能丢失精度 |
| `toBigDecimal()` | `BigDecimal` | 同 `value` |
| `NumberNode.ofRaw(raw: String)` | `NumberNode` | 保留原始写法 |
| `NumberNode.of(value: BigDecimal)` | `NumberNode` | 用 `toPlainString()` 作为 raw |
| `NumberNode.of(Int/Long/Double/Float/BigInteger)` | `NumberNode` | 浮点 NaN/Infinity 抛 `IllegalArgumentException` |

相等性按数值比较：`NumberNode.of(1) == NumberNode.ofRaw("1.0")` 为 `true`，
`hashCode` 使用 `stripTrailingZeros()`。

```kotlin
val n = NumberNode.ofRaw("1.50")
println(n.raw)          // 1.50
println(n.value)        // 1.50
println(n.toDouble())   // 1.5
```

### BooleanNode

| 成员 | 类型 | 说明 |
|---|---|---|
| `value` | `Boolean` | 布尔值 |
| `BooleanNode.of(value: Boolean)` | `BooleanNode` | 工厂 |

### BinaryNode

| 成员 | 类型 | 说明 |
|---|---|---|
| `value` | `ByteArray` | **每次访问都返回副本** |
| `BinaryNode.of(value: ByteArray)` | `BinaryNode` | 工厂，**内部复制传入数组** |
| `asBinary()` | `ByteArray` | 同 `value`（副本） |

`BinaryNode` 用于 CBOR byte string、YAML `!!binary`、Ion blob/clob、Protobuf bytes
等二进制内容。由于输入输出都复制，调用方无法通过修改数组绕过不可变语义，但也意味着
大对象有额外拷贝成本。

### NullNode

| 成员 | 类型 | 说明 |
|---|---|---|
| `NullNode.INSTANCE` | `NullNode` | 全局唯一实例 |
| `isNull` | `Boolean` | 恒为 `true` |

`ObjectNode` 的字段可以是 `NullNode`；`get` 返回 `null` 表示**字段不存在**，
返回 `NullNode.INSTANCE` 表示**字段存在但值为 null**。

## 不可变性与相等性

- 所有节点对外只读：`keys`、`asMap()`、`asList()` 都是不可修改视图；
- 工厂方法会复制输入（`ObjectNode.from` 复制 Map，`ArrayNode.from` 复制元素列表，
  `BinaryNode.of` 复制字节数组），之后修改源容器不影响节点树；
- 结构相等：
  - `ObjectNode`：字段集合与值相等（字段顺序参与 `LinkedHashMap` 的 `equals`，
    实际比较的是 Map 内容）；
  - `ArrayNode`：元素顺序与值相等；
  - 标量：`StringNode` / `BooleanNode` 为 `data class` 语义；`NumberNode` 数值相等，
    忽略 raw 写法差异；`BinaryNode` 按字节内容比较；
  - `NullNode` 与自身相等。
- `toString()` 便于调试：`ObjectNode({name=小于})`、`ArrayNode([...])`、
  `BinaryNode(size=3)`、`null`。

## DocumentTypeReference

泛型在 JVM 上会被擦除，`DocumentTypeReference` 用于把完整的 `java.lang.reflect.Type`
带到 Mapper：

| 成员 | 说明 |
|---|---|
| `type` | 完整 `Type` |
| `getRawType()` | 去掉泛型参数后的 `Class<T>` |
| `DocumentTypeReference.of<T>()` | reified 工厂，例如 `of<List<String>>()` |
| `DocumentTypeReference.of(clazz: Class<T>)` | 按 Class 创建 |
| `DocumentTypeReference.of(type: Type)` | 按现有 `Type` 创建 |
| `DocumentTypeReference.listType(elementType)` | 构造 `List<elementType>` 引用 |
| `DocumentTypeReference.mapType(keyType, valueType)` | 构造 `Map<K, V>` 引用 |

内部由 `TypeCapture<T>` 通过匿名子类的 `genericSuperclass` 捕获泛型，并把
`WildcardType` 归一化为上界、递归重建 `ParameterizedType`。

```kotlin
val userType = DocumentTypeReference.of<User>()
val listType = DocumentTypeReference.listType(User::class.java)
val mapType = DocumentTypeReference.mapType(String::class.java, User::class.java)

val users: List<User> = mapper.parse(text, listType)
val userMap: Map<String, User> = mapper.parse(text, mapType)
```

> 说明：`SimpleParameterizedType`、`TypeCapture` 中的辅助实现是 `internal`/`@PublishedApi`，
> 不属于公开 API。

## 示例：手工构造与遍历

```kotlin
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

val document = ObjectNode.of(
    "users" to ArrayNode.of(
        ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
        ),
    ),
)

val users = document["users"]?.asArray() ?: error("missing users")
for (user in users) {
    val obj = user.asObject()
    val name = obj["name"]?.asStringOrNull()
    val age = obj["age"]?.asNumberOrNull()?.toInt()
    println("$name ($age)")
}
```

## 扩展点

`core` 本身没有 SPI：

- 不能继承 `Node`（`sealed interface`，实现类在模块内部）；
- 要表达新语义，应使用现有的 7 种节点并配合格式/序列化层扩展；
- 需要新的目标类型映射时，在 `serialization` 中实现 `ObjectInstantiator`、
  `DocumentBaseConverter` 等接口，而不是扩展 `core`；
- `NumberNode.ofRaw` 允许格式层保留原始数字文本，这是格式扩展的推荐方式。

## 限制

- 节点树不可变，没有 `put` / `remove` / `add`；修改需重新构造（`DocumentMapper.merge`
  是在实体层合并，不是修改 Node）；
- `ObjectNode` 不支持重复 key：`of` 中重复 key 以后者覆盖（解析器按格式策略处理重复 key）；
- `ObjectNode` / `ArrayNode` 只保留顺序；XML 混合内容等更复杂的结构由格式层自定义表示；
- `NumberNode` 无法表达 NaN / Infinity（`NumberNode.of(Double)` 会抛
  `IllegalArgumentException`）；格式层如需保留需自行用字符串表示；
- `BinaryNode` 每次访问都复制，大二进制场景注意性能；
- `Node` 树没有父指针、没有路径信息，错误定位由 Mapper 的路径参数完成；
- 树深度没有硬限制，限制由 `serialization` 的 `NodeLimits`
  （`maxNodeDepth` / `maxNodeCount` / `maxStringLength`）施加。

## 依赖坐标

```kotlin
// Gradle
implementation("com.mingliqiye.document:core:2.5.0")

// 或使用 BOM
implementation(platform("com.mingliqiye.document:mingli-document-bom:2.5.0"))
implementation("com.mingliqiye.document:core")
```

通常不需要单独声明 `core`：声明任一 `format-*` 或 `serialization` 模块时会由传递依赖引入。
只有「手工构造 / 遍历文档树、不涉及格式」的工具才需要直接依赖 `core`。

## 构建

```bash
gradle :core:test
gradle :core:ktlintCheck
```
