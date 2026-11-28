---
title: 迁移指南
---

# 迁移指南

> 本页覆盖两条常见迁移路线：**1.x -> 2.0.0** 与 **从 Jackson 迁移**。
> 所有结论都基于仓库内的 `MIGRATION.md`、`CHANGELOG.md` 与源码，不承诺与第三方库
> 完全等价。

## 目录

- [版本路线](#版本路线)
- [从 1.x 升级到 2.0.0](#从-1-x-升级到-2-0-0)
- [坐标与 BOM 升级](#坐标与-bom-升级)
- [转换器接口改名](#转换器接口改名)
- [注解泛型收紧](#注解泛型收紧)
- [默认行为兼容性](#默认行为兼容性)
- [1.x 各版本新增能力](#_1-x-各版本新增能力)
- [从 Jackson 迁移](#从-jackson-迁移)
- [概念对照表](#概念对照表)
- [代码对照示例](#代码对照示例)
- [行为差异与常见注意](#行为差异与常见注意)
- [迁移检查清单](#迁移检查清单)

## 版本路线

```text
1.0.0       初始版本
1.1.0       文件/流 API
1.2.0       各格式 maxDepth、remoteReferenceResolver
1.3.0       Ion preserveTypes、CBOR preserveTags、maxStringLength、YAML maxAliases
1.4.0       原生 Token Reader（CSV/Properties/TOML/YAML/XML）、JSON Schubfach、fuzzContinuous
1.5.0       Jazzer 目标、fuzzJazzer
2.0.0       代码生成、record、merge、Kotlin value/sealed、CSV 嵌套、Protobuf 扩展、OpenAPI 等
```

`MIGRATION.md` 对 2.0.0 的定位是：

> 2.0.0 汇总 1.3.0 之后的能力，API 总体兼容。

也就是说，1.x 的公开 API 基本保留，主要变化集中在**转换器命名**与
**`@DocumentConverter` 泛型收紧**两处。

## 从 1.x 升级到 2.0.0

### 1. 升级版本

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))
    implementation("com.mingliqiye.document.format:json")
    // 或者手动把每个模块版本从 1.x 改为 2.0.0
}
```

### 2. 改转换器接口名

旧代码：

```kotlin
import com.mingliqiye.document.serialization.JsonConverter

class MoneyConverter : JsonConverter<Money> {
    // ...
}
```

新代码：

```kotlin
import com.mingliqiye.document.serialization.DocumentBaseConverter

class MoneyConverter : DocumentBaseConverter<Money> {
    override fun getType(): DocumentTypeReference<Money> =
        DocumentTypeReference.of(Money::class.java)

    override fun serialize(value: Money, context: SerializationContext): Node { /* ... */ }

    override fun deserialize(node: Node, context: DeserializationContext): Money? { /* ... */ }
}
```

改名对照：

```text
JsonConverter      -> DocumentBaseConverter
JsonSerializer     -> DocumentBaseSerializer
JsonDeserializer   -> DocumentBaseDeserializer
```

### 3. 检查 `@DocumentConverter` 用法

2.0.0 的 `@DocumentConverter` 只接受**双向**的 `DocumentBaseConverter`。
如果旧代码传的是单向实现：

```kotlin
// 旧：可能只实现了单向
@DocumentConverter(MySerializer::class)
```

需要改成：

```kotlin
@DocumentSerializer(MySerializer::class)
// 或
@DocumentDeserializer(MyDeserializer::class)
```

### 4. 注解模块依赖

`serialization` 现在通过 `api` 暴露 `annotation`（注解中的标记接口是
`DocumentBaseSerializer` 等的父类型）。正常使用 Gradle/Maven 传递依赖即可，
**不要**把 `annotation` 声明为 `compileOnly`。

### 5. 检查新增能力是否影响你

2.0.0 新增能力默认都是关闭/兼容的，但部分行为值得确认：

| 能力 | 默认 | 如果你依赖旧行为 |
|---|---|---|
| `writeReferences` | `false` | 无需改动 |
| `resolveReferences` | `false` | 无需改动 |
| `maxInputSize` / `maxNodeDepth` / `maxNodeCount` | `0`（不限制） | 无需改动 |
| `atomicFileWrite` / `createParentDirectories` | `false` | 无需改动 |
| 字段命名策略 | `AS_IS` | 无需改动 |
| CBOR tag 保真 / Ion 类型保真 | `false` | 无需改动 |
| YAML 自定义 tag | `false`（拒绝） | 若旧版本能解析未知 tag，需要显式开 `allowCustomTags(true)` |
| JSON Schubfach Double 输出 | 始终启用 | 输出文本可能更短（语义不变） |
| 原生 Token Reader | 默认启用 | 若旧代码依赖「先构建 Node」的副作用，需要显式走 Node 路径 |

## 坐标与 BOM 升级

2.0.0 的 BOM 覆盖：

```text
annotation exception core schema serialization serialization-kotlin codegen openapi
format:{json,yaml,xml,properties,toml,csv,ion,cbor,smile,avro,protobuf}
```

Gradle：

```kotlin
dependencies {
    implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))
    implementation("com.mingliqiye.document:core")
    implementation("com.mingliqiye.document.format:json")
}
```

Maven：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.mingliqiye.document</groupId>
            <artifactId>mingli-document-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 转换器接口改名

再强调一次包路径不变，只是类名变了：

```text
com.mingliqiye.document.serialization.JsonConverter
com.mingliqiye.document.serialization.JsonSerializer
com.mingliqiye.document.serialization.JsonDeserializer
```

改为：

```text
com.mingliqiye.document.serialization.DocumentBaseConverter
com.mingliqiye.document.serialization.DocumentBaseSerializer
com.mingliqiye.document.serialization.DocumentBaseDeserializer
```

自定义转换器注册方式不变：

```kotlin
JsonMapper.builder()
    .addConverter(MoneyConverter())
    .addSerializer(...)
    .addDeserializer(...)
    .build()
```

## 注解泛型收紧

2.0.0 的注解定义：

```kotlin
public annotation class DocumentConverter(
    val value: KClass<out DocumentBaseConverter<*>>,
)

public annotation class DocumentSerializer(
    val value: KClass<out DocumentBaseSerializer<*>>,
)

public annotation class DocumentDeserializer(
    val value: KClass<out DocumentBaseDeserializer<*>>,
)
```

迁移原则：

- 双向转换器 -> `@DocumentConverter`
- 只写出 -> `@DocumentSerializer`
- 只读入 -> `@DocumentDeserializer`
- 缺失的方向会回退到全局转换器或内置映射。

## 默认行为兼容性

以下配置默认值保持与 1.x 一致（或为不改变行为的新开关），不需要迁移：

```text
writeReferences          默认 false
resolveReferences        默认 false
maxInputSize             默认 0（不限制）
maxNodeDepth             默认 0（不限制）
maxNodeCount             默认 0（不限制）
maxStringLength          默认 0（不限制）
atomicFileWrite          默认 false
createParentDirectories  默认 false
namingStrategy           默认 AS_IS
```

## 1.x 各版本新增能力

升级时按版本确认即可，均不需要代码迁移：

**1.2.0**

```text
Protobuf/Avro/Ion/YAML/XML maxDepth
remoteReferenceResolver
```

**1.3.0**

```text
IonConfig.preserveTypes
CborConfig.preserveTags
maxStringLength
YAML maxAliases
CSV 原生 Token Reader（内部路径）
```

**1.4.0**

```text
CSV / Properties / TOML / YAML / XML 原生 Token Reader
JSON Schubfach Double 格式化
fuzzContinuous
YAML maxAliases
```

**1.5.0**

```text
Jazzer 目标与 fuzzJazzer 任务
fuzzContinuous
```

**2.0.0**

```text
codegen（KSP）、DocumentRuntimeCodecFactory、DocumentAccessorCodegen
Java record 支持
只读属性与 merge
CBOR sequence、Smile 多消息流
Kotlin value class / sealed class
CSV 嵌套结构 / 多级 Schema
Protobuf extensions / group / MessageSet / proto3 语义
Security baseline、OpenAPI 模块、Maven Central 发布
YAML 自定义 tag、JQF fuzz、全格式原生 Token Reader
```

## 从 Jackson 迁移

`mingli-document` 的 `serialization` 模块在设计上对标 Jackson 的 `ObjectMapper`：
实体 ↔ 树 ↔ 格式。区别在于：

1. **树模型格式无关**：`Node` 是所有格式共享的树，而不是 JSON 专用；
2. **格式 Mapper 分离**：没有「一个 ObjectMapper 读所有格式」，而是 `JsonMapper`、
   `YamlMapper`… 各自继承同一套 API；
3. **注解前缀不同**：`@DocumentXxx` 而不是 `@JsonXxx`；
4. **多态默认关闭**：必须显式注册子类型，不扫描 classpath。

### 概念对照表

| Jackson | mingli-document |
|---|---|
| `ObjectMapper` / `JsonMapper` | `JsonMapper` / `YamlMapper` / `XmlMapper` …（都实现 `DocumentMapperApi`） |
| `JsonNode` | `Node` |
| `ObjectNode` / `ArrayNode` | `ObjectNode` / `ArrayNode` |
| `TextNode` / `IntNode` / `BooleanNode` / `BinaryNode` / `NullNode` | `StringNode` / `NumberNode` / `BooleanNode` / `BinaryNode` / `NullNode` |
| `TypeReference<T>` | `DocumentTypeReference<T>` |
| `readValue(String, Class)` | `parse(source, Class)` |
| `writeValueAsString(value)` | `format(value)` |
| `writerWithDefaultPrettyPrinter()` | `formatPretty(value)` / `JsonConfig.pretty(true)` |
| `writeValueAsBytes` | `formatBytes(value)` |
| `readTree` / `valueToTree` | `parse(source, Node::class.java)` / `toNode(value)` |
| `treeToValue` | `fromNode(node, Class)` |
| `convertValue` | `convert(source, Class)` |
| `@JsonProperty("name")` | `@DocumentName("name")` |
| `@JsonIgnore` | `@DocumentIgnore` |
| `@JsonCreator` + `@JsonProperty` 参数 | `@DocumentCreator` + `@DocumentName` 参数 |
| `@JsonNaming(SnakeCaseStrategy)` | `namingStrategy(PropertyNamingStrategy.SNAKE_CASE)` |
| `@JsonInclude(NON_NULL)` | `writeNulls(false)` |
| `@JsonSerialize(using=...)` | `@DocumentSerializer(...)` |
| `@JsonDeserialize(using=...)` | `@DocumentDeserializer(...)` |
| `@JsonTypeInfo` / `@JsonSubTypes` | `@DocumentTypeInfo` + `config.subtype(...)` / `kotlinSealedSubtypes(...)` |
| `SimpleModule` + `addSerializer/addDeserializer` | `DocumentModule` + `context.addConverter(...)` |
| `registerModule(module)` | `JsonMapper.builder().addModule(module).build()` |
| `FAIL_ON_UNKNOWN_PROPERTIES` | `failOnUnknownFields(true)` |
| `SerializationFeature.INDENT_OUTPUT` | `formatPretty` / `JsonConfig.pretty(true)` |
| `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` | 无直接开关（null 写基本类型会抛异常） |
| `JsonParser` / `JsonGenerator` | `DocumentTokenReader` / `DocumentTokenWriter` |
| `JsonSchema`（第三方模块） | `format-json` 内置轻量 `JsonSchema`（仅 JSON 文本校验） |

::: warning 不是 1:1 兼容
上表是「概念对应」，不是 API 兼容承诺。例如 Jackson 的 `JsonNode` 有 `path()`、
`at()`、`deepCopy()` 等大量方法，本库的 `Node` 只保留类型断言与容器读取；
Jackson 的 `ObjectMapper` 配置项远多于 `DocumentMapperConfig`。
:::

## 代码对照示例

Jackson 版本：

```kotlin
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class User(
    @JsonProperty("display_name") val name: String,
    val age: Int?,
)

val jackson = jacksonObjectMapper()
val user = jackson.readValue<User>("""{"display_name":"小于","age":18}""")
val json = jackson.writerWithDefaultPrettyPrinter().writeValueAsString(user)
```

mingli-document 版本：

```kotlin
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class User(
    @DocumentName("display_name") val name: String,
    val age: Int?,
)

val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

val user = mapper.parse("""{"display_name":"小于","age":18}""", User::class.java)
val json = mapper.formatPretty(user)
```

Jackson 树操作：

```kotlin
val node = jackson.readTree("""{"a":{"b":1}}""")
val b = node["a"]["b"].asInt()
```

mingli-document 树操作：

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse("""{"a":{"b":1}}""", Node::class.java)
val b = node.asObject()["a"]!!.asObject()["b"]!!.asNumber().toInt()
```

## 行为差异与常见注意

1. **未知字段默认忽略**：Jackson 默认 `FAIL_ON_UNKNOWN_PROPERTIES=true`，本库默认
   忽略。需要严格校验时显式 `failOnUnknownFields(true)`。
2. **Java 私有字段**：Jackson 可以通过配置让私有字段直接映射；本库依赖 JavaBeans
   Introspector 或 `public` 字段，**没有 getter/setter 的私有字段不会映射**。
   请补 getter/setter、改成 public 字段，或使用 `@DocumentCreator` / record。
3. **Kotlin data class 需要模块**：对应 Jackson 的 `jackson-module-kotlin`，
   本库需要 `addModule(kotlinModule())`。
4. **Java record 需要 JDK 16+ 运行时**：编译目标仍可保持 JDK 8。
5. **多态必须显式注册**：Jackson 的 `@JsonSubTypes` 自动注册；本库需要
   `config.subtype(...)` 或 Kotlin `kotlinSealedSubtypes(...)`，不扫描 classpath。
6. **日期需要注解**：Jackson 有内置日期序列化器；本库需要
   `@DocumentDateTimeFormat(pattern, timezone)` 或自定义转换器。
7. **枚举按 `name()` 精确匹配**：不支持下划线/大小写宽松匹配，未知枚举值抛异常
   （除非使用生成的枚举转换器，其非法值返回 `null`）。
8. **Kotlin 注解 use-site target**：字段注解常需要 `@field:DocumentName(...)`；
   注解定义同时支持 `FIELD` / `PROPERTY` / getter，但默认落点取决于注解定义。
9. **null 行为**：`writeNulls` 默认 `true`（与 Jackson 默认一致）；关闭后 null 字段
   直接省略，读取端按 `MissingFieldPolicy` 处理。
10. **流式写出不等于 Jackson 的 `JsonGenerator`**：Token 路径是内部 API 的开放形式，
    但 `writeReferences`、merge 等 Node 能力在流式路径不可用。
11. **Map key**：写出一律 `toString()`；读出按声明 key 类型解析，复杂对象 key 不支持。
12. **JSON 字节 API 固定 UTF-8**：Jackson 可用 `JsonEncoding` 配置，本库 JSON 走严格
    UTF-8 字节语义。
13. **没有 `@JsonPropertyOrder`**：字段顺序由 JavaBeans 属性顺序/字段顺序决定，
    不提供显式排序注解。
14. **`ObjectMapper` 级别的 mixin、过滤器、视图**：不存在对应能力；需要时在 Node 层
    手工处理。

## 迁移检查清单

从 1.x：

- [ ] 版本号/BOM 改成 `2.0.0`；
- [ ] 全局搜索 `JsonConverter` / `JsonSerializer` / `JsonDeserializer` 并改名；
- [ ] 检查 `@DocumentConverter` 是否传入单向实现（改成 Serializer/Deserializer）；
- [ ] 确认 `annotation` 在编译类路径（传递依赖即可）；
- [ ] 若旧代码依赖未知 YAML tag 自动解析，开启 `allowCustomTags(true)`；
- [ ] 跑一遍 `gradle :fuzz:test` 与业务回归测试。

从 Jackson：

- [ ] 建立实体对照：字段注解换成 `@DocumentXxx`；
- [ ] 泛型目标类型换成 `DocumentTypeReference`；
- [ ] 配置迁移：`DocumentMapperConfig.builder()...`；
- [ ] 决定是否需要 `kotlinModule()` / record 运行时；
- [ ] 多态子类型显式注册；
- [ ] 日期字段补 `@DocumentDateTimeFormat`；
- [ ] 未知字段策略确认（默认从 fail 变成 ignore）；
- [ ] 树操作代码改成 `Node` API；
- [ ] 流式场景改成 `format(value, outputStream)` / Token API；
- [ ] 双跑一段时间，对比两边输出（尤其是数字、日期、null、枚举）。

下一步：[常见问题](./faq.md) 汇总了使用中最容易踩的坑。
