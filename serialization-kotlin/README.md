# serialization-kotlin

Kotlin 类型支持模块：

- **data class**：没有无参构造器时按主构造器实例化，支持默认参数、可空参数、嵌套 data class、完整泛型集合；
- **value class**：`@JvmInline value class` 按底层值序列化/反序列化；
- **sealed class / sealed interface**：反射发现 sealed 子类并注册多态绑定，支持 `@DocumentTypeInfo` / `@DocumentTypeName`。

## Maven

```kotlin
implementation("com.mingliqiye.document:serialization-kotlin:2.5.0")
```

## 快速开始

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class User(val name: String, val age: Int)

val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

val json = mapper.format(User("小于", 18))
val user = mapper.parse(json, User::class.java)
```

## data class 支持

- data class 主构造器
- 默认参数、可空参数
- 嵌套 data class、泛型集合与 Map（`List<Int>`、`Map<String, List<Address>>` 等按元素类型反序列化）
- `@DocumentName` / `@DocumentIgnore` / `@DocumentDateTimeFormat` / `@DocumentNumberFormat` 等字段注解（写入 Kotlin 属性、getter、构造参数或 backing field 均可）
- Token 路径、Node 路径与流式路径直接实例化

## value class

### 直接构造参数（无需额外配置）

value class 作为 data class 主构造参数时自动按底层值映射：

```kotlin
@JvmInline
value class UserId(val value: String)

@JvmInline
value class Score(val points: Int)

data class Player(val id: UserId, val score: Score)

val json = mapper.format(Player(UserId("u-1"), Score(7)))
// {"id":"u-1","score":7}
val player = mapper.parse(json, Player::class.java)
```

规则：

- 引用底层类型（`String`、`UUID`、`LocalDate` 等）：自动拆箱/装箱；
- 非空原始类型底层（`Int`、`Long`、`Boolean` 等）：按 JVM 原始类型映射；
- 可空 value class（底层是引用类型）：自动支持，`null` 正常映射；
- 字段注解与 data class 普通字段一致，写在 value class 属性上即可。

### 注册编解码器（boxed 位置）

value class 出现在下列 **boxed 位置**时，JVM 上保存的是 wrapper 对象而不是底层值，
需要把 value class 传给 `kotlinModule(...)` 注册标量编解码器：

- 根对象：`mapper.format(UserId("x"))`、`mapper.parse("\"x\"", UserId::class.java)`
- 集合/Map 值/数组元素：`List<UserId>`、`Map<String, UserId>`、`Array<UserId>`
- 可空原始类型底层：`Score?`、`Boolean?` 等（JVM 表示为 wrapper）

```kotlin
val mapper = JsonMapper.builder()
    .addModule(kotlinModule(UserId::class, Score::class, Price::class))
    .build()

data class Order(
    val ids: List<UserId>,             // ["a","b"]
    val prices: Map<String, Price>,    // {"x":100}
    val scores: Array<Score>,          // [1,2]
    val bonus: Score?,                 // 5 或 null
)

mapper.format(Order(...)) // 所有 value class 都写成底层标量
mapper.parse(json, Order::class.java)
```

也可以一次注册多个 value class；同一个 Mapper 多次 `kotlinModule(...)` 会合并。

## sealed class / sealed interface

sealed 层级通过配置注册多态子类型。`kotlinSealedSubtypes(...)` 会递归展开
`sealedSubclasses`，为全部具体叶子子类生成注册项：

```kotlin
@DocumentTypeInfo(property = "kind")
sealed class Shape

@DocumentTypeName("circle")
data class Circle(val radius: Int) : Shape()

@DocumentTypeName("square")
data class Square(val side: Int) : Shape()

@DocumentTypeName("empty")
data object Empty : Shape()

val config = DocumentMapperConfig.builder()
    .kotlinSealedSubtypes(Shape::class)
    .build()

val mapper = JsonMapper.builder()
    .config(config)
    .addModule(kotlinModule())
    .build()

mapper.format(Circle(1)) // {"kind":"circle","radius":1}
mapper.parse("""{"kind":"square","side":2}""", Shape::class.java) // Square(2)
```

- 类型属性名：由基类 `@DocumentTypeInfo` 指定，默认 `type`；可用 `typeProperty(...)` 覆盖；
- 类型名：子类 `@DocumentTypeName` 优先，没有注解时用简单类名（同名时回退 JVM 全限定名）；
- `data object` / 无 `var` 的 `object` 子类反序列化会复用同一个单例；
- 支持 sealed class、sealed interface、嵌套 sealed 层级，以及 `List`/`Map` 值/根对象多态；
- 与 `serialization` 模块的多态 registry 完全兼容：`kotlinSealedSubtypes` 只是批量调用
  `DocumentMapperConfigBuilder.subtype(...)`；已有显式 registry 配置无需改动。

需要同时配置格式专属选项（例如 `JsonConfig.builder()`）时，用发现 API 手动注册：

```kotlin
val jsonConfig = JsonConfig.builder()
for (binding in kotlinSealedSubtypeBindings(Shape::class)) {
    jsonConfig.subtype(binding.baseClass, binding.typeName, binding.subtypeClass)
}
val mapper = JsonMapper.builder()
    .config(jsonConfig.build())
    .addModule(kotlinModule())
    .build()
```

## Kotlin reified 扩展

依赖 reified 类型参数的 Mapper 扩展函数统一放在本模块：

```kotlin
import com.mingliqiye.document.serialization.kotlin.parse
import com.mingliqiye.document.serialization.kotlin.fromNode
import com.mingliqiye.document.serialization.kotlin.convert
import com.mingliqiye.document.serialization.kotlin.readerFor

val user: User = mapper.parse<User>(json)
val list: List<User> = mapper.parse<List<User>>(json)

val node = mapper.readTree(json)
val fromTree: User = mapper.fromNode<User>(node)

val converted: User = mapper.convert<User>(node)
val reader: DocumentObjectReader<User> = mapper.readerFor<User>()
```

这些 API 需要添加：

```kotlin
implementation("com.mingliqiye.document:serialization-kotlin")
```

纯 Java 的 `serialization` 模块不包含 reified 扩展；非 reified 的
`parse(..., DocumentTypeReference)` / `fromNode(..., DocumentTypeReference)` 仍然可用。

## 限制与不支持场景

以下场景会抛出 `com.mingliqiye.document.serialization.exception.DocumentSerializationException`
（部分为 `IllegalArgumentException` 的配置期错误，已单独标注）：

### value class

- **boxed 位置未注册编解码器**：反序列化抛 `DocumentSerializationException`；
  序列化会退化为 wrapper 对象形态（如 `{"value":"a"}`），不要依赖该形态。
  修复方式：`kotlinModule(UserId::class, ...)`。
- **泛型 value class**（`value class Box<T>(val value: T)`）：注册时抛
  `DocumentSerializationException`，文档表示无法确定。
- **value class 嵌套 value class**：注册时抛 `DocumentSerializationException`，只支持一层。
- **value class 作为 Map 键**：`DocumentSerializationException`（Map 键只支持标量类型）。
- **非 data class 的 Kotlin Bean 中的 value class 属性**：受 JVM 访问器 mangling 影响，
  JavaBeans 只能读不能写，反序列化会跳过该属性（保持默认值）；请改用 data class。
- **自定义 `namingStrategy` 不作用于 Kotlin data class 构造参数**（原有行为）：
  构造参数始终按 Kotlin 参数名匹配，需要重命名请用 `@DocumentName`。

### sealed class / sealed interface

- **未注册的叶子子类**：序列化时抛 `DocumentSerializationException`；只扫描
  `sealedSubclasses`，不会扫描普通类。
- **非 sealed 的抽象中间子类**：无法通过反射发现其后代，`kotlinSealedSubtypes`
  直接抛 `DocumentSerializationException`；请用
  `DocumentMapperConfigBuilder.subtype(...)` 显式注册具体后代。
- **value class / enum 子类**：无法映射为多态对象节点，注册时抛
  `DocumentSerializationException`。
- **带 `var` 属性的 object 子类**：不使用单例，走"新建实例 + 填字段"回退路径，
  单例身份不保留。
- 缺失类型属性、未知类型名、类型属性不是字符串：反序列化抛
  `DocumentSerializationException`。
- 传入非 sealed 类型：`IllegalArgumentException`。

## 构建

```bash
gradle :serialization-kotlin:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
