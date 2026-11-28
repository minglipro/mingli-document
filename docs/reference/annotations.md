# 注解参考

所有注解位于 `com.mingliqiye.document.annotation`（`annotation` 模块），与格式无关，由
`serialization` 与各 `format-*` 模块在运行时读取。通常由依赖传递引入，无需单独声明：

```kotlin
implementation("com.mingliqiye.document:annotation:2.3.0")
```

## 总表

| 注解 | 保留 | 目标（`@Target`） | 属性 | 作用 |
|---|---|---|---|---|
| `@DocumentName` | RUNTIME | FIELD、PROPERTY、FUNCTION、VALUE_PARAMETER | `value: String` | 文档字段名 |
| `@DocumentIgnore` | RUNTIME | FIELD、PROPERTY、FUNCTION | — | 忽略字段 |
| `@DocumentConverter` | RUNTIME | FIELD、PROPERTY_GETTER、PROPERTY_SETTER、FUNCTION | `KClass<out DocumentBaseConverter<*>>` | 字段双向转换 |
| `@DocumentSerializer` | RUNTIME | 同上 | `KClass<out DocumentBaseSerializer<*>>` | 字段单向序列化 |
| `@DocumentDeserializer` | RUNTIME | 同上 | `KClass<out DocumentBaseDeserializer<*>>` | 字段单向反序列化 |
| `@DocumentTypeInfo` | RUNTIME | CLASS | `property: String = "type"` | 多态基类类型属性名 |
| `@DocumentTypeName` | RUNTIME | CLASS | `value: String` | 多态子类类型名 |
| `@DocumentCreator` | RUNTIME | CONSTRUCTOR | — | 反序列化构造器 |
| `@DocumentBuilder` | RUNTIME | FUNCTION | — | Builder 静态工厂 |
| `@DocumentBuild` | RUNTIME | FUNCTION | — | Builder 构建方法 |
| `@DocumentDateTimeFormat` | RUNTIME | FIELD、PROPERTY、PROPERTY_GETTER、FUNCTION、VALUE_PARAMETER | `value`、`timezone = ""` | 日期时间格式 |
| `@DocumentNumberFormat` | RUNTIME | 同上 | `value`、`locale = ""` | 数字格式 |
| `@DocumentRootName` | RUNTIME | CLASS | `value: String` | XML 根元素名 |
| `@DocumentEnumCodec` | **SOURCE** | CLASS | `converterPackage`、`converterName`、`moduleName`（默认 `""`） | KSP 枚举代码生成 |

## 标记接口

`annotation` 模块提供三个无方法标记接口，作为注解参数的公共父类型：

```kotlin
public interface DocumentBaseConverter<T>
public interface DocumentBaseSerializer<T>
public interface DocumentBaseDeserializer<T>
```

真正的方法接口在 `serialization` 并继承它们（`DocumentBaseConverter` 同时继承序列化/
反序列化接口）：

```kotlin
public interface DocumentBaseSerializer<T> : annotation.DocumentBaseSerializer<T> {
    public fun getType(): DocumentTypeReference<T>
    public fun serialize(value: T, context: SerializationContext): Node
}
public interface DocumentBaseDeserializer<T> : annotation.DocumentBaseDeserializer<T> {
    public fun getType(): DocumentTypeReference<T>
    public fun deserialize(node: Node, context: DeserializationContext): T?
}
```

## 优先级

```text
@DocumentSerializer / @DocumentDeserializer（单向，最高）
  > @DocumentConverter（字段级双向）
  > 全局注册（DocumentMapperBuilder.addConverter/...）
  > DocumentMapperConfigBuilder.field(...)
  > 内置 Bean / 标量映射
```

显式 `@DocumentName` 优先于命名策略；`typeProperty(...)` 优先于
`@DocumentTypeInfo.property`；Kotlin 属性注解默认落字段，需 getter/setter 时用
`@get:` / `@set:`，构造参数用 `@param:`。
---

## @DocumentName

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentName(val value: String)
```

- 字段/getter 上决定写出字段名；`@DocumentCreator` 参数上决定读取字段名；
- Kotlin 主构造参数上读写两侧同时生效；Java record 组件上同时作用于读写；
- `value` 空字符串会导致空字段名，库不做校验；同名映射不做冲突检测。

```kotlin
class User {
    @field:DocumentName("user_name")
    var name: String = ""
}

data class UserDto(@param:DocumentName("user_name") val name: String, val age: Int)
```

## @DocumentIgnore

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, FUNCTION)
public annotation class DocumentIgnore
```

- 序列化不写出；反序列化不读入，且不会被 `failOnUnknownFields` 当作未知字段；
- 对 record 组件、`DocumentRuntimeCodecFactory`、`DocumentAccessorCodegen` 同样生效，
  只影响 Bean 映射，不影响手工构造的 `Node`/Map。

## @DocumentConverter

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION)
public annotation class DocumentConverter(val value: KClass<out DocumentBaseConverter<*>>)
```

- 字段级双向转换器；实现类需无参构造器或 Kotlin `object`；
- 实例在 Mapper 内缓存，**必须无状态/线程安全**；
- 通过 `SerializationContext.property` / `DeserializationContext.property` 获取字段元数据；
- 缺少的方向回退到全局转换器或内置映射；
- 泛型已收紧：只接受双向 `DocumentBaseConverter`，单向实现改用另外两个注解。

```kotlin
class MoneyConverter : DocumentBaseConverter<Money> {
    override fun getType() = DocumentTypeReference.of(Money::class.java)
    override fun serialize(value: Money, context: SerializationContext): Node = StringNode.of(value.toString())
    override fun deserialize(node: Node, context: DeserializationContext): Money = Money.parse(node.asString())
}

class Order {
    @field:DocumentConverter(MoneyConverter::class)
    var amount: Money = Money.ZERO
}
```

## @DocumentSerializer

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION)
public annotation class DocumentSerializer(val value: KClass<out DocumentBaseSerializer<*>>)
```

- 只接管序列化，优先级最高；反序列化走 `@DocumentConverter`/全局/内置；
- 必须实现 `getType()`；可用作只写字段（如脱敏输出）。

```kotlin
class MaskedSerializer : DocumentBaseSerializer<String> {
    override fun getType() = DocumentTypeReference.of(String::class.java)
    override fun serialize(value: String, context: SerializationContext): Node = StringNode.of("***")
}

class Account {
    @field:DocumentSerializer(MaskedSerializer::class)
    var password: String = ""
}
```

## @DocumentDeserializer

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION)
public annotation class DocumentDeserializer(val value: KClass<out DocumentBaseDeserializer<*>>)
```

- 只接管反序列化；`deserialize` 返回 `null` 表示无法还原；
- 抛出 `DocumentSerializationException` 会中断整个反序列化，建议携带 `context.path`；
- 可与 `@DocumentSerializer` 同字段组合，形成自定义读写。

```kotlin
class MoneyDeserializer : DocumentBaseDeserializer<Money> {
    override fun getType() = DocumentTypeReference.of(Money::class.java)
    override fun deserialize(node: Node, context: DeserializationContext): Money? =
        node.asStringOrNull()?.let { Money.parse(it) }
}
```

## @DocumentTypeInfo

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentTypeInfo(val property: String = "type")
```

- 标在多态基类/接口，指定文档中记录子类型名的属性名；
- 字段声明为该类型且实际值已注册时，序列化额外写出类型属性；
- 必须配合显式子类型注册（`subtype(...)` / `kotlinSealedSubtypes(...)`），不扫描子类；
- `property` 空白在构建配置时抛 `IllegalArgumentException`。

```kotlin
@DocumentTypeInfo(property = "kind")
interface Animal

@DocumentTypeName("cat")
class Cat : Animal

val config = DocumentMapperConfig.builder()
    .subtype(Animal::class.java, "cat", Cat::class.java)
    .build()
val mapper = JsonMapper(config)
mapper.format(Cat()) // {"kind":"cat"}
```

## @DocumentTypeName

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentTypeName(val value: String)
```

- 标在具体子类，值不能为空；同基类下不能重复；
- 显式注解优先于注册名，注册名保留为反序列化别名；
- 没有注解时使用注册时提供的 `typeName`（Kotlin sealed 自动注册时回退简单类名）。

## @DocumentCreator

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
public annotation class DocumentCreator
```

- 标记反序列化构造器；一个类型最多一个，优先于无参构造和 Builder；
- 参数名取 `@DocumentName`，否则用编译保留参数名（Java `-parameters`、Kotlin `-java-parameters`）；
- 参数类型递归反序列化，支持泛型、嵌套 Bean、集合、Map、可空与基本类型；
- 缺字段按 `MissingFieldPolicy` 处理；构造器异常包装为 `DocumentSerializationException`。

```java
public final class User {
    private final String name;
    private final int age;

    @DocumentCreator
    public User(@DocumentName("name") String name, @DocumentName("age") int age) {
        this.name = name;
        this.age = age;
    }
}
```

## @DocumentBuilder

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class DocumentBuilder
```

- 标记目标类的**无参静态工厂**，反序列化时先取 Builder，再用文档填充，最后构建；
- 启用条件：没有 `@DocumentCreator`、没有无参构造器、存在该静态工厂；
- 一个类型最多一个；工厂必须返回 Builder 类型（完整示例见
  [annotation 模块](../modules/annotation.md)）。

## @DocumentBuild

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class DocumentBuild
```

- 标在 Builder 上，把 Builder 转换为目标对象；必须无参实例方法；
- 未标注时回退无参 `build()`；一个 Builder 类型最多一个；
- 与 `@DocumentBuilder` 成对使用，Builder 本身仍按普通 Bean 规则填充。

```java
class UserBuilder {
    private String name;

    UserBuilder setName(String name) { this.name = name; return this; }

    @DocumentBuild
    User build() { return new User(name); }
}
```

## @DocumentDateTimeFormat

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, PROPERTY_GETTER, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentDateTimeFormat(
    val value: String,          // 例 "yyyy-MM-dd HH:mm:ss"
    val timezone: String = "",  // 例 "Asia/Shanghai"；空表示系统默认
)
```

- 支持 `java.util.Date`、`Calendar`，以及 `LocalDate`、`LocalDateTime`、`LocalTime`、
  `OffsetDateTime`、`ZonedDateTime`、`Instant`（并兼容 `java.sql.Date`/`Timestamp` 读取）；
- 写出为字符串节点，读取按目标字段类型解析；非法 pattern 在构造转换器时抛异常；
- 解析失败包装为 `DocumentSerializationException`，消息含字段名与 pattern；支持
  `VALUE_PARAMETER`，可用于构造器注入参数。

```kotlin
class Event {
    @field:DocumentDateTimeFormat("yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    var at: LocalDateTime? = null
}
```

## @DocumentNumberFormat

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(FIELD, PROPERTY, PROPERTY_GETTER, FUNCTION, VALUE_PARAMETER)
public annotation class DocumentNumberFormat(
    val value: String,       // DecimalFormat pattern，例 "#,##0.00" / "0.00%"
    val locale: String = "", // 例 "zh-CN"；空表示系统默认
)
```

- 数字以**字符串**写出，读取时按目标类型还原；支持 `BigDecimal`、`BigInteger`、`Byte`、
  `Short`、`Int`、`Long`、`Float`、`Double`、`Number`；
- `isParseBigDecimal = true`，避免二进制浮点误差；`locale` 用 `Locale.forLanguageTag`，
  `_` 替换为 `-`；非 `Number` 值或非数字节点抛 `DocumentSerializationException`；
- 百分比 pattern 遵循 `DecimalFormat` 语义（0.5 → `50.00%`）。

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

- 仅 XML Mapper 使用；写出时作为根元素名；
- 优先级：注解 > `XmlConfig.rootName` > `"root"`；
- 不参与反序列化（读取时不强制匹配根名）；其他格式忽略。

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

| 参数 | 空串语义 | 非空语义 |
|---|---|---|
| `converterPackage` | `com.mingliqiye.document.generated` | 生成转换器所在包 |
| `converterName` | `<枚举简单名>DocumentConverter` | 生成转换器类名 |
| `moduleName` | `GeneratedDocumentModule` | 生成模块类名；多枚举指定时必须包名+类名一致 |

- 编译期 KSP 生成 `DocumentBaseConverter` 与一个 `DocumentModule`；
- 序列化写枚举 `name`，反序列化 `valueOf`，非法名称返回 `null`；
- `SOURCE` 保留，不进入运行时字节码；只支持 public 顶层/嵌套枚举；
- 非 public、非法包名/类名、重复生成类名、模块名冲突都会使编译失败；
- 详见 [codegen 模块](../modules/codegen.md)。

```kotlin
@DocumentEnumCodec
public enum class Pet { CAT, DOG }

val mapper = JsonMapper.builder().addModule(GeneratedDocumentModule()).build()
```

## 注解读取 API

运行时字段元数据通过 `serialization` 的 `DocumentProperty` 暴露，主要成员：
`declaringClass`、`name`（Java 属性名）、`type`（泛型 `Type`）、`documentName`
（已应用 `@DocumentName` / 命名策略）、`annotations`、`getAnnotation(Class)`、
`hasAnnotation(Class)`、`isIgnored()`、`getRawType()`。

`getAnnotation` 支持 `@AliasFor` 别名解析；Kotlin 无 use-site target 的属性注解由
`PropertyMetadataResolver` 补全；转换器通过 `context.property` 读取字段名、注解与
忽略标记，`context.path` 提供错误路径。

## Kotlin / Java 放置规则

| 场景 | Kotlin | Java |
|---|---|---|
| 字段名 | `@field:DocumentName("x")`（或属性默认落点） | 字段或 getter |
| 忽略 | `@field:DocumentIgnore` / `@get:DocumentIgnore` | 字段或 getter |
| 构造参数 | `@param:DocumentName("x")` | 参数上 |
| 转换器 | 属性上（目标含 getter/setter） | 字段或 getter/setter |
| 日期/数字格式 | `@field:` / `@get:` / `@param:` | 字段/getter/参数 |
| 多态 | 类上直接标注 | 类上直接标注 |

## 常见问题

**Q：`@DocumentName` 与命名策略冲突？** 显式注解优先，命名策略只处理未注解字段。

**Q：多态子类没注册会怎样？** 反序列化遇到未知类型名抛 `DocumentSerializationException`；
请显式 `subtype(...)` 或 `kotlinSealedSubtypes(...)`。

**Q：转换器可以是 `object` 吗？** 可以，Mapper 先找 Kotlin object 的 `INSTANCE`，再尝试
无参构造器。注解不能继承，子类字段需重新标注。
