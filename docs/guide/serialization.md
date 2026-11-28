---
title: Bean 序列化
---

# Bean 序列化

> 本页覆盖 `serialization` 与 `serialization-kotlin` 两个模块的全部映射规则：
> Bean 识别、注解、构造器 / Builder / record、Kotlin data class、命名策略、
> null 与缺字段、多态、merge、自定义转换器与模块。

## 目录

- [四个入口](#四个入口)
- [Java Bean 识别规则](#java-bean-识别规则)
- [支持的类型](#支持的类型)
- [注解总表](#注解总表)
- [字段名与命名策略](#字段名与命名策略)
- [构造器注入](#构造器注入)
- [Builder 模式](#builder-模式)
- [Java record](#java-record)
- [只读属性](#只读属性)
- [Kotlin 支持](#kotlin-支持)
- [null 与缺字段](#null-与缺字段)
- [多态类型](#多态类型)
- [合并已有对象](#合并已有对象)
- [自定义转换器](#自定义转换器)
- [DocumentModule 模块](#documentmodule-模块)
- [泛型集合与 Map](#泛型集合与-map)
- [限制与注意事项](#限制与注意事项)
- [综合示例](#综合示例)

## 四个入口

所有格式 Mapper 共享同一套映射引擎，差异只在解析/写出格式：

```kotlin
val mapper = JsonMapper()

mapper.toNode(user)                                  // Bean  -> Node
mapper.fromNode(node, User::class.java)              // Node  -> Bean
mapper.parse("""{"name":"小于"}""", User::class.java)  // 文本  -> Bean
mapper.format(user)                                  // Bean  -> 文本
mapper.convert(user, Other::class.java)              // Bean  -> Bean（内部经 Node）
```

因此本页所有规则对 11 种格式同时生效。

## Java Bean 识别规则

Mapper 为每个类型构建一次 `BeanMetadata`，属性来源按以下顺序合并（同一属性名只取
第一次出现的定义）：

1. **JavaBeans Introspector**：`getXxx()` / `isXxx()` + `setXxx(...)`；
2. **record 组件**（JDK 16+ 运行时）：`name()` 访问器；
3. **public 字段**：`public String name;` 这种直接可读写的字段（`final` public 字段只读）；
4. **流式 setter**：`setXxx(...)` 返回非 `void`（如返回 `this` 的 Builder），
   JavaBeans Introspector 不识别这种情况，Mapper 会补充写访问器。

由此可得几条实用结论：

| 你的类 | 是否可映射 |
|---|---|
| 标准 JavaBean（无参构造 + getter/setter） | ✅ |
| `public` 字段 + 无参构造 | ✅ |
| 只有 `private` 字段、没有 getter/setter | ❌ 属性不可见（record / `@DocumentCreator` 除外） |
| `final` public 字段 / 只有 getter | 可读（写出），反序列化跳过 |
| 流式 setter 的 Builder | ✅（配合 `@DocumentBuilder`） |
| 没有无参构造、没有 `@DocumentCreator`/Builder | ❌ 无法实例化 |
| 内部类、非静态嵌套类 | 需要可访问的构造器 |

::: tip 注解放哪
`@DocumentName` / `@DocumentIgnore` 等注解可以放在字段、getter、Kotlin 属性上；
Mapper 会把字段、getter（`getXxx$annotations`）等处的注解合并到同一个属性元数据，
也支持 `@AliasFor` 别名解析。
:::

## 支持的类型

反序列化时按目标类型分派：

| 目标类型 | 行为 |
|---|---|
| `String` / `CharSequence` | 字符串（读数字/布尔时会按文本尝试转换） |
| `Char` / `Character` | 单字符字符串，长度必须为 1 |
| `Boolean` / `boolean` | 布尔 |
| `Byte/Short/Int/Long/Float/Double` 及包装 | 数字，越界/精度不符抛异常 |
| `BigDecimal` / `BigInteger` | 精确数字 |
| `ByteArray` | `BinaryNode` / Base64 字符串（取决于格式与上下文） |
| 枚举 | 文档字符串 == 枚举 `name()`（大小写敏感，未知值抛异常） |
| 数组 `T[]` | `ArrayNode` |
| `Collection` / `List` / `Set` / `Queue` | `ArrayNode`；集合实现类会尝试实例化，失败则回退标准集合 |
| `Map<K, V>` | `ObjectNode`；key 支持 String、枚举、Boolean、整数、浮点、BigDecimal/BigInteger |
| 嵌套 Bean | `ObjectNode` |
| `Node` / `ObjectNode` / ... | 直接取文档树，不做类型转换 |
| `Any` / `Object` | 解析为 `Node` |

Java 类型与 Node 的对应（写出方向）：

| Java 值 | Node |
|---|---|
| `null` | `NullNode` |
| `ByteArray` | `BinaryNode` |
| `CharSequence` / `Char` | `StringNode` |
| `Boolean` | `BooleanNode` |
| `Byte/Short/Int/Long/BigInteger/BigDecimal/Float/Double` | `NumberNode`（`Float/Double` 必须是有限值） |
| 枚举 | `StringNode(value.name)` |
| `Map` | `ObjectNode`，key 用 `toString()`，`null` key 写成 `"null"` |
| `Iterable` | `ArrayNode` |
| `Array<*>` / 反射数组 | `ArrayNode` |
| 其他对象 | `ObjectNode`（Bean 规则） |

::: warning 日期与时间
`java.util.Date`、`Calendar`、`java.time.*` **不是**内置标量类型。
不标注 `@DocumentDateTimeFormat` 时会走 Bean 映射，结果不符合预期；
需要格式化的日期/时间字段请显式加注解或自定义转换器。
:::

## 注解总表

注解位于 `com.mingliqiye.document.annotation`：

| 注解 | 目标 | 作用 |
|---|---|---|
| `@DocumentName("name")` | 字段 / 属性 / getter / 构造参数 | 指定文档字段名 |
| `@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)` | 类 / 字段 / getter / 构造参数 | 类级/字段级命名策略覆盖 |
| `@DocumentInclude(DocumentIncludePolicy.NON_NULL)` | 类 / 字段 / getter / 构造参数 | 类级/字段级写出策略覆盖 |
| `@DocumentIgnore` | 字段 / 属性 / getter | 忽略该属性 |
| `@DocumentConverter(Xxx::class)` | 字段 / getter / setter | 字段级**双向**转换器 |
| `@DocumentSerializer(Xxx::class)` | 字段 / getter / setter | 字段级序列化器 |
| `@DocumentDeserializer(Xxx::class)` | 字段 / getter / setter | 字段级反序列化器 |
| `@DocumentDateTimeFormat(pattern, timezone)` | 字段 / 属性 / getter / 参数 | 日期时间格式 |
| `@DocumentNumberFormat(pattern, locale)` | 字段 / 属性 / getter / 参数 | 数字格式 |
| `@DocumentCreator` | 构造器 | 指定反序列化构造器 |
| `@DocumentBuilder` | 静态方法 | 指定 Builder 静态工厂 |
| `@DocumentBuild` | 方法 | 指定 Builder 的构建方法 |
| `@DocumentTypeInfo(property)` | 类 | 多态基类的类型属性名（默认 `type`） |
| `@DocumentTypeName("name")` | 类 | 多态子类型名 |
| `@DocumentRootName("root")` | 类 | XML 写出根元素名（仅 XML） |

示例：

```kotlin
import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName

class Profile {
    @field:DocumentName("display_name")
    var name: String = ""

    @field:DocumentIgnore
    var internalId: Long = 0
}
```

### 日期与数字格式

```kotlin
import com.mingliqiye.document.annotation.DocumentDateTimeFormat
import com.mingliqiye.document.annotation.DocumentNumberFormat
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Date

class Event {
    @field:DocumentDateTimeFormat("yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    var createdAt: LocalDateTime = LocalDateTime.now()

    @field:DocumentDateTimeFormat("yyyy-MM-dd")
    var day: Date = Date()

    @field:DocumentNumberFormat("#,##0.00", locale = "zh-CN")
    var amount: BigDecimal = BigDecimal.ZERO
}
```

支持的类型：

- `@DocumentDateTimeFormat`：`java.util.Date`、`Calendar`、`java.time.LocalDate`、
  `LocalDateTime`、`LocalTime`、`OffsetDateTime`、`ZonedDateTime`、`Instant`，
  以及 `java.sql.Date` / `java.sql.Timestamp`。
- `@DocumentNumberFormat`：`BigDecimal`、`BigInteger`、`Byte`、`Short`、`Int`、`Long`、
  `Float`、`Double`、`Number`。

两者都使用「写为字符串、读时再还原」的方式；格式非法或类型不支持会抛
`DocumentSerializationException`。

## 字段名与命名策略

优先级从高到低：

1. 字段级 `@DocumentName`
2. 构造参数上的 `@DocumentName`（`@DocumentCreator` / Kotlin data class）
3. 字段/getter 上的 `@DocumentNaming`（字段级命名策略）
4. 类上的 `@DocumentNaming`（类级命名策略）
5. `DocumentMapperConfig.namingStrategy`（默认 `AS_IS`）
6. Java 属性名

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .build(),
)

class User {
    var userName: String = ""
}

mapper.format(User().apply { userName = "x" }) // {"user_name":"x"}
```

### 类级 / 字段级命名覆盖

```kotlin
import com.mingliqiye.document.annotation.DocumentNaming
import com.mingliqiye.document.annotation.DocumentNamingStrategy
import com.mingliqiye.document.annotation.DocumentNamingStrategy.KebabCaseStrategy
import com.mingliqiye.document.annotation.DocumentNamingStrategy.SnakeCaseStrategy

@DocumentNaming(SnakeCaseStrategy)      // 类级：该类所有未显式命名的属性
class Account {
    var userName: String = ""           // user_name

    @field:DocumentNaming(KebabCaseStrategy)  // 字段级覆盖类级
    var displayName: String = ""        // display-name
}
```

- `@DocumentName` 始终最高优先级，不受 `@DocumentNaming` 影响；
- `@DocumentNaming` 支持 Java getter（`FUNCTION` target）与 `@DocumentCreator` 构造参数；
- Kotlin 使用 `@field:` / `@get:` 等 use-site target；
- 类级策略对嵌套 Bean、集合元素分别按各自声明类型生效。

`PropertyNamingStrategy` 五种取值（`DocumentNamingStrategy` 一一对应）：

| 策略 | `userName` -> | `URLValue` -> |
|---|---|---|
| `AS_IS`（默认） | `userName` | `URLValue` |
| `SNAKE_CASE` | `user_name` | `url_value` |
| `KEBAB_CASE` | `user-name` | `url-value` |
| `UPPER_CAMEL` | `UserName` | `UrlValue` |
| `LOWER_CAMEL` | `userName` | `urlValue` |

分词规则：驼峰边界、连续大写缩写末尾（`URLValue` 拆为 `URL` + `Value`）、
数字与大写字母之间，以及 `_`、`-`、空格、`.` 都视为分隔。

::: warning Kotlin data class 与命名策略
Kotlin data class 的构造参数**始终按参数名匹配**，`namingStrategy` 不作用于
构造参数（这是已记录的行为）。需要改名请用 `@DocumentName`。
:::

## 构造器注入

不可变 Java Bean 用 `@DocumentCreator` 指定反序列化构造器：

```java
import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentName;

public class User {
    private final String name;
    private final int age;

    @DocumentCreator
    public User(@DocumentName("name") String name, @DocumentName("age") int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }
}
```

规则：

- 参数名优先取参数上的 `@DocumentName`；没有注解时回退到**编译期保留的参数名**
  （Java 需要 `javac -parameters`，Kotlin 需要 `-java-parameters`）；
- 参数类型参与递归反序列化，支持泛型、嵌套 Bean、集合、Map、可空与基本类型；
- 一个类型最多一个 `@DocumentCreator`；
- 缺字段按 `MissingFieldPolicy` 处理；
- 参数名与属性同名时，参数上的 `@DocumentName` 同时用于**写出**，保证读写对称；
- 没有标注的类继续走无参构造 + setter。

## Builder 模式

不可变类且没有无参构造器时，可以用 Builder：

```java
import com.mingliqiye.document.annotation.DocumentBuild;
import com.mingliqiye.document.annotation.DocumentBuilder;

public class User {
    private final String name;

    private User(String name) { this.name = name; }

    @DocumentBuilder
    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private String name;

        public UserBuilder setName(String name) {
            this.name = name;
            return this;
        }

        @DocumentBuild
        public User build() {
            return new User(name);
        }
    }
}
```

启用条件（全部满足）：

```text
没有 @DocumentCreator 构造器
没有无参构造器
目标类存在 @DocumentBuilder 静态工厂（无参静态方法，返回 Builder）
Builder 存在 @DocumentBuild 方法，或回退到无参 build()
```

填充 Builder 时按普通 Bean 规则（无参构造 + setter/public 字段/流式 setter）。
`@DocumentBuild` 方法必须是无参实例方法，返回值可赋值给目标类型。

## Java record

record 组件访问器是 `name()` 而不是 `getName()`，Mapper 会自动识别 record 组件：

```java
public record Address(String city, int zipCode) {}

public record User(
        @DocumentName("display_name") String name,
        int age,
        Address address) {}
```

```kotlin
val node = mapper.toNode(User("小于", 18, Address("杭州", 310000)))
val user = mapper.fromNode(node, User::class.java)
```

要点：

- **需要 JDK 16+ 运行时**：`Class.isRecord()` / `RecordComponent` 从 JDK 16 开始提供；
  主代码仍按 JDK 8 目标编译，运行在 JDK 8 时该分支不执行，普通 Bean 行为不变。
- 序列化：组件按可读属性写出，支持 `@DocumentName`、`@DocumentIgnore` 与命名策略。
- 反序列化：优先 `@DocumentCreator` 构造器；没有时自动绑定 canonical constructor，
  按组件名取值（可用 `@DocumentName` 覆盖）。
- record 组件都是只读的，按构造参数注入；缺字段按 `MissingFieldPolicy` 处理。
- `@DocumentIgnore` 的组件既不写出，也不从文档读取。

## 只读属性

「只读属性」= 没有 setter、也不是可写构造参数的属性（Java `final` 字段、只有 getter 的
属性）：

- 序列化**照常写出**；
- 反序列化**直接跳过**：文档中出现该字段不会当作未知字段，即使开启
  `failOnUnknownFields` 也不报错；
- `MissingFieldPolicy.FAIL` 不会因为缺少只读字段而报错；
- record / `@DocumentCreator` 构造器 Bean 中，只读属性是构造参数，按参数注入。

```java
public final class ReadOnlyUser {
    public final String tag = "fixed";   // 无 setter：反序列化跳过

    private final String name;

    @DocumentCreator
    public ReadOnlyUser(@DocumentName("name") String name) {
        this.name = name;
    }

    public String getName() { return name; }

    @DocumentName("label")
    public String getLabel() {           // 只读属性：写出 label，读入时跳过
        return name.toUpperCase();
    }
}
```

## Kotlin 支持

### 引入模块

```kotlin
implementation("com.mingliqiye.document:serialization-kotlin:2.3.0")
```

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

data class User(val name: String, val age: Int)

val json = mapper.format(User("小于", 18))
val user = mapper.parse(json, User::class.java)
```

默认 `kotlinModule()` 提供：

- data class 主构造器实例化（无无参构造器也可以）；
- 默认参数、可空参数；
- 嵌套 data class、泛型集合与 Map（按元素类型反序列化）；
- 无 `var` 的 Kotlin `object` / `data object` 单例反序列化（配合 sealed 多态）；
- 字段注解（`@DocumentName`、`@DocumentIgnore`、`@DocumentDateTimeFormat`、
  `@DocumentNumberFormat` 等），写在属性、getter、构造参数或 backing field 上均可。

### value class

`@JvmInline value class` 作为 data class **直接构造参数**时自动按底层值映射：

```kotlin
@JvmInline
value class UserId(val value: String)

@JvmInline
value class Score(val points: Int)

data class Player(val id: UserId, val score: Score)

val json = mapper.format(Player(UserId("u-1"), Score(7))) // {"id":"u-1","score":7}
val player = mapper.parse(json, Player::class.java)
```

但 value class 出现在 **boxed 位置**（根对象、`List`/`Map`/数组元素、可空原始类型）
时，需要注册标量编解码器：

```kotlin
val mapper = JsonMapper.builder()
    .addModule(kotlinModule(UserId::class, Score::class))
    .build()

data class Order(
    val id: UserId,                 // 直接构造参数：无需注册也能工作
    val ids: List<UserId>,          // boxed：需要注册
    val scores: Array<Score>,       // boxed
    val bonus: Score?,              // 可空原始类型底层：boxed
)
```

未注册时：反序列化抛 `DocumentSerializationException`；序列化会退化为
`{"value":"a"}` 形态，不要依赖。

value class 限制：

- 泛型 value class、value class 嵌套 value class：注册时报错；
- value class 作为 Map 键：不支持；
- 非 data class 的 Kotlin Bean 中的 value class 属性受 JVM 访问器 mangling 影响，
  只能读不能写，反序列化会跳过——请改用 data class；
- 自定义 `namingStrategy` 不作用于 data class 构造参数。

### sealed class / sealed interface

```kotlin
import com.mingliqiye.document.annotation.DocumentTypeInfo
import com.mingliqiye.document.annotation.DocumentTypeName
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.kotlin.kotlinModule
import com.mingliqiye.document.serialization.kotlin.kotlinSealedSubtypes

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

mapper.format(Circle(1))                                   // {"kind":"circle","radius":1}
mapper.parse("""{"kind":"square","side":2}""", Shape::class.java) // Square(2)
```

规则：

- `kotlinSealedSubtypes(...)` 递归展开 `sealedSubclasses`，为全部具体叶子子类生成
  `subtype(...)` 注册——本质与手写 registry 相同；
- 类型属性名默认 `type`，由基类 `@DocumentTypeInfo` 指定，可用 `typeProperty(...)` 覆盖；
- 类型名优先 `@DocumentTypeName`，没有注解时用简单类名（重名回退全限定名）；
- `data object` / 无 `var` 的 `object` 子类复用单例；
- 支持 sealed class、sealed interface、嵌套 sealed、`List`/`Map` 值/根对象多态；
- 未注册的叶子子类序列化报错；非 sealed 的抽象中间子类无法反射发现后代，会报错，
  请显式 `subtype(...)` 注册具体后代；
- value class / enum 子类不能作为多态对象子类型。

## null 与缺字段

### 写出 null

`writeNulls` 默认 `true`：Bean 属性为 `null` 时写出 `null`。关闭后直接省略该字段：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .writeNulls(false)
        .build(),
)

class User { var name: String? = null }
mapper.format(User()) // {}
```

### 写出策略（@DocumentInclude）

`@DocumentInclude` 可标注在类或字段/getter 上，按属性覆盖全局 `writeNulls`：

```kotlin
import com.mingliqiye.document.annotation.DocumentInclude
import com.mingliqiye.document.annotation.DocumentIncludePolicy
import com.mingliqiye.document.annotation.DocumentIncludePolicy.NON_EMPTY

@DocumentInclude(NON_EMPTY)
class Order {
    var id: String = ""               // 空字符串不写出

    @field:DocumentInclude(DocumentIncludePolicy.ALWAYS)
    var remark: String? = null        // 总是写出 null
}
```

| 策略 | 行为 |
|---|---|
| `ALWAYS` | 总是写出（包括 `null`） |
| `NON_NULL` | `null` 不写出 |
| `NON_EMPTY` | `null`/空字符串/空集合/空 `Map`/空数组不写出 |
| `USE_DEFAULTS` | 回退到下一优先级，最终使用全局 `writeNulls` |

优先级：字段级 > 类级 > 全局 `writeNulls`；只影响序列化，反序列化不受影响；
嵌套对象与集合元素按各自类型的策略处理。

### null → 基本类型（DeserializationFeature）

默认行为：文档 `null` 赋给 Java 基本类型时使用 Java 默认值（`0` / `false`），
不抛异常。需要严格校验时开启 `FAIL_ON_NULL_FOR_PRIMITIVES`：

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig

val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .failOnNullForPrimitives(true)
        .build(),
)

class User { var age: Int = 0 }

mapper.parse("""{"age":null}""", User::class.java)
// -> DocumentSerializationException: Cannot assign null to primitive type int at $.age
```

覆盖字段注入、`@DocumentCreator` 构造参数、Builder 属性、Java record 组件与基本类型
数组元素；`fromNode` / `fromTokens` / `parse` 均生效。

### 缺少字段

`MissingFieldPolicy` 控制「文档里没有这个属性」时的行为：

| 策略 | 行为 |
|---|---|
| `KEEP_DEFAULT`（默认） | 保留对象构造后的默认值 |
| `SET_NULL` | 引用类型设为 `null`；基本类型保留默认值 |
| `SET_EMPTY` | `String` 设空串，集合/Map/数组设空集合，其他引用类型设 `null` |
| `FAIL` | 缺少可写字段时抛 `DocumentSerializationException` |

```kotlin
val strict = JsonMapper(
    DocumentMapperConfig.builder()
        .missingFieldPolicy(MissingFieldPolicy.FAIL)
        .failOnUnknownFields(true)
        .build(),
)

strict.parse("""{"name":"a"}""", User::class.java) // age 缺失 -> 抛异常
strict.parse("""{"name":"a","age":1,"x":2}""", User::class.java) // 未知字段 x -> 抛异常
```

### 未知字段

`failOnUnknownFields` 默认 `false`（忽略未知字段）；开启后遇到文档中未声明的字段抛
`DocumentSerializationException`。只读属性和 `@DocumentIgnore` 属性属于「已知字段」，
不会被当作未知字段。

## 多态类型

多态**只使用显式注册的 registry**，不会反射扫描子类：

```kotlin
import com.mingliqiye.document.annotation.DocumentTypeInfo
import com.mingliqiye.document.annotation.DocumentTypeName
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.format.json.JsonMapper

@DocumentTypeInfo("kind")
interface Animal

@DocumentTypeName("cat")
class Cat : Animal { var name: String = "" }

@DocumentTypeName("dog")
class Dog : Animal { var name: String = "" }

val config = DocumentMapperConfig.builder()
    .subtype(Animal::class.java, "cat", Cat::class.java)
    .subtype(Animal::class.java, "dog", Dog::class.java)
    .build()

val mapper = JsonMapper(config)

class Zoo { var pet: Animal? = null }

mapper.format(Zoo().apply { pet = Cat().apply { name = "咪" } })
// {"pet":{"kind":"cat","name":"咪"}}

mapper.parse("""{"pet":{"kind":"dog","name":"旺"}}""", Zoo::class.java)
```

要点：

- 字段声明类型是基类时，序列化会额外写入类型属性；反序列化按该属性查找子类；
- 缺失类型属性、未知类型名、类型属性不是字符串都会抛
  `DocumentSerializationException`；
- 子类的 `@DocumentTypeName` 优先于注册时提供的 `typeName`；注册名保留为反序列化别名；
- 声明为具体子类时不会回退到父类注册；
- 子类序列化结果必须是对象，且不能与类型属性名冲突；
- 用 `typeProperty(baseClass, "属性名")` 覆盖类型属性名。

## 合并已有对象

`merge(node, target)` 把文档树合并到**已有对象**：文档中出现的字段覆盖，未出现的字段
保留原值。

```kotlin
val user = mapper.parse(
    """{"name":"a","age":18,"address":{"city":"杭州","zipCode":310000}}""",
    User::class.java,
)

// 浅合并：address 整体替换
mapper.merge(ObjectNode.of("age" to NumberNode.of(20)), user)
// user.age == 20；name、address 保留

// 深合并：嵌套 Bean 递归合并
mapper.merge(
    ObjectNode.of("address" to ObjectNode.of("city" to StringNode.of("北京"))),
    user,
    MergePolicy.DEEP,
)
// user.address.city == "北京"，user.address.zipCode == 310000
```

`MergePolicy`：

| 常量 | `deepMerge` | `skipNulls` | 语义 |
|---|---|---|---|
| `REPLACE`（默认） | false | false | 文档字段整体替换；`null` 覆盖已有值 |
| `DEEP` | true | false | 嵌套 Bean 递归合并 |
| `SKIP_NULLS` | false | true | `null` 不覆盖已有值 |
| `DEEP_SKIP_NULLS` | true | true | 深合并 + 忽略 null |
| `MergePolicy.of(deep, skipNulls)` | 自定义 | 自定义 | 自定义组合 |

语义与限制：

- 只支持 **Node 到 Bean** 的合并；流式写出路径没有合并 API；
- `node` 必须是对象节点；目标是 `null` 或非 Bean 类型时抛 `DocumentSerializationException`；
- 可变 Bean **就地修改**并返回同一实例；
- record / `@DocumentCreator` / `@DocumentBuilder` Bean 返回**新实例**，原实例不变
  （内部用「已有对象序列化结果 + 覆盖字段」重建）；
- 集合、Map、数组与多态声明字段**始终整体替换**，不做元素级合并；
- 只读属性即使在文档中出现也会跳过；
- 未知字段默认忽略，开启 `failOnUnknownFields` 时报错；
- `MissingFieldPolicy` 不参与 merge：未出现的字段一律保留原值。

## 自定义转换器

三种接口，都位于 `com.mingliqiye.document.serialization`：

```kotlin
interface DocumentBaseSerializer<T> {
    fun getType(): DocumentTypeReference<T>
    fun serialize(value: T, context: SerializationContext): Node
}

interface DocumentBaseDeserializer<T> {
    fun getType(): DocumentTypeReference<T>
    fun deserialize(node: Node, context: DeserializationContext): T?
}

// 双向：同时实现上面两个
interface DocumentBaseConverter<T> : DocumentBaseSerializer<T>, DocumentBaseDeserializer<T>
```

上下文对象提供：

```text
mapper    当前 Mapper
property  当前字段/属性元数据（根对象或集合元素为 null）
path      当前路径，用于错误定位
```

`DocumentProperty` 提供 `declaringClass`、`name`、`type`、`documentName`、`annotations`、
`getAnnotation(...)`、`getRawType()` 等。

### 写法示例

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DeserializationContext
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.SerializationContext

class MoneyConverter : DocumentBaseConverter<Money> {
    override fun getType(): DocumentTypeReference<Money> =
        DocumentTypeReference.of(Money::class.java)

    override fun serialize(value: Money, context: SerializationContext): Node =
        StringNode.of("${value.amount}${value.currency}")

    override fun deserialize(node: Node, context: DeserializationContext): Money? =
        Money.parse(node.asString())
}
```

### 注册方式

```kotlin
val mapper = JsonMapper.builder()
    .addConverter(MoneyConverter())   // 双向
    .addSerializer(...)               // 只序列化
    .addDeserializer(...)             // 只反序列化
    .build()
```

或者字段级：

```kotlin
class Order {
    @field:DocumentConverter(MoneyConverter::class)
    var total: Money = Money.ZERO
}
```

### 优先级

```text
字段级 @DocumentSerializer / @DocumentDeserializer
  > 字段级 @DocumentConverter / @DocumentDateTimeFormat / @DocumentNumberFormat
  > Builder 全局注册的转换器
  > 内置 Bean 映射
```

- `@DocumentConverter` 只接受**双向** `DocumentBaseConverter`；单向实现请用
  `@DocumentSerializer` / `@DocumentDeserializer`（2.3.0 收紧的行为）；
- 转换器必须有**无参构造器**，或声明为 Kotlin `object`（通过 `INSTANCE` 取单例）；
- 转换器实例按类型缓存在 Mapper 内，**必须无状态**，不要在转换器里保存请求级数据；
- 内置的日期/数字格式转换器内部对 `SimpleDateFormat` / `DecimalFormat` 做了同步，
  自定义转换器若持有非线程安全对象请自行处理。

## DocumentModule 模块

`DocumentModule` 用于把一组转换器/策略打包注册（类似 Jackson 的 `Module`）：

```kotlin
import com.mingliqiye.document.serialization.DocumentModule
import com.mingliqiye.document.serialization.ModuleSetupContext

class MyModule : DocumentModule {
    override val moduleName: String = "my-module"

    override fun setupModule(context: ModuleSetupContext) {
        context.addConverter(MoneyConverter())
        context.addSerializer(...)
        context.addDeserializer(...)
        context.setObjectInstantiator(...)
        context.setPropertyMetadataResolver(...)
    }
}

val mapper = JsonMapper.builder()
    .addModule(MyModule())
    .build()
```

- 模块按 `moduleName` 去重（同名模块后者覆盖）；多个模块注册同一类型时后注册模块
  覆盖先注册模块；
- 转换器合并顺序：先收集 Builder 上直接 `addConverter` / `addSerializer` /
  `addDeserializer` 的项，再合并各模块 `setupModule` 注册的项；同一类型 key 冲突时
  **模块注册覆盖 Builder 注册**。实际使用中应避免对同一类型同时注册，以免依赖合并
  顺序；
- 模块注册的 `ObjectInstantiator` / `PropertyMetadataResolver` 优先于 Builder 上直接
  `.objectInstantiator(...)` / `.propertyMetadataResolver(...)` 设置的实例；
- 不需要模块时也可直接注册：`addConverter` / `addSerializer` / `addDeserializer`
  与 `.propertyMetadataResolver(resolver)`；
- 内置示例：`kotlinModule()` 就是通过模块注册 `ObjectInstantiator`、
  `PropertyMetadataResolver` 与 value class 转换器。

## 泛型集合与 Map

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference

data class Address(val city: String)

class User {
    var addresses: List<Address> = emptyList()
    var tags: Set<String> = emptySet()
    var scores: Map<String, List<Int>> = emptyMap()
}

val mapper = JsonMapper.builder().addModule(kotlinModule()).build()

val user = mapper.parse(
    """
    {
      "addresses": [{"city": "杭州"}],
      "tags": ["a", "b"],
      "scores": {"math": [90, 95]}
    }
    """.trimIndent(),
    User::class.java,
)

// 顶层泛型：用 DocumentTypeReference
val users = mapper.parse(
    """[{"city":"杭州"}]""",
    DocumentTypeReference.listType(Address::class.java),
)
val index = mapper.parse(
    """{"a":[1,2]}""",
    DocumentTypeReference.mapType(String::class.java, List::class.java),
)
```

嵌套在 Bean 属性里的泛型信息从 getter/setter 或字段的 `genericType` 解析；
顶层泛型必须通过 `DocumentTypeReference` 显式给出。

## 限制与注意事项

- **最大嵌套深度 128**：实体序列化内部有固定深度保护（`MAX_SERIALIZATION_DEPTH`），
  超过抛 `DocumentSerializationException`；这是安全兜底，不是可配置项。
- **循环引用**：默认 `toNode` 检测到环会抛异常；开启 `writeReferences(true)` 后写出
  `$ref`（`toNode` / `format` 走 Node 路径，会写出引用）。`format(value, OutputStream)`
  与 `formatBytes` 的 Token 流式路径不参与 `$ref` 写出，遇到环仍会抛异常。
- **immutable 类型与 merge**：record / 构造器 Bean 的 merge 返回新对象，忘记接收返回值
  会「看起来没生效」。
- **多态不自动扫描**：必须显式注册，Kotlin sealed 用 `kotlinSealedSubtypes`。
- **Map key** 序列化用 `toString()`，反序列化按声明 key 类型解析；复杂对象 key 不支持。
- **未知字段** 默认忽略，需要严格校验时开启 `failOnUnknownFields`。
- **Java record** 仅在 JDK 16+ 运行时有特殊支持。
- **Kotlin data class** 必须注册 `kotlinModule()`，否则没有无参构造器会实例化失败。

## 综合示例

```kotlin
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class Address(val city: String, val zipCode: Int)

data class User(
    val name: String,
    val age: Int? = null,
    val address: Address? = null,
    @DocumentName("vip_level") val level: Int = 0,
)

data class Response(val code: Int, val data: User)

val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .config(
        DocumentMapperConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .writeNulls(false)
            .missingFieldPolicy(MissingFieldPolicy.KEEP_DEFAULT)
            .failOnUnknownFields(false)
            .build(),
    )
    .build()

val json = """
    {
      "code": 0,
      "data": {
        "name": "小于",
        "age": 18,
        "address": {"city": "杭州", "zipCode": 310000},
        "vip_level": 3
      }
    }
""".trimIndent()

val response = mapper.parse(json, Response::class.java)
println(response.data.address?.city) // 杭州
println(mapper.formatPretty(response))
```

下一步：读 [文件与流](./io.md) 掌握真正的读写方式，以及
[配置](./configuration.md) 了解每个开关的默认值。
