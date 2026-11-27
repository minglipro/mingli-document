# document

格式无关的文档树模型 + JSON/YAML 编解码 + Java Bean 映射。

`com.mingliqiye.document` 用一棵与具体序列化格式解耦的文档树（`Node`）作为中间层：

- **格式无关**：`core` 只定义节点树与类型引用，不依赖任何具体格式；
- **JSON/YAML Mapper**：`format-json` 提供 `JsonMapper`，`format-yaml` 提供 `YamlMapper`，两者继承通用 `DocumentMapper`；
- **Java Bean 映射**：`serialization` 负责 Java/Kotlin 实体与文档树之间的双向映射，支持字段注解与自定义转换器。

> XML **尚未实现**，只在路线图中；详见「限制与路线图」。

## 模块与依赖

| 模块 | 职责 | 依赖 |
|---|---|---|
| `annotation` | 格式无关注解：`@DocumentName`、`@DocumentIgnore`、`@DocumentConverter` | 无 |
| `exception` | 文档异常基类 `DocumentException` / `DocumentTypeException` | `com.mingliqiye.exception:mingli-exception:1.1.1-Final` |
| `core` | 文档树 `Node`、`DocumentTypeReference` | `exception` |
| `format-json` | JSON/JSONC Mapper `JsonMapper`、`JsonReader` | `serialization`、`core` |
| `format-yaml` | YAML Mapper `YamlMapper`、`YamlReader` | `serialization`、`core`、SnakeYAML |
| `serialization` | `DocumentMapper` 基类、Java Bean ↔ `Node` 映射、转换器、字段注解与别名注解读取 | `core`、`annotation`、`com.mingliqiye.utils.aliasfor:alias-for:1.0.3` |
| `serialization-kotlin` | Kotlin data class 支持模块 `KotlinModule` | `serialization`、`annotation`、`kotlin-reflect` |

依赖方向（`A ──► B` 表示 A 依赖 B）：

```text
format-json ──► serialization ──► core ──► exception ──► mingli-exception
format-yaml ──► serialization
serialization-kotlin ──► serialization
serialization ──► annotation（implementation）
              ──► alias-for 1.0.3（api）
annotation：项目内无依赖
```

- `core` 不依赖任何具体格式；`format-json` / `format-yaml` 依赖 `serialization`，并继承其 `DocumentMapper` 基类。
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
| `format(value)` / `formatPretty(value)` | Java 实体 → 紧凑 / 格式化文本 |
| `convert(source, Class)` / `convert(source, DocumentTypeReference)` | 实体↔实体、`Node`↔实体转换 |

> `JsonMapper` / `YamlMapper` 继承 `DocumentMapper`；格式模块内部 Codec 不对外暴露。

## Kotlin data class

Java Bean 需要无参构造器；Kotlin data class 可以通过 `serialization-kotlin` 使用主构造器反序列化。

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.KotlinModule

data class User(val name: String, val age: Int)

fun main() {
    val mapper = JsonMapper.builder()
        .addModule(KotlinModule)
        .build()

    val user = mapper.parse("""{"name":"小于","age":18}""", User::class.java)
    println(mapper.formatPretty(user))
}
```

YAML 同样注册模块：

```kotlin
val mapper = YamlMapper.builder()
    .addModule(KotlinModule)
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

## 字段注解

注解位于 `com.mingliqiye.document.annotation`：

| 注解 | 作用 |
|---|---|
| `@DocumentName("name")` | 指定该字段在文档中的名称 |
| `@DocumentIgnore` | 序列化和反序列化时跳过该字段 |
| `@DocumentConverter(XxxConverter::class)` | 指定该字段专用的转换器实现类 |

字段注解示例（Kotlin；Java Bean 直接把注解写在字段上即可。Kotlin 属性可以不写 use-site target，注解默认落在字段上；需要标在 getter/setter 时使用 `@get:` / `@set:`）：

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

- 格式实现有 JSON（`format-json`）和 YAML（`format-yaml`）；
- Java Bean 映射支持：基本类型及包装类型、`String`、枚举、`BigDecimal`/`BigInteger`、数组、`Collection`、`Map`、以及 public getter/setter 或 public 字段的 Bean；
- Java Bean 需要无参构造器；Kotlin data class 可配合 `serialization-kotlin` 使用主构造器反序列化；
- `JsonMapper` / `YamlMapper` 自带对应格式的文本解析与写出；通用 `DocumentMapper` 不直接对外构造；
- 序列化检测循环引用，最大嵌套深度为 128；JSON 解析最大嵌套深度为 512。

**尚未实现**：

- XML 适配器尚未实现；格式模块通过继承 `DocumentMapper` 提供 Mapper，内部 Codec 不对外暴露。
- XML 适配器计划在 `core` API 稳定后再接入。

常见异常：

| 异常 | 模块 | 触发场景 |
|---|---|---|
| `DocumentException` | `exception` | 文档处理异常公共基类 |
| `DocumentTypeException` | `exception` | 节点类型与预期不符（如对数组调用 `asObject()`） |
| `DocumentSerializationException` | `serialization`（`com.mingliqiye.document.serialization.exception`） | Bean 映射失败、循环引用、格式函数不可用等 |
| `JsonParseException` | `format-json`（`com.mingliqiye.document.format.json.exception`） | JSON/JSONC 语法错误 |
| `YamlParseException` | `format-yaml`（`com.mingliqiye.document.format.yaml.exception`） | YAML 语法错误或不支持的类型 |

更多代码规范、提交规范与模块约束见 [CONTRIBUTING.md](CONTRIBUTING.md)。
