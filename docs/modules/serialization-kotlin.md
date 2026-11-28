# serialization-kotlin 模块

Kotlin 类型支持模块，向 `serialization` 的 Mapper 注入 Kotlin 实例化策略、属性元数据
解析器和 value class 转换器，使 **data class、value class、sealed class / sealed
interface** 能像 Java Bean 一样参与映射。

- 坐标：`com.mingliqiye.document:serialization-kotlin`
- 当前版本：`2.0.0`
- 包名：`com.mingliqiye.document.serialization.kotlin`
- 目标字节码：JDK 8
- 公开依赖：`com.mingliqiye.document:serialization`（`api`）
- 实现依赖：`annotation`、`kotlin-reflect`（`implementation`）

## 职责

| 能力 | 关键类型 |
|---|---|
| data class 主构造器实例化 | `KotlinDataClassInstantiator`（`internal`，经 `kotlinModule()` 注入） |
| Kotlin 属性注解补全 | `KotlinPropertyMetadataResolver`（`internal`） |
| value class 标量编解码 | `KotlinValueClassConverter`（`internal`）、`KotlinValueClass`（`internal`） |
| sealed 子类型发现与注册 | `KotlinSealedSubtype`、`kotlinSealedSubtypeBindings(...)`、`kotlinSealedSubtypes(...)` |
| Kotlin object 单例复用 | `KotlinObjects`（`internal`） |
| 模块入口 | `kotlinModule(vararg valueClasses)` |

公开 API 只有三个 Kotlin 顶层函数/类型：

```kotlin
public fun kotlinModule(vararg valueClasses: KClass<*>): DocumentModule
public class KotlinSealedSubtype(
    public val baseClass: Class<*>,
    public val typeName: String,
    public val subtypeClass: Class<*>,
)
public fun kotlinSealedSubtypeBindings(vararg baseClasses: KClass<*>): List<KotlinSealedSubtype>
public fun DocumentMapperConfigBuilder.kotlinSealedSubtypes(
    vararg baseClasses: KClass<*>,
): DocumentMapperConfigBuilder
```

## 依赖关系

```text
serialization ◄── api ── serialization-kotlin ── implementation ── kotlin-reflect
      ▲                                                └────────── annotation
      │ api
format-json / 其他 format-*
```

- 通过 `DocumentModule`（`kotlinModule()`）注册，不修改 `serialization` 的公共 API；
- `kotlinModule()` 在 `setupModule` 中做三件事：
  1. `context.setObjectInstantiator(KotlinDataClassInstantiator)`；
  2. `context.setPropertyMetadataResolver(KotlinPropertyMetadataResolver)`；
  3. 为传入的 value class 注册 `KotlinValueClassConverter`；
- sealed 多态需要改 Mapper 配置，`DocumentModule` 无法做到，因此走
  `DocumentMapperConfigBuilder.kotlinSealedSubtypes(...)` 扩展函数。

## 快速开始

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class User(val name: String, val age: Int)

val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

val json = mapper.format(User("小于", 18))   // {"name":"小于","age":18}
val user = mapper.parse(json, User::class.java)
```

只加 `kotlinModule()` 即可支持 data class 主构造器与 sealed `object` 单例；value class
在 boxed 位置需要额外注册，sealed 层级需要额外配置（见下）。

## data class 支持

- 没有无参构造器时按**主构造器**实例化；
- 支持默认参数、可空参数、嵌套 data class；
- 泛型集合与 Map 按元素类型反序列化（`List<Int>`、`Map<String, List<Address>>` 等）；
- 支持 `@DocumentName` / `@DocumentIgnore` / `@DocumentDateTimeFormat` /
  `@DocumentNumberFormat` 等字段注解；注解可写在 Kotlin 属性、getter、构造参数或
  backing field 上（模块通过 `PropertyMetadataResolver` 补全 Kotlin 属性注解）；
- Node 路径、流式路径与 Token 路径都直接实例化；
- 主构造参数名按 Kotlin 参数名匹配（或 `@param:DocumentName` / 属性上的
  `@DocumentName`）。

```kotlin
data class Address(val city: String, val zipCode: Int)

data class User(
    @param:DocumentName("display_name") val name: String,
    val age: Int = 0,
    val address: Address? = null,
    val tags: List<String> = emptyList(),
)

val mapper = JsonMapper.builder().addModule(kotlinModule()).build()
val user = mapper.parse(
    """{"display_name":"小于","age":18,"address":{"city":"杭州","zipCode":310000}}""",
    User::class.java,
)
```

说明：

- 默认参数：文档缺少字段时使用 Kotlin 默认值（等价数据库层面「保留默认」）；
- 可空参数：文档出现 `null` 时正常映射为 `null`；
- `namingStrategy` 不作用于 Kotlin data class 构造参数（构造参数始终按 Kotlin 参数名
  匹配），重命名请用 `@DocumentName`。

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

规则（源码 `KotlinTypeModel`）：

- 引用底层类型（`String`、`UUID`、`LocalDate` 等）：自动拆箱/装箱；
- 非空原始类型底层（`Int`、`Long`、`Boolean` 等）：按 JVM 原始类型映射；
- 可空 value class（底层是引用类型）：自动支持，`null` 正常映射；
- 字段注解与 data class 普通字段一致，写在 value class 属性上即可。

### boxed 位置需要注册编解码器

value class 出现在下列 **boxed 位置**时，JVM 上保存的是 wrapper 对象而不是底层值，
必须把 value class 传给 `kotlinModule(...)`：

- 根对象：`mapper.format(UserId("x"))`、`mapper.parse("\"x\"", UserId::class.java)`；
- 集合 / Map 值 / 数组元素：`List<UserId>`、`Map<String, UserId>`、`Array<UserId>`；
- 可空原始类型底层：`Score?`、`Boolean?` 等（JVM 表示为 wrapper）。

```kotlin
val mapper = JsonMapper.builder()
    .addModule(kotlinModule(UserId::class, Score::class, Price::class))
    .build()

data class Order(
    val ids: List<UserId>,            // ["a","b"]
    val prices: Map<String, Price>,   // {"x":100}
    val scores: Array<Score>,         // [1,2]
    val bonus: Score?,                // 5 或 null
)

mapper.format(Order(...)) // 所有 value class 都写成底层标量
mapper.parse(json, Order::class.java)
```

- 可以一次注册多个 value class；
- 同一个 Mapper 多次 `kotlinModule(...)` 会合并（模块名按注册集合区分，不会被去重覆盖）；
- 注册时校验：必须是 value class（否则 `IllegalArgumentException`），且形态受支持
  （否则 `DocumentSerializationException`）。

## sealed class / sealed interface

sealed 层级通过配置注册多态子类型。`kotlinSealedSubtypes(...)` 递归展开
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

mapper.format(Circle(1))                                  // {"kind":"circle","radius":1}
mapper.parse("""{"kind":"square","side":2}""", Shape::class.java) // Square(2)
```

规则：

- 类型属性名：由基类 `@DocumentTypeInfo` 指定，默认 `type`；可用
  `typeProperty(...)` 覆盖；
- 类型名：子类 `@DocumentTypeName` 优先；没有注解时用简单类名，简单类名重复时回退
  JVM 全限定名；
- `data object` / 无 `var` 的 `object` 子类反序列化复用同一个单例（`KotlinObjects`）；
- 支持 sealed class、sealed interface、嵌套 sealed 层级，以及 `List`/`Map` 值/根对象多态；
- 与 `serialization` 的多态 registry 完全兼容：`kotlinSealedSubtypes` 只是批量调用
  `DocumentMapperConfigBuilder.subtype(...)`；已有显式 registry 配置无需改动。

### 与格式专属配置组合

需要同时配置格式专属选项（例如 `JsonConfig.builder()`）时，用发现 API 手动注册：

```kotlin
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.serialization.kotlin.kotlinModule
import com.mingliqiye.document.serialization.kotlin.kotlinSealedSubtypeBindings

val jsonConfig = JsonConfig.builder()
for (binding in kotlinSealedSubtypeBindings(Shape::class)) {
    jsonConfig.subtype(binding.baseClass, binding.typeName, binding.subtypeClass)
}

val mapper = JsonMapper.builder()
    .config(jsonConfig.build())
    .addModule(kotlinModule())
    .build()
```

`KotlinSealedSubtype` 是发现结果的数据载体：

```kotlin
public class KotlinSealedSubtype(
    public val baseClass: Class<*>,      // 多态基类/接口（JVM 类型）
    public val typeName: String,         // 文档中的类型名（@DocumentTypeName 优先）
    public val subtypeClass: Class<*>,   // 具体子类（JVM 类型）
)
```

`toString()` 形式：`baseClass.name[typeName]=subtypeClass.name`，便于日志排查。

## 配置

| 配置 | 入口 | 说明 |
|---|---|---|
| data class 基础支持 | `kotlinModule()` | 必须；启用主构造器实例化与属性注解解析 |
| value class 编解码器 | `kotlinModule(ValueClass1::class, ...)` | 仅 boxed 位置需要 |
| sealed 子类型 | `DocumentMapperConfigBuilder.kotlinSealedSubtypes(...)` 或手动 `subtype(...)` | 递归展开具体叶子子类 |
| 类型属性名 | 基类 `@DocumentTypeInfo(property = "...")` 或 `typeProperty(...)` | 默认 `type` |
| 格式专属配置 | 各格式 `XxxConfig.builder()` | 通过 `kotlinSealedSubtypeBindings` 组合 |

## 示例：完整 Kotlin 模型

```kotlin
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.annotation.DocumentTypeInfo
import com.mingliqiye.document.annotation.DocumentTypeName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.kotlin.kotlinModule

@JvmInline
value class OrderId(val value: String)

@DocumentTypeInfo(property = "status")
sealed interface OrderState {
    @DocumentTypeName("new")
    data object New : OrderState

    @DocumentTypeName("paid")
    data class Paid(val paidAt: String) : OrderState
}

data class Order(
    val id: OrderId,
    @param:DocumentName("items_count") val itemsCount: Int,
    val state: OrderState,
    val remarks: List<String>,
)

val config = DocumentMapperConfig.builder()
    .kotlinSealedSubtypes(OrderState::class)
    .build()

val mapper = JsonMapper.builder()
    .config(config)
    .addModule(kotlinModule(OrderId::class))
    .build()

val json = mapper.format(
    Order(OrderId("o-1"), 3, OrderState.Paid("2026-01-01"), listOf("a")),
)
val order = mapper.parse(json, Order::class.java)
```

## 扩展点

1. **`kotlinModule(...)`**：注册 value class 标量编解码器；多次调用可合并。
2. **`kotlinSealedSubtypeBindings(...)`**：只做「发现」，不修改配置；适合自由组合到
   任意 `DocumentMapperConfigBuilder` 子类（如 `JsonConfigBuilder`）。
3. **`kotlinSealedSubtypes(...)`**：`DocumentMapperConfigBuilder` 扩展，批量调用
   `subtype(...)`。
4. **`DocumentModule` 机制**：如果需要接管其他 Kotlin 类型（例如特殊构造器约定），
   可以在业务侧实现 `ObjectInstantiator` 并通过 `DocumentMapperBuilder.objectInstantiator`
   或自定义 `DocumentModule` 注册；`kotlinModule` 只是内置的一种实现。
5. **`PropertyMetadataResolver`**：如果业务框架有额外的注解元数据来源，可注册自己的
   resolver；与 Kotlin resolver 同时存在时，Mapper 会按构建顺序取模块上下文中的
   最后一个（`ModuleSetupContext.setPropertyMetadataResolver` 覆盖）。

## 限制与不支持场景

以下场景会抛 `com.mingliqiye.document.serialization.exception.DocumentSerializationException`
（部分为配置期 `IllegalArgumentException`，已单独标注）。

### value class

| 场景 | 行为 |
|---|---|
| boxed 位置未注册编解码器 | 反序列化抛异常；序列化退化为 wrapper 对象形态（如 `{"value":"a"}`），不要依赖 |
| 泛型 value class（`value class Box<T>(val value: T)`） | 注册时抛 `DocumentSerializationException`，文档表示无法确定 |
| value class 嵌套 value class | 注册时抛 `DocumentSerializationException`，只支持一层 |
| value class 作为 Map 键 | 抛 `DocumentSerializationException`（Map 键只支持标量类型） |
| 非 data class 的 Kotlin Bean 中的 value class 属性 | 受 JVM 访问器 mangling 影响，JavaBeans 只能读不能写，反序列化跳过该属性；请改用 data class |
| 自定义 `namingStrategy` | 不作用于 Kotlin data class 构造参数；需要重命名请用 `@DocumentName` |
| 底层类型为不可映射的复杂类型 | 按内置映射规则失败（如无转换器） |

### sealed class / sealed interface

| 场景 | 行为 |
|---|---|
| 未注册的叶子子类 | 序列化抛 `DocumentSerializationException`；只扫描 `sealedSubclasses` |
| 非 sealed 的抽象中间子类 | 无法反射发现其后代，`kotlinSealedSubtypes` 直接抛异常；请显式 `subtype(...)` |
| value class / enum 子类 | 无法映射为多态对象节点，注册时抛异常 |
| 带 `var` 属性的 object 子类 | 不使用单例，走「新建实例 + 填字段」回退路径，单例身份不保留 |
| 缺失类型属性 / 未知类型名 / 类型属性不是字符串 | 反序列化抛 `DocumentSerializationException` |
| 传入非 sealed 类型 | `IllegalArgumentException` |
| sealed 层级里没有可注册叶子 | `IllegalArgumentException` |
| 类型名重复（未注解且简单类名相同） | 自动回退 JVM 全限定名，不报错 |

### 其他

- Kotlin `object` 单例只对「带 `@Metadata` 且带自类型静态 `INSTANCE` 字段、且没有
  `var` 属性」的类型启用；其他情况走普通实例化；
- `kotlin-reflect` 是 `implementation` 依赖，不会暴露到使用方的编译类路径，但运行时需要
  （由 Gradle 的 runtime classpath 传递）；
- 模块不提供 Kotlin 协程、`@JvmRecord`、自定义 `@JvmInline` 之外的内联类等能力；
- 与 Java record 支持互不影响：Java record 由 `serialization` 模块自身处理。

## 坐标与构建

```kotlin
implementation("com.mingliqiye.document:serialization-kotlin:2.0.0")

// 使用 BOM
implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))
implementation("com.mingliqiye.document:serialization-kotlin")
```

```bash
gradle :serialization-kotlin:test
gradle :serialization-kotlin:ktlintCheck
```

相关文档：

- [serialization](./serialization.md)：Mapper、配置、多态注册表
- [注解模块](./annotation.md)：`@DocumentTypeInfo` / `@DocumentTypeName` / `@DocumentName`
- [Kotlin 数据类型指南](/guide/serialization.html)（站点指南章节）
