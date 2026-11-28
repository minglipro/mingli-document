# annotation 模块

`annotation` 是格式无关的注解模块，只包含注解类型和三个标记接口，不依赖任何其他模块。
`serialization` 与各 `format-*` 模块通过反射读取这些注解，决定字段名、忽略、转换器、
多态类型、实例化方式、日期/数字格式等行为。

- 坐标：`com.mingliqiye.document:annotation`
- 当前版本：`2.5.1`
- 包名：`com.mingliqiye.document.annotation`
- 目标字节码：JDK 8
- 保留策略：除 `@DocumentEnumCodec` 外全部为 `RUNTIME`

## 职责

| 能力 | 对应注解 |
|---|---|
| 文档字段名 | `@DocumentName` |
| 类级/字段级命名策略 | `@DocumentNaming`（`DocumentNamingStrategy`） |
| 类级/字段级写出策略 | `@DocumentInclude`（`DocumentIncludePolicy`） |
| 忽略字段 | `@DocumentIgnore` |
| 字段级转换器（双向 / 单向） | `@DocumentConverter`、`@DocumentSerializer`、`@DocumentDeserializer` |
| 多态类型协议 | `@DocumentTypeInfo`、`@DocumentTypeName` |
| 反序列化实例化 | `@DocumentCreator`、`@DocumentBuilder`、`@DocumentBuild` |
| 日期 / 数字格式 | `@DocumentDateTimeFormat`、`@DocumentNumberFormat` |
| XML 根元素名 | `@DocumentRootName` |
| KSP 代码生成 | `@DocumentEnumCodec` |

## 依赖关系

```text
annotation（零依赖）
   ▲ api
   │
serialization ── api ── core ── api ── exception
   ▲
   │ implementation
serialization-kotlin
```

- `serialization` 通过 `api(project(":annotation"))` 暴露注解，使用方无需单独声明；
- `annotation` 中的 `DocumentBaseConverter` / `DocumentBaseSerializer` /
  `DocumentBaseDeserializer` 只是**标记接口**，真正的方法接口在 `serialization` 并继承
  它们（避免注解模块反向依赖 `serialization`）。

## 注解总表

| 注解 | 目标（`@Target`） | 保留 | 属性 | 默认值 |
|---|---|---|---|---|
| `@DocumentName` | FIELD、PROPERTY、FUNCTION、VALUE_PARAMETER | RUNTIME | `value: String` | 无 |
| `@DocumentNaming` | CLASS、FIELD、PROPERTY_GETTER、PROPERTY_SETTER、FUNCTION、VALUE_PARAMETER | RUNTIME | `value: DocumentNamingStrategy` | 无 |
| `@DocumentInclude` | CLASS、FIELD、PROPERTY_GETTER、PROPERTY_SETTER、FUNCTION、VALUE_PARAMETER | RUNTIME | `value: DocumentIncludePolicy` | 无 |
| `@DocumentIgnore` | FIELD、PROPERTY、FUNCTION | RUNTIME | 无 | — |
| `@DocumentConverter` | FIELD、PROPERTY_GETTER、PROPERTY_SETTER、FUNCTION | RUNTIME | `value: KClass<out DocumentBaseConverter<*>>` | 无 |
| `@DocumentSerializer` | FIELD、PROPERTY_GETTER、PROPERTY_SETTER、FUNCTION | RUNTIME | `value: KClass<out DocumentBaseSerializer<*>>` | 无 |
| `@DocumentDeserializer` | FIELD、PROPERTY_GETTER、PROPERTY_SETTER、FUNCTION | RUNTIME | `value: KClass<out DocumentBaseDeserializer<*>>` | 无 |
| `@DocumentTypeInfo` | CLASS | RUNTIME | `property: String` | `"type"` |
| `@DocumentTypeName` | CLASS | RUNTIME | `value: String` | 无 |
| `@DocumentCreator` | CONSTRUCTOR | RUNTIME | 无 | — |
| `@DocumentBuilder` | FUNCTION | RUNTIME | 无 | — |
| `@DocumentBuild` | FUNCTION | RUNTIME | 无 | — |
| `@DocumentDateTimeFormat` | FIELD、PROPERTY、PROPERTY_GETTER、FUNCTION、VALUE_PARAMETER | RUNTIME | `value: String`、`timezone: String` | `timezone = ""` |
| `@DocumentNumberFormat` | FIELD、PROPERTY、PROPERTY_GETTER、FUNCTION、VALUE_PARAMETER | RUNTIME | `value: String`、`locale: String` | `locale = ""` |
| `@DocumentRootName` | CLASS | RUNTIME | `value: String` | 无 |
| `@DocumentEnumCodec` | CLASS | **SOURCE** | `converterPackage`、`converterName`、`moduleName` | 全为 `""` |

## @DocumentName

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentName(val value: String)
```

指定字段 / 属性 / 构造参数在文档中的名称：

- 字段或 getter 上：决定**写出**字段名；`@DocumentCreator` 参数上：决定**读取**字段名；
- Kotlin 主构造参数上读写两侧都生效；显式注解优先于 `PropertyNamingStrategy`。

```kotlin
class Profile {
    @field:DocumentName("display_name")
    var name: String = ""

    @get:DocumentName("created_at")
    val createdAt: String = ""
}
```

## @DocumentNaming

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(CLASS, FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentNaming(val value: DocumentNamingStrategy)

public enum class DocumentNamingStrategy {
    AsIsStrategy, SnakeCaseStrategy, KebabCaseStrategy,
    UpperCamelStrategy, LowerCamelStrategy,
}
```

类级/字段级命名策略覆盖，优先级：

```text
@DocumentName > 字段级 @DocumentNaming > 类级 @DocumentNaming
  > 全局 DocumentMapperConfig.namingStrategy
```

- 类级策略作用于该类型所有未显式命名的属性/构造参数/record 组件；
- 字段级覆盖类级；`@DocumentName` 始终最高；
- `@DocumentCreator` 构造参数未标注 `@DocumentName` 时先看参数上的
  `@DocumentNaming`，再看类级策略；
- Builder 模式：目标类上的类级注解通过 Builder 的封闭类继承。

```kotlin
@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)
class User {
    var userName: String = ""

    @field:DocumentNaming(DocumentNamingStrategy.KebabCaseStrategy)
    var displayName: String = ""
}
```

## @DocumentInclude

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(CLASS, FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentInclude(val value: DocumentIncludePolicy)

public enum class DocumentIncludePolicy { ALWAYS, NON_NULL, NON_EMPTY, USE_DEFAULTS }
```

类级/字段级写出策略覆盖，只影响序列化：

| 策略 | 行为 |
|---|---|
| `ALWAYS` | 总是写出（包括 `null`） |
| `NON_NULL` | `null` 不写出 |
| `NON_EMPTY` | `null`、空字符串、空集合、空 `Map`、空数组不写出 |
| `USE_DEFAULTS` | 回退到下一优先级，最终使用全局 `writeNulls` |

优先级：字段级 > 类级 > 全局 `DocumentMapperConfig.writeNulls`；嵌套对象与集合元素
按各自类型/字段的策略处理；`toNode` 与流式写出路径一致。

```kotlin
@DocumentInclude(DocumentIncludePolicy.NON_EMPTY)
class Order {
    var id: String = ""

    @field:DocumentInclude(DocumentIncludePolicy.ALWAYS)
    var remark: String? = null
}
```

## @DocumentIgnore

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, FUNCTION)
public annotation class DocumentIgnore
```

标记字段 / 属性在序列化时忽略：

- 序列化不写出；反序列化不读入，且不会被当作未知字段报错；
- Java record 组件、`DocumentRuntimeCodecFactory`、`DocumentAccessorCodegen` 路径同样生效。

```kotlin
class User {
    @field:DocumentIgnore
    var internalId: Long = 0
}
```

## @DocumentConverter

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION)
public annotation class DocumentConverter(
    val value: KClass<out DocumentBaseConverter<*>>,
)
```

字段级**双向**转换器，同时作用于序列化与反序列化。优先级：

```text
@DocumentSerializer / @DocumentDeserializer（单向，最高）
  > @DocumentConverter（字段级双向）
  > Builder 注册的全局转换器
  > 内置 Bean / 标量映射
```

- 转换器实现类需提供无参构造器或声明为 Kotlin `object`；
- 实例在 Mapper 内缓存，**必须保持无状态 / 线程安全**；
- 缺少的方向会回退到全局转换器或内置映射；
- 只实现单向能力的类不能用于本注解（见 `MIGRATION.md`）。

```kotlin
class MoneyConverter : DocumentBaseConverter<Money> {
    override fun getType() = DocumentTypeReference.of(Money::class.java)
    override fun serialize(value: Money, context: SerializationContext): Node = StringNode.of(value.toString())
    override fun deserialize(node: Node, context: DeserializationContext): Money = Money.parse(node.asString())
}

class Order {
    @field:DocumentConverter(MoneyConverter::class)
    var total: Money = Money.ZERO
}
```

## @DocumentSerializer 与 @DocumentDeserializer

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION)
public annotation class DocumentSerializer(val value: KClass<out DocumentBaseSerializer<*>>)

@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION)
public annotation class DocumentDeserializer(val value: KClass<out DocumentBaseDeserializer<*>>)
```

单向转换器，优先级高于 `@DocumentConverter` 与全局转换器，适合方向不对称的场景：

```kotlin
class PasswordSerializer : DocumentBaseSerializer<String> {
    override fun getType() = DocumentTypeReference.of(String::class.java)
    override fun serialize(value: String, context: SerializationContext): Node = StringNode.of("***")
}

class Account {
    @field:DocumentSerializer(PasswordSerializer::class)
    var password: String = ""
}
```

## @DocumentTypeInfo 与 @DocumentTypeName

多态协议由**显式注册**驱动，注解只提供类型属性名/类型名：

```kotlin
@DocumentTypeInfo(property = "kind")
interface Animal

@DocumentTypeName("cat")
class Cat : Animal
```

- `@DocumentTypeInfo` 标注多态基类 / 接口，`property` 默认 `"type"`；
- `@DocumentTypeName` 标注具体子类，`value` 不能为空；
- 字段声明类型是基类时，序列化会额外写出类型属性；
- 反序列化按类型属性查找**已注册**的子类；未注册的子类不会被反射发现；
- 注册时提供的类型名作为别名保留；显式注解名优先用于写出；
- 类型属性名可被 `DocumentMapperConfigBuilder.typeProperty(...)` 覆盖，优先级高于注解；
- 缺失/未知类型名、类型属性不是字符串都会抛 `DocumentSerializationException`。

注册示例（详见 [serialization](./serialization.md#多态)）：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .subtype(Animal::class.java, "cat", Cat::class.java)
        .build(),
)
mapper.format(Cat()) // {"type":"cat"}
```

## @DocumentCreator

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
public annotation class DocumentCreator
```

标记反序列化使用的构造器，用于没有无参构造器的不可变 Bean：

- 构造参数名取参数上的 `@DocumentName`；没有注解时使用编译保留的参数名
  （Java 需 `-parameters`，Kotlin 需 `-java-parameters`）；
- 参数类型参与递归反序列化，支持泛型、嵌套 Bean、集合、Map、可空与基本类型；
- 一个类型最多一个；存在时优先于无参构造 + setter 与 Builder 模式；缺字段按
  `MissingFieldPolicy` 处理。

```java
class User {
    @DocumentCreator
    User(@DocumentName("name") String name, @DocumentName("age") int age) { ... }
}
```

## @DocumentBuilder 与 @DocumentBuild

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class DocumentBuilder

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class DocumentBuild
```

Builder 模式实例化，适用于只有私有构造器、字段全 `final` 的不可变类：

1. 调用 `@DocumentBuilder` 标注的无参静态工厂得到 Builder；
2. 按普通 Bean 规则（无参构造 + setter）用文档内容填充 Builder；
3. 调用 `@DocumentBuild` 标注的无参实例方法得到目标对象；未标注时回退无参 `build()`。

启用条件：没有 `@DocumentCreator`、没有无参构造器、目标类存在 `@DocumentBuilder`
静态工厂，且 Builder 存在 `@DocumentBuild` 或无参 `build()`。两个注解都最多一个。

```java
class User {
    @DocumentBuilder
    static UserBuilder builder() { return new UserBuilder(); }
}

class UserBuilder {
    @DocumentBuild
    User build() { return new User(name); }
}
```

## @DocumentDateTimeFormat

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, PROPERTY_GETTER, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentDateTimeFormat(
    val value: String,          // 例如 "yyyy-MM-dd HH:mm:ss"
    val timezone: String = "",  // 例如 "Asia/Shanghai"；空表示系统默认时区
)
```

- 支持 `java.util.Date`、`Calendar` 以及 `java.time` 的 `LocalDate`、
  `LocalDateTime`、`LocalTime`、`OffsetDateTime`、`ZonedDateTime`、`Instant`；
- 写出时格式化为**字符串**节点，读取时按目标字段类型解析；解析失败抛
  `DocumentSerializationException`（message 含字段名与 pattern）；
- `timezone` 为空时使用系统默认时区。

```kotlin
class Order {
    @field:DocumentDateTimeFormat("yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    var createdAt: LocalDateTime? = null
}
```

## @DocumentNumberFormat

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, PROPERTY_GETTER, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentNumberFormat(
    val value: String,       // DecimalFormat pattern，例如 "#,##0.00"
    val locale: String = "", // 例如 "zh-CN"；空表示系统默认 Locale
)
```

- 使用 `java.text.DecimalFormat` 的 pattern 语法（`#,##0.00`、`0.00%` 等）；
- 字段以**字符串**形式写入文档，读取时按目标数字类型还原；支持 `BigDecimal`、
  `BigInteger`、`Byte`、`Short`、`Int`、`Long`、`Float`、`Double`、`Number`；
- `isParseBigDecimal = true` 避免二进制浮点误差；`locale` 使用 `Locale.forLanguageTag`。

```kotlin
class Invoice {
    @field:DocumentNumberFormat("#,##0.00", locale = "zh-CN")
    var amount: BigDecimal = BigDecimal.ZERO
}
```

## @DocumentRootName

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentRootName(val value: String)
```

仅 XML Mapper 使用：写出时作为根元素名。解析优先级：

```text
@DocumentRootName(类) > XmlConfig.rootName > "root"
```

```kotlin
@DocumentRootName("user")
data class User(val name: String)
```

## @DocumentEnumCodec

```kotlin
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentEnumCodec(
    val converterPackage: String = "",
    val converterName: String = "",
    val moduleName: String = "",
)
```

标记枚举，由 `codegen` 模块的 KSP 处理器在编译期生成：

- `<枚举名>DocumentConverter : DocumentBaseConverter<枚举>`；
- 一个注册全部转换器的 `DocumentModule`。

参数语义：

| 参数 | 空字符串含义 | 非空含义 |
|---|---|---|
| `converterPackage` | 默认包 `com.mingliqiye.document.generated` | 生成的包名 |
| `converterName` | `<枚举名>DocumentConverter` | 生成类名 |
| `moduleName` | 默认 `GeneratedDocumentModule` | 模块类名；多个枚举同时指定时必须包名+类名完全一致 |

- 保留策略 `SOURCE`，处理器读完即丢弃，不进入运行时字节码；
- 只支持 public 顶层或 public 嵌套枚举；
- 生成的转换器类名在同一编译单元内必须唯一；
- 更完整的配置与限制见 [codegen 模块](./codegen.md)。

## 注解读取 API

字段级元数据通过 `serialization` 的 `DocumentProperty` 暴露给转换器，主要成员：
`declaringClass`、`name`、`type`、`documentName`、`annotations`、`getAnnotation`、
`hasAnnotation`、`isIgnored()`、`getRawType()`。

`getAnnotation` 支持 `@AliasFor` 别名解析（`alias-for` 依赖）；转换器通过
`SerializationContext.property` / `DeserializationContext.property` 获取当前字段上下文。

## Kotlin 使用位置

Kotlin 属性上的注解默认落在**字段**上；需要注解 getter / setter 时使用 use-site target：

```kotlin
class Bean {
    @field:DocumentName("n")     // 字段
    @get:DocumentIgnore          // getter
    var value: String = ""

    @set:DocumentIgnore          // setter（需要 var）
    var secret: String = ""
}
```

注意：`@DocumentConverter` / `@DocumentSerializer` / `@DocumentDeserializer` 的目标包含
getter/setter，**不包含** `VALUE_PARAMETER`；`@DocumentCreator` 的参数名需要
`@DocumentName` 写在构造参数上；value class 的注解规则见
[serialization-kotlin](./serialization-kotlin.md)。

## 扩展点

1. **自定义转换器**：实现 `DocumentBaseConverter` / `DocumentBaseSerializer` /
   `DocumentBaseDeserializer`，再用字段注解、`DocumentMapperBuilder.addConverter/...`
   或 `DocumentMapperConfigBuilder.field(...)` 注册。
2. **自定义注解**：注解模块不提供 SPI；自定义注解需要在自定义转换器或 Mapper 中读取，
   内置映射不会识别未知注解。

## 限制

- 注解不提供「必填」语义（`MissingFieldPolicy` 在 Mapper 配置层）；
- `@DocumentIgnore` 不作用于 Map 的 key（Map 内容按值映射）；
- `@DocumentTypeInfo` / `@DocumentTypeName` 必须配合显式子类型注册才生效，不会自动扫描；
- `@DocumentRootName` 只被 XML Mapper 使用，其他格式忽略；
- `@DocumentEnumCodec` 是源码级注解，运行时反射读不到；
- 注解本身不校验取值（如空字符串类型名、非法 pattern），非法值在 Mapper 使用时报
  `IllegalArgumentException` 或 `DocumentSerializationException`；
- 转换器实例被缓存，任何可变状态都会造成并发问题。
