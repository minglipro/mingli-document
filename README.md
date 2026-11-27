# document

格式无关的文档树模型 + JSON/YAML/XML/Properties/TOML/CSV/Ion 编解码 + Java Bean 映射。

`com.mingliqiye.document` 用一棵与具体序列化格式解耦的文档树（`Node`）作为中间层：

- **格式无关**：`core` 只定义节点树与类型引用，不依赖任何具体格式；
- **JSON/YAML/XML/Properties/TOML/CSV/Ion Mapper**：`format-json`、`format-yaml`、`format-xml`、`format-properties`、`format-toml`、`format-csv`、`format-ion` 分别提供对应 Mapper，均继承通用 `DocumentMapper`；
- **Java Bean 映射**：`serialization` 负责 Java/Kotlin 实体与文档树之间的双向映射，支持字段注解与自定义转换器。

## 模块与依赖

| 模块 | 职责 | 依赖 |
|---|---|---|
| `annotation` | 格式无关注解：`@DocumentName`、`@DocumentIgnore`、`@DocumentConverter` | 无 |
| `exception` | 文档异常基类 `DocumentException` / `DocumentTypeException` | `com.mingliqiye.exception:mingli-exception:1.1.1-Final` |
| `core` | 文档树 `Node`、`DocumentTypeReference` | `exception` |
| `format-json` | JSON/JSONC Mapper `JsonMapper`、`JsonReader` | `serialization`、`core` |
| `format-yaml` | YAML Mapper `YamlMapper`、`YamlReader` | `serialization`、`core`、SnakeYAML |
| `format-xml` | XML Mapper `XmlMapper`、`XmlReader` | `serialization`、`core` |
| `format-properties` | Properties Mapper `PropertiesMapper`、`PropertiesReader` | `serialization`、`core` |
| `format-toml` | TOML Mapper `TomlMapper`、`TomlReader` | `serialization`、`core`、Tomlj |
| `format-csv` | CSV Mapper `CsvMapper`、`CsvReader` | `serialization`、`core`、Commons CSV |
| `format-ion` | Ion Mapper `IonMapper`、`IonReader`（文本 Ion） | `serialization`、`core`、Ion Java |
| `serialization` | `DocumentMapper` 基类、Java Bean ↔ `Node` 映射、转换器、字段注解与别名注解读取 | `core`、`annotation`、`com.mingliqiye.utils.aliasfor:alias-for:1.0.3` |
| `serialization-kotlin` | Kotlin data class 支持函数 `kotlinModule()` | `serialization`、`annotation`、`kotlin-reflect` |

依赖方向（`A ──► B` 表示 A 依赖 B）：

```text
format-json ──► serialization ──► core ──► exception ──► mingli-exception
format-yaml ──► serialization
format-xml  ──► serialization
format-properties ──► serialization
format-toml ──► serialization
format-csv  ──► serialization
format-ion  ──► serialization
serialization-kotlin ──► serialization
serialization ──► annotation（implementation）
              ──► alias-for 1.0.3（api）
annotation：项目内无依赖
```

- `benchmarks` 是开发工具模块，不对外发布；
- `core` 不依赖任何具体格式；`format-json` / `format-yaml` / `format-xml` / `format-properties` / `format-toml` / `format-csv` / `format-ion` 依赖 `serialization`，并继承其 `DocumentMapper` 基类。
- 外部使用方只需要引入对应格式模块（如 `format-json`）即可获得 `JsonMapper`；不需要接触内部 Codec 实现。

## 坐标

- group：`com.mingliqiye.document`
- 当前版本：`1.0-SNAPSHOT`（在根 `build.gradle.kts` 中统一定义）
- 构建目标：JDK 8

各模块的依赖坐标形如 `com.mingliqiye.document:<模块名>:1.0-SNAPSHOT`，例如（仓库配置以根 `build.gradle.kts` 为准：`mavenLocal`、`mingliqiye-public`、Maven Central）：

```kotlin
dependencies {
    implementation("com.mingliqiye.document:serialization:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:format-json:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:format-yaml:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:format-xml:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:format-properties:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:format-toml:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:format-csv:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:format-ion:1.0-SNAPSHOT")
    implementation("com.mingliqiye.document:serialization-kotlin:1.0-SNAPSHOT")
    // 注解模块在 serialization 中是 implementation 依赖，不会传递到消费方编译类路径
    implementation("com.mingliqiye.document:annotation:1.0-SNAPSHOT")
}
```

## 环境要求

- JDK 8：所有模块均配置 `jvmToolchain(8)`，编译与测试目标为 Java 8（见「构建与测试」）。
- Kotlin 2.0.0（根构建脚本中的 Kotlin JVM 插件版本）。
- 构建工具：仓库自带 Gradle Wrapper 8.14.7。

## 快速开始

以下是一个完整 Kotlin 文件（`Main.kt`），引入依赖后可直接运行：

```kotlin
// Main.kt
import com.mingliqiye.document.format.json.JsonMapper

class User {
    var name: String = ""
    var age: Int = 0
}

fun main() {
    // JSON 格式使用 JsonMapper；YAML 使用 YamlMapper
    val mapper = JsonMapper()

    val user = User().apply {
        name = "小于"
        age = 18
    }

    // 实体 -> 文档树
    val node = mapper.toNode(user)
    println(node.asObject()["name"]?.asString()) // 小于

    // 文档树 -> 实体
    val restored = mapper.fromNode(node, User::class.java)
    println("${restored.name}:${restored.age}") // 小于:18

    // 文本 -> 实体
    val parsed = mapper.parse("""{"name":"小于","age":18}""", User::class.java)
    println(parsed.age) // 18

    // 实体 -> 文本
    println(mapper.format(parsed))       // 紧凑 JSON
    println(mapper.formatPretty(parsed)) // 两空格缩进 JSON
}
```

`DocumentMapper` 的主要方法：

| 方法 | 作用 |
|---|---|
| `toNode(value)` | 任意实体、集合、`Map`、数组或 `Node` → 文档树 |
| `fromNode(node, Class)` / `fromNode(node, DocumentTypeReference)` | 文档树 → Java 实体（支持泛型类型） |
| `parse(source, Class)` / `parse(source, DocumentTypeReference)` | 文本 → Java 实体 |
| `parse(byteArray, Class)` / `parse(byteArray, DocumentTypeReference)` | 二进制 → Java 实体（二进制 Mapper） |
| `format(value)` / `formatPretty(value)` | Java 实体 → 紧凑 / 格式化文本 |
| `formatBytes(value)` | Java 实体 → 二进制（二进制 Mapper） |
| `convert(source, Class)` / `convert(source, DocumentTypeReference)` | 实体↔实体、`Node`↔实体转换 |

> `JsonMapper` / `YamlMapper` / `XmlMapper` / `PropertiesMapper` / `TomlMapper` / `CsvMapper` / `IonMapper` 继承 `DocumentMapper`；格式模块内部 Codec 不对外暴露。

## Kotlin data class

Java Bean 需要无参构造器；Kotlin data class 可以通过 `serialization-kotlin` 使用主构造器反序列化。

```kotlin
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class User(
    @DocumentName("display_name") val name: String,
    val age: Int,
)

fun main() {
    val mapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()

    val user = mapper.parse("""{"display_name":"小于","age":18}""", User::class.java)
    println(mapper.formatPretty(user))
}
```

YAML 同样注册模块：

```kotlin
val mapper = YamlMapper.builder()
    .addModule(kotlinModule())
    .build()
```

支持：

- 主构造器参数反序列化
- 默认参数（缺失时使用默认值）
- 嵌套 data class
- `List` / `Map` 中的 data class
- `@DocumentName`、`@DocumentIgnore`
- 非 data class 自动回退到默认 Java Bean 逻辑

> `serialization-kotlin` 依赖 `kotlin-reflect`；不需要 Kotlin 反射时可以不引入该模块。
>
> 低层扩展点 `ObjectInstantiator` 仍可通过 `DocumentModule` 的 `ModuleSetupContext` 注入。

## Mapper 模块系统

格式 Mapper 支持类似 Jackson `Module` 的模块机制：

```kotlin
public interface DocumentModule {
    public val moduleName: String
    public fun setupModule(context: ModuleSetupContext)
}
```

自定义模块：

```kotlin
import com.mingliqiye.document.serialization.DocumentModule
import com.mingliqiye.document.serialization.ModuleSetupContext

object MoneyModule : DocumentModule {
    override val moduleName: String = "money"

    override fun setupModule(context: ModuleSetupContext) {
        context.addConverter(MoneyConverter())
    }
}

fun main() {
    val mapper = JsonMapper.builder()
        .addModule(MoneyModule)
        .build()
}
```

模块可以：

- `addSerializer(...)`
- `addDeserializer(...)`
- `addConverter(...)`
- `setObjectInstantiator(...)`

`addModule(...)` 按 `moduleName` 去重，模块配置在 `build()` 时应用；Builder 自身注册的配置与模块配置会合并。

## 自定义转换器

转换器接口位于 `com.mingliqiye.document.serialization`：

```kotlin
interface JsonSerializer<T> {
    fun getType(): DocumentTypeReference<T>
    fun serialize(value: T, context: SerializationContext): Node
}

interface JsonDeserializer<T> {
    fun getType(): DocumentTypeReference<T>
    fun deserialize(node: Node, context: DeserializationContext): T?
}

// 双向转换器 = 序列化 + 反序列化
interface JsonConverter<T> : JsonSerializer<T>, JsonDeserializer<T>
```

实现一个双向转换器：

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DeserializationContext
import com.mingliqiye.document.serialization.JsonConverter
import com.mingliqiye.document.serialization.SerializationContext

class Money(val amount: Int)

class MoneyConverter : JsonConverter<Money> {
    override fun getType(): DocumentTypeReference<Money> = DocumentTypeReference.of()

    override fun serialize(value: Money, context: SerializationContext): Node =
        StringNode.of("${value.amount} CNY")

    override fun deserialize(node: Node, context: DeserializationContext): Money =
        Money(node.asString().removeSuffix(" CNY").toInt())
}
```

在 Builder 中注册：

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

fun main() {
    val mapper = JsonMapper.builder()
        .addConverter(MoneyConverter()) // 同时注册序列化和反序列化方向
        .build()

    val node = mapper.toNode(Money(12))
    println(node.asString()) // 12 CNY

    val money = mapper.fromNode(node, Money::class.java)
    println(money.amount) // 12
}
```

只注册单向能力时使用 `addSerializer(...)` / `addDeserializer(...)`，双向用 `addConverter(...)`。注册信息以 `getType().getRawType()` 为键；匹配时先精确匹配，再尝试 `isAssignableFrom`，同一类型后注册的会覆盖先注册的。

### 通过 context.property 读取字段信息

转换器执行时，`SerializationContext.property` / `DeserializationContext.property` 携带当前字段元数据（根对象或集合元素处为 `null`）；`context.path` 可用于错误定位。

`DocumentProperty` 提供：

| 属性/方法 | 说明 |
|---|---|
| `declaringClass` | 声明该属性的 Java 类 |
| `name` | Java 属性名（不受注解影响） |
| `type` / `getRawType()` | 泛型类型 / 原始类型 |
| `documentName` | 应用 `@DocumentName` 后的文档字段名 |
| `annotations` | 字段及读写方法上合并后的注解列表 |
| `getAnnotation(Class)` / `hasAnnotation(Class)` | 读取注解，支持 `@AliasFor` 别名解析 |
| `isIgnored()` | 是否被 `@DocumentIgnore` 标记 |

```kotlin
import com.mingliqiye.document.annotation.DocumentConverter
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DeserializationContext
import com.mingliqiye.document.serialization.JsonConverter
import com.mingliqiye.document.serialization.SerializationContext

// 复用一个自定义字段注解
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class MoneyLabel(val value: String)

// 复用上一节的 Money 类
class LabeledMoneyConverter : JsonConverter<Money> {
    override fun getType(): DocumentTypeReference<Money> = DocumentTypeReference.of()

    override fun serialize(value: Money, context: SerializationContext): Node {
        val label = context.property?.getAnnotation(MoneyLabel::class.java)?.value ?: "?"
        return StringNode.of("${value.amount} $label")
    }

    override fun deserialize(node: Node, context: DeserializationContext): Money {
        val label = context.property?.getAnnotation(MoneyLabel::class.java)?.value ?: "?"
        return Money(node.asString().removeSuffix(" $label").toInt())
    }
}

class LabeledWallet {
    @field:MoneyLabel("USD")
    @field:DocumentConverter(LabeledMoneyConverter::class)
    var money: Money = Money(0)
}

fun main() {
    val mapper = JsonMapper()
    val node = mapper.toNode(LabeledWallet().apply { money = Money(12) })
    println(node.asObject()["money"]?.asString()) // 12 USD
}
```

在转换器中读取字段名与注解：

```kotlin
val property = context.property                        // 根对象/集合元素处为 null
val javaName = property?.name                           // Java 属性名
val documentName = property?.documentName               // 应用 @DocumentName 后的名字
val label = property?.getAnnotation(MoneyLabel::class.java)?.value
```

## 字段级配置

格式 Mapper 可以传入 `DocumentMapperConfig`，按 `声明类 + 属性名` 定义字段的解析与格式化方式：

```kotlin
val config = DocumentMapperConfig.builder()
    .field(Order::class.java, "createdAt", DateFieldConverter())
    .build()

val jsonMapper = JsonMapper(config)
val yamlMapper = YamlMapper(config)
```

也可以从 Builder 传入：

```kotlin
val mapper = JsonMapper.builder()
    .config(config)
    .build()
```

格式子模块还可以使用带格式选项的 Config：

```kotlin
val jsonConfig = JsonConfig.builder()
    .field(Order::class.java, "createdAt", DateFieldConverter())
    .allowComments(false)
    .pretty(true)
    .build()

val jsonMapper = JsonMapper(jsonConfig)

val yamlConfig = YamlConfig.builder()
    .field(Order::class.java, "createdAt", DateFieldConverter())
    .pretty(true)
    .build()

val yamlMapper = YamlMapper(yamlConfig)
```

配置中的转换器与普通 `JsonConverter` 完全一致，仍然可以通过 `context.property` 获取：

```kotlin
context.property?.declaringClass
context.property?.name
context.property?.documentName
```

优先级：

```text
DocumentMapperConfig 字段配置
  ↓
@DocumentConverter 字段注解
  ↓
Builder 全局转换器
  ↓
内置类型映射
```

`DocumentMapperConfig` 对 Java Bean 和 Kotlin data class 字段都生效。

## 读写行为配置

`DocumentMapperConfig` 同时提供类似 Jackson `DeserializationFeature` / `SerializationFeature` 的开关：

```kotlin
val config = DocumentMapperConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.SET_EMPTY)
    .failOnUnknownFields(true)
    .writeNulls(false)
    .build()

val mapper = JsonMapper(config)
```

### missingFieldPolicy

文档缺少 Bean 字段时：

| 策略 | 行为 |
|---|---|
| `KEEP_DEFAULT` | 保留对象构造后的默认值（默认） |
| `SET_NULL` | 引用类型设为 `null`；基本类型保留默认值 |
| `SET_EMPTY` | `String` 设为 `""`；集合、Map、数组设为空集合；其他引用类型设为 `null` |
| `FAIL` | 抛出 `DocumentSerializationException` |

### failOnUnknownFields

```kotlin
DocumentMapperConfig.builder()
    .failOnUnknownFields(true)
    .build()
```

遇到文档中不存在于 Bean 的字段时抛出异常；默认忽略。

### writeNulls

```kotlin
DocumentMapperConfig.builder()
    .writeNulls(false)
    .build()
```

Bean 字段值为 `null` 时不写出该字段；默认写出。

JSON/YAML/XML 专属 Config 也提供这些方法：

```kotlin
val jsonConfig = JsonConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.SET_EMPTY)
    .failOnUnknownFields(true)
    .writeNulls(false)
    .allowComments(false)
    .pretty(true)
    .build()
```

> `missingFieldPolicy` / `failOnUnknownFields` 主要作用于普通 Java Bean；Kotlin data class 仍按主构造参数规则：必填参数缺失会报错，默认参数保留默认值。

## 日期时间格式

`@DocumentDateTimeFormat` 直接定义单个日期/时间字段的解析与格式化：

```kotlin
import com.mingliqiye.document.annotation.DocumentDateTimeFormat
import java.time.LocalDate
import java.util.Date

class Order {
    @field:DocumentDateTimeFormat("yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    var createdAt: Date? = null

    @field:DocumentDateTimeFormat("yyyy/MM/dd")
    var deliveryDate: LocalDate? = null
}
```

支持类型：

```text
java.util.Date
java.util.Calendar
java.sql.Date / Timestamp
java.time.LocalDate
java.time.LocalDateTime
java.time.LocalTime
java.time.OffsetDateTime
java.time.ZonedDateTime
java.time.Instant
```

`timezone` 可选；不传时使用系统默认时区。

优先级：

```text
DocumentMapperConfig 字段配置
  ↓
@DocumentConverter
  ↓
@DocumentDateTimeFormat
  ↓
@DocumentNumberFormat
  ↓
Builder 全局转换器
  ↓
内置类型映射
```

## 数字格式

`@DocumentNumberFormat` 使用 `DecimalFormat` 定义数字字段的解析与格式化：

```kotlin
import com.mingliqiye.document.annotation.DocumentNumberFormat
import java.math.BigDecimal

class Order {
    @field:DocumentNumberFormat("#,##0.00")
    var amount: BigDecimal? = null

    @field:DocumentNumberFormat("#,##0", locale = "de-DE")
    var count: Int = 0
}
```

输出示例：

```json
{"amount":"1,234.50","count":"1"}
```

支持目标类型：

```text
BigDecimal
BigInteger
Byte / Short / Int / Long
Float / Double
Number
```

`locale` 可选；不传时使用系统默认 Locale。

## 字段注解

注解位于 `com.mingliqiye.document.annotation`：

| 注解 | 作用 |
|---|---|
| `@DocumentName("name")` | 指定该字段在文档中的名称 |
| `@DocumentIgnore` | 序列化和反序列化时跳过该字段 |
| `@DocumentConverter(XxxConverter::class)` | 指定该字段专用的转换器实现类 |
| `@DocumentDateTimeFormat("yyyy-MM-dd", timezone = "Asia/Shanghai")` | 指定日期/时间字段的解析与格式化模式 |
| `@DocumentNumberFormat("#,##0.00", locale = "zh-CN")` | 指定数字字段的解析与格式化模式 |

字段注解示例（Kotlin；Java Bean 直接把注解写在字段上即可。普通 Kotlin 属性如果注解默认落在 property 上，Java 反射读不到，建议使用 `@field:`；引入 `serialization-kotlin` 后，data class / Kotlin 属性可以通过 Kotlin 反射读取无 use-site target 的注解）：

```kotlin
import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper

class Profile {
    @field:DocumentName("display_name")
    var name: String = ""

    @field:DocumentIgnore
    var age: Int = 0
}

fun main() {
    val mapper = JsonMapper()
    val node = mapper.toNode(Profile().apply { name = "小于" }).asObject()

    println(node["display_name"]?.asString()) // 小于
    println(node.containsKey("age"))          // false（被忽略）
    println(mapper.format(Profile().apply { name = "小于" })) // {"display_name":"小于"}
}
```

字段级转换器直接标注在字段上，只对该字段生效，转换器实例按类型缓存：

```kotlin
import com.mingliqiye.document.annotation.DocumentConverter
import com.mingliqiye.document.format.json.JsonMapper

// 复用上文的 Money 与 MoneyConverter
class Wallet {
    @field:DocumentConverter(MoneyConverter::class)
    var money: Money = Money(0)
}

fun main() {
    val mapper = JsonMapper() // 字段转换器不依赖文本 codec
    val node = mapper.toNode(Wallet().apply { money = Money(12) })
    println(node.asObject()["money"]?.asString()) // 12 CNY
}
```

> 字段级转换器实现类需要可实例化：无参构造器，或 Kotlin `object`（通过 `INSTANCE` 获取）；并且至少要实现 `JsonSerializer` 或 `JsonDeserializer`。缺少当前方向的实现时会回退到 Builder 注册的转换器或内置映射。

### 优先级

序列化/反序列化时，转换机制的生效顺序（高 → 低）：

1. **字段注解 `@DocumentConverter`**：只作用于被标注的字段，优先级最高；转换器只实现了单向接口时，缺失方向继续回退；
2. **Builder 注册的转换器**：`addSerializer` / `addDeserializer` / `addConverter` 注册的全局转换器；
3. **内置 Bean 映射**：getter/setter、集合、`Map`、数组、枚举、数字等默认处理。

一句话：**字段注解 > Builder 注册 > 内置 Bean 映射**。

同时，`@DocumentIgnore` 的字段在进入转换器之前就会被跳过；`@DocumentName` 在内置 Bean 映射中决定文档字段名，转换器也可以通过 `DocumentProperty.documentName` 读取到它，而 `DocumentProperty.name` 始终是 Java 属性名。

## 注解读取与 @AliasFor

`DocumentProperty.getAnnotation(...)` / `hasAnnotation(...)` 内部通过 `com.mingliqiye.utils.aliasfor:alias-for:1.0.3` 的 `AnnotationGetter` 读取注解，因此自定义注解可以用 `@AliasFor` 声明属性互为别名，读取任意一个属性都能得到合并后的值。

```kotlin
import com.mingliqiye.utils.aliasfor.AliasFor

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class DisplayText(
    @get:AliasFor("alias") val value: String = "",
    @get:AliasFor("value") val alias: String = "",
)
```

在转换器方法内读取（`context` 为 `SerializationContext` 或 `DeserializationContext`）：

```kotlin
val text = context.property?.getAnnotation(DisplayText::class.java)
val label = text?.value ?: text?.alias // value 与 alias 互为别名
```

`serialization` 以 `api` 依赖方式暴露 `alias-for`，使用方编译自定义注解时可直接引用 `@AliasFor`。

## JSON 支持

`JsonMapper` 是 JSON 格式的 Mapper，`JsonReader` 默认按 **JSONC** 方式解析：

- 允许 `//` 行注释和 `/* ... */` 块注释；
- 允许额外空白，并兼容 BOM 与常见 Unicode 空白字符；
- 语法本身保持严格 JSON：拒绝尾逗号、单引号、前导零（如 `01`）、前导加号（如 `+1`）、缺少小数/指数数字（如 `1.`、`1e`）、`NaN`/`Infinity` 等非法字面量；
- 解析失败抛出 `JsonParseException`，异常信息包含 `line`、`column` 定位。

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.format.json.JsonReader
import com.mingliqiye.document.format.json.toJsonNode
import com.mingliqiye.document.format.json.toJsonString

fun main() {
    val json = """
        // JSONC：允许注释与额外空白
        {
          "name": "小于", /* 行内注释 */
          "age": 18
        }
    """.trimIndent()

    val node = json.toJsonNode()
    println(node.asObject()["name"]?.asString()) // 小于
    println(node.toJsonString(pretty = true))    // 两空格缩进的 JSON

    // 严格模式：拒绝注释
    try {
        JsonReader("""{"a":1} // strict""", allowComments = false).read()
    } catch (e: JsonParseException) {
        println(e.message) // 带 line / column 的错误信息
    }

    // 也可以使用文档树扩展
    val parsed = json.toJsonNode()
    println(parsed.toJsonString(pretty = true))
}
```

- 从 JSON 解析得到的数字以 `BigDecimal` 保存精确值，并保留原始文本（`NumberNode.raw`），输出时按原文写回；
- JSON 最大嵌套深度为 512，超过时抛出 `JsonParseException`；
- 输出格式化使用两空格缩进。

## YAML 支持

YAML 使用 [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) 2.6，并通过 `SafeConstructor` 只还原标准 YAML 标量和集合。

```kotlin
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.format.yaml.toYamlNode
import com.mingliqiye.document.format.yaml.toYamlString

fun main() {
    val text = """
        name: 小于
        tags:
          - a
          - b
        profile:
          age: 18
    """.trimIndent()

    val node = text.toYamlNode()
    println(node.asObject()["name"]?.asString()) // 小于

    // 流式紧凑输出（默认）
    println(node.toYamlString())
    // 块格式输出
    println(node.toYamlString(pretty = true))

    // 作为 Mapper 使用
    val mapper = YamlMapper()
    println(mapper.formatPretty(node))
}
```

- YAML 映射键会转换为字符串（数字键 `1` 转为 `"1"`），复杂映射键不支持；
- 空文档解析为 `NullNode`；
- 重复键默认拒绝，解析失败抛出 `YamlParseException`；
- 不支持 YAML 的 `NaN` / `Infinity` 数字。

## XML 支持

`XmlMapper` 使用标准 JDK DOM/XML 能力，不需要额外 XML 依赖。

由于 XML 没有原生类型信息，`XmlWriter` 使用 `type` / `nil` 属性保存节点类型：

```xml
<root type="object">
  <name type="string">小于</name>
  <age type="number">18</number>
  <active type="boolean">true</boolean>
  <tags type="array">
    <item type="string">a</item>
    <item type="string">b</item>
  </tags>
  <address type="object">
    <city type="string">杭州</city>
  </address>
  <missing nil="true"/>
</root>
```

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper

val mapper = XmlMapper(
    XmlConfig.builder()
        .rootName("user")
        .build(),
)

val xml = mapper.formatPretty(user)
val restored = mapper.parse(xml, User::class.java)
```

数组统一写成：

```xml
<tags type="array"><item .../></tags>
```

对象写成嵌套元素；`null` 使用 `nil="true"`。

约束：

- XML attribute 暂不作为字段映射，只使用 `type` / `nil` 保留类型；
- 不支持自定义命名空间；
- 字段名中的非法 XML 字符会编码为 `_xHHHH_`；
- 解析器禁用 DOCTYPE 与外部实体，避免 XXE。

## Properties 支持

`PropertiesMapper` 将树/Bean 展平成 Java Properties 的 `key=value` 文本：

```kotlin
import com.mingliqiye.document.format.properties.PropertiesMapper
import com.mingliqiye.document.format.properties.toPropertiesNode
import com.mingliqiye.document.format.properties.toPropertiesString

val node = """
    user.name=小于
    user.age=18
    tags[0]=a
    tags[1]=b
""".trimIndent().toPropertiesNode()

println(node.toPropertiesString())

val mapper = PropertiesMapper()
val text = mapper.format(user)
val restored = mapper.parse(text, User::class.java)
```

路径约定：

```text
user.name       -> 对象字段
tags[0]         -> 数组元素
matrix[0][1]    -> 嵌套数组
```

字段名中的 `%`、`.`、`[`、`]` 使用百分号编码：

```text
user%2Ename     -> 字段名 "user.name"
```

约束：

- 根节点必须是对象或数组；
- Properties 只有字符串值，读取时按 `true` / `false` / 数字 / 字符串推断；
- 空对象、空数组不会写出；`null` 字段会写成空值；
- 不保留 Java Properties 的注释与原始顺序。

## TOML 支持

`TomlMapper` 使用 [Tomlj](https://github.com/tomlj/tomlj) 解析，并内置内联 table/array 写出：

```kotlin
import com.mingliqiye.document.format.toml.TomlMapper
import com.mingliqiye.document.format.toml.toTomlNode
import com.mingliqiye.document.format.toml.toTomlString

val node = """
    name = "小于"
    age = 18
    user = { city = "杭州" }
""".trimIndent().toTomlNode()

println(node.toTomlString())

val mapper = TomlMapper()
val restored = mapper.parse(mapper.format(user), User::class.java)
```

限制：

- TOML 没有 null；对象 null 字段省略，数组 null 元素报错；
- 日期时间读取为 ISO 字符串；
- 使用 inline table 输出，不输出 `[table]` 分节；
- Tomlj 1.0.0 对嵌套空数组的回读存在限制。

## CSV 支持

`CsvMapper` 面向平坦表格：

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.toCsvNode
import com.mingliqiye.document.format.csv.toCsvString

val rows = """
    name,age
    小于,18
    小明,20
""".trimIndent().toCsvNode()

println(rows.toCsvString())

val mapper = CsvMapper(CsvConfig.builder().delimiter(',').header(true).build())
val text = mapper.format(users)
```

限制：

- CSV 没有类型，读取时按 `true` / `false` / 数字 / 字符串推断；
- 只支持平坦对象行，嵌套对象/数组单元格抛 `CsvException`；
- 读取根固定为数组；单 Bean 建议用 `DocumentTypeReference.listType(Bean::class.java)`。

## Ion 支持

`IonMapper` 先支持文本 Ion：

```kotlin
import com.mingliqiye.document.format.ion.IonMapper
import com.mingliqiye.document.format.ion.toIonNode
import com.mingliqiye.document.format.ion.toIonString

val node = """{ name: "小于", age: 18 }""".toIonNode()
println(node.toIonString(pretty = true))

val mapper = IonMapper()
val restored = mapper.parse(mapper.format(user), User::class.java)
```

限制：

- 当前使用 String API，只支持文本 Ion，暂不支持二进制 Ion；
- Ion BLOB/CLOB 暂时映射为 Base64 字符串；
- symbol/timestamp 写出为字符串，Ion 注解忽略。

## 二进制节点与字节 API

`core` 现在提供 `BinaryNode`：

```kotlin
val bytes = BinaryNode.of(byteArrayOf(1, 2, 3))
val raw = bytes.asBinary()
```

`BinaryNode` 会复制输入/输出数组，并实现内容相等性。

`DocumentMapperApi` 增加字节 API：

```kotlin
mapper.parse(bytes, User::class.java)
mapper.formatBytes(user)
```

- 文本格式 Mapper 默认不支持字节 API，会抛 `DocumentSerializationException`；
- 二进制格式 Mapper 通过 `DocumentMapper` 的二进制解析/写出函数接入；
- Java `ByteArray` Bean 字段会映射为 `BinaryNode`；
- JSON/XML/YAML/Properties/TOML/CSV 暂时以 Base64 字符串兼容写出，Ion 原生映射为 blob。

这是 CBOR、Smile、Ion binary 等格式的基础 API。

## 性能基准

`benchmarks` 模块提供 JMH 基准。JDK8、1000 个 Bean（约 128 KB JSON）的结果：

| Benchmark | 本项目 | Jackson |
|---|---:|---:|
| JSON 解析树 | 0.492 ms | 0.329 ms |
| JSON → Bean | 0.724 ms | 0.298 ms |
| JSON → data class | 0.819 ms | 0.298 ms |
| Bean → JSON | 0.727 ms | 0.207 ms |

运行：

```bash
gradle :benchmarks:jmh
```

说明：

- 本项目面向统一不可变 Node 树，Jackson 直接绑定 Bean，两者不是同一工作负载
- `JsonMapper.format` 使用流式写出，不构建完整 Node 树
- 详细方法和结果见 [benchmarks/README.md](benchmarks/README.md)
## 构建与测试

```bash
# 全量格式检查与构建（含测试）
gradle ktlintCheck build

# 推荐使用仓库自带 Wrapper（Windows）
gradlew.bat ktlintCheck build

# 只跑单个模块的测试
gradle :serialization:test
gradle :format-json:test
gradle :format-yaml:test
gradle :format-xml:test
gradle :format-properties:test
gradle :format-toml:test
gradle :format-csv:test
gradle :format-ion:test
gradle :serialization-kotlin:test

# 自动格式化 Kotlin 代码
gradle ktlintFormat
```

- 所有模块均应用 `jvmToolchain(8)`，即使用 JDK 8 toolchain 编译与运行测试；本机需能解析到 JDK 8（`settings.gradle.kts` 启用了 Foojay toolchain resolver，缺失时会尝试自动下载）。
- 静态检查由 ktlint Gradle 插件执行，`ktlintCheck` 失败即视为构建失败。
- 若本机安装的 Gradle 版本与 Wrapper 不一致，优先使用仓库 Wrapper 复现构建结果（Wrapper 版本见 `gradle/wrapper/gradle-wrapper.properties`）。
- 测试使用 `kotlin.test` / JUnit Platform，`gradle build` 会执行各模块测试。

## 限制与路线图

当前已实现的范围：

- 格式实现有 JSON（`format-json`）、YAML（`format-yaml`）、XML（`format-xml`）、Properties（`format-properties`）、TOML（`format-toml`）、CSV（`format-csv`）和文本 Ion（`format-ion`）；
- Java Bean 映射支持：基本类型及包装类型、`String`、枚举、`BigDecimal`/`BigInteger`、数组、`Collection`、`Map`、以及 public getter/setter 或 public 字段的 Bean；
- Java Bean 需要无参构造器；Kotlin data class 可配合 `serialization-kotlin` 使用主构造器反序列化；
- `JsonMapper` / `YamlMapper` / `XmlMapper` / `PropertiesMapper` / `TomlMapper` / `CsvMapper` / `IonMapper` 自带对应格式的文本解析与写出；通用 `DocumentMapper` 不直接对外构造；
- 序列化检测循环引用，最大嵌套深度为 128；JSON 解析最大嵌套深度为 512。

**尚未实现 / 限制**：

- XML 适配器使用 `type` / `nil` 属性保存类型信息，暂不支持 XML attribute 映射与自定义命名空间；
- XML 数组统一写成 `<字段 type="array"><item>...</item></字段>`，对象写成嵌套元素；
- XML 解析器禁用 DOCTYPE 与外部实体，避免 XXE。
- 其他格式的通用限制见上文。

常见异常：

| 异常 | 模块 | 触发场景 |
|---|---|---|
| `DocumentException` | `exception` | 文档处理异常公共基类 |
| `DocumentTypeException` | `exception` | 节点类型与预期不符（如对数组调用 `asObject()`） |
| `DocumentSerializationException` | `serialization`（`com.mingliqiye.document.serialization.exception`） | Bean 映射失败、循环引用、格式函数不可用等 |
| `JsonParseException` | `format-json`（`com.mingliqiye.document.format.json.exception`） | JSON/JSONC 语法错误 |
| `YamlParseException` | `format-yaml`（`com.mingliqiye.document.format.yaml.exception`） | YAML 语法错误或不支持的类型 |
| `XmlParseException` | `format-xml`（`com.mingliqiye.document.format.xml.exception`） | XML 语法错误或不支持的内容 |
| `PropertiesParseException` | `format-properties`（`com.mingliqiye.document.format.properties.exception`） | Properties 语法错误或路径冲突 |
| `TomlParseException` | `format-toml`（`com.mingliqiye.document.format.toml.exception`） | TOML 语法错误 |
| `CsvParseException` | `format-csv`（`com.mingliqiye.document.format.csv.exception`） | CSV 语法错误 |
| `IonParseException` | `format-ion`（`com.mingliqiye.document.format.ion.exception`） | Ion 文本错误或根值数量不对 |

更多代码规范、提交规范与模块约束见 [CONTRIBUTING.md](CONTRIBUTING.md)。
