# serialization

Java Bean / Kotlin 实体与文档树之间的通用映射模块。

格式无关，具体格式由 `format-*` 模块注入。

## 主要能力

- `DocumentMapper` / `DocumentMapperApi`
- `DocumentMapperBuilder`
- `DocumentMapperConfig`
- 字段转换器、日期格式、数字格式
- 根对象 Bean 校验扩展点 `DocumentBeanValidator`（配合 `validation` 模块）
- 多态类型注册与类型属性
- Bean 元数据缓存与访问器
- 只读属性与构造器注入
- Java record：通过可选 `record-support` 模块（JDK 17+，`JavaRecordSupport` SPI）支持，
  未引入时安全降级为普通 Bean
- `merge` 合并到已有对象（`MergePolicy`）
- 对象映射便捷 API：`readTree` / `readValue` / `writeValueAs*` / `updateValue` /
  `convertValue` / `readerFor` / `writer`（format-agnostic，`DocumentObjectReader` /
  `DocumentObjectWriter` 轻量包装）
- `DocumentTokenReader` / `DocumentTokenWriter`
- `BinaryDocumentMapper`
- `NodeTokenReader`

## Maven

```kotlin
implementation("com.mingliqiye.document:serialization:2.5.1")
```

## 主要 API

```kotlin
mapper.toNode(value)
mapper.fromNode(node, User::class.java)
mapper.parse(text, User::class.java)
mapper.parse(bytes, User::class.java)
mapper.format(value)
mapper.formatBytes(value)
mapper.parse(file, User::class.java)
mapper.format(value, outputStream)
mapper.formatBytes(value)                   // 文本格式返回 charset 编码字节；二进制返回原始字节
mapper.formatPretty(value, file)            // 格式化文本写入 File
mapper.formatPretty(value, path)            // 格式化文本写入 Path
mapper.formatPretty(value, outputStream)    // 格式化文本写入 OutputStream
mapper.formatPretty(value, writer)          // 格式化文本写入 Writer
mapper.merge(node, target)                    // 合并到已有对象，未出现字段保留
mapper.merge(node, target, MergePolicy.DEEP)  // 嵌套 Bean 深合并

// 对象映射便捷 API（全部 format-agnostic，语义等价于上面的 parse/format/toNode/fromNode）
mapper.readTree(text)                          // 文本 -> Node 树（支持 String/ByteArray/File/Path/InputStream/Reader）
mapper.writeTree(node)                         // Node 树 -> 文本
mapper.writeTreeAsBytes(node)                  // Node 树 -> 字节
mapper.valueToTree(user)                       // 等价 toNode
mapper.treeToValue(node, User::class.java)     // 等价 fromNode
mapper.readValue(text, User::class.java)       // 等价 parse
mapper.writeValueAsString(user)                // 等价 format
mapper.writeValueAsBytes(user)                 // 等价 formatBytes
mapper.updateValue(user, overrides)            // 覆盖内容合并到已有对象
mapper.convertValue(source, User::class.java)  // convert 的别名
mapper.readerFor(User::class.java)             // DocumentObjectReader：绑定目标类型
mapper.writerWithDefaultPrettyPrinter()        // DocumentObjectWriter：格式化输出
```

## 对象映射 API

`DocumentMapperApi` 在既有 `parse` / `format` / `toNode` / `fromNode` / `convert` /
`merge` 之上提供了一组便捷命名。它们不引入新的格式绑定，也不改变原有语义，只是为了
迁移与日常使用更直观；所有 `format-*` Mapper（JSON、YAML、XML、CBOR、Avro 等）都可共用。

### 树 API

| 方法 | 说明 |
|---|---|
| `readTree(String/ByteArray/File/Path/InputStream/Reader)` | 解析为 `Node` 树；读取规则与 `parse` 一致，顶层 `null` 返回 `NullNode.INSTANCE` |
| `writeTree(node)` | `Node` 树写为文本，等价 `format(node)` |
| `writeTreeAsBytes(node)` | `Node` 树写为字节，等价 `formatBytes(node)` |
| `valueToTree(value)` | 任意对象转树，等价 `toNode(value)` |
| `treeToValue(node, Class/DocumentTypeReference)` | 树转实体，等价 `fromNode(node, ...)` |

```kotlin
val tree = mapper.readTree("""{"name":"小于","age":18}""")
val name = tree.asObject()["name"]?.asString()   // 小于
val user = mapper.treeToValue(tree, User::class.java)
val compact = mapper.writeTree(tree)              // {"name":"小于","age":18}
```

`readTree` 与 `parse(source, Node::class.java)` 的差别只在顶层 `null`：`parse` 返回
`null`，`readTree` 统一返回 `NullNode.INSTANCE`，便于直接当树使用。同理，
`treeToValue(NullNode.INSTANCE, User::class.java)` 返回 `null`，可空场景请用可空变量接收。

### 值 API

| 方法 | 说明 |
|---|---|
| `readValue(source, Class/DocumentTypeReference)` | 六种来源（`String` / `ByteArray` / `File` / `Path` / `InputStream` / `Reader`）解析为实体，等价 `parse` |
| `writeValueAsString(value)` | 等价 `format(value)` |
| `writeValueAsBytes(value)` | 等价 `formatBytes(value)` |
| `updateValue(target, overrides)` | `toNode(overrides)` 后按 `MergePolicy.REPLACE` 合并到 `target` |
| `convertValue(source, Class/DocumentTypeReference)` | 等价 `convert(source, ...)` |

`updateValue` 的返回值即合并结果：

```kotlin
val user = mapper.readValue("""{"name":"a","age":18}""", User::class.java)

val updated = mapper.updateValue(user, mapOf("age" to 20))
// 可变 Bean：就地修改并返回同一实例；记录/构造器 Bean 返回新实例，必须接收返回值
```

语义注意事项：

- `overrides` 可以是实体、`Map` 或对象 `Node`；没有出现的字段保留目标对象原值；
- `overrides` 为 `null`（`NullNode`）或 `target` 为 `null` 时抛
  `DocumentSerializationException`；
- 合并规则、深合并与 null 策略与 `merge` 完全一致（详见下文「合并已有对象」）。

### Reader / Writer

`readerFor` / `writer` 是对 Mapper 的轻量包装，只固定「目标类型」或「输出风格」，
不复制完整配置能力（没有逐字段过滤器、schema 切换等进阶配置）；字符集、输入大小限制、
`$ref` 解析、未知字段策略等仍然由 Mapper 决定。

| 方法 | 返回 | 说明 |
|---|---|---|
| `readerFor(Class)` / `readerFor(DocumentTypeReference)` | `DocumentObjectReader<T>` | 绑定目标类型的读取器 |
| `writer()` | `DocumentObjectWriter` | 紧凑风格写出器 |
| `writerWithDefaultPrettyPrinter()` | `DocumentObjectWriter` | 格式化风格写出器 |

```kotlin
val reader = mapper.readerFor(User::class.java)
val fromFile = reader.readValue(File("user.json"))
val fromText = reader.readValue("""{"name":"a"}""")

val addresses = mapper.readerFor(DocumentTypeReference.of<List<Address>>()).readValue(text)
```

Kotlin reified 版本位于 `serialization-kotlin`：

```kotlin
import com.mingliqiye.document.serialization.kotlin.readerFor
import com.mingliqiye.document.serialization.kotlin.parse

val typed: List<User> = mapper.readerFor<List<User>>().readValue(text)
val user: User = mapper.parse<User>(text)
```

val writer = mapper.writerWithDefaultPrettyPrinter()
writer.writeValueAsString(user)
writer.writeValue(user, File("user.json"))
writer.writeValue(user, outputStream)   // 只 flush，不关闭流
writer.writeValue(user, stringWriter)   // 只 flush，不关闭 Writer
```

`DocumentObjectReader` 的 `readValue` 覆盖 `String` / `ByteArray` / `File` / `Path` /
`InputStream` / `Reader`；`DocumentObjectWriter` 的 `writeValue` 覆盖 `File` / `Path` /
`OutputStream` / `Writer`，另有 `writeValueAsString` / `writeValueAsBytes`。

### 与常见对象映射 API 对照

| 常见对象映射库写法 | 本库写法 | 等价底层方法 |
|---|---|---|
| `readTree(source)` | `readTree(source)` / `readTree(file)` / `readTree(input)` … | 内部以 `parse(source, Any::class.java)` 取节点，顶层 `null` 返回 `NullNode` |
| `writeTree(node)` | `writeTree(node)` / `writeTreeAsBytes(node)` | `format(node)` / `formatBytes(node)` |
| `valueToTree(value)` | `valueToTree(value)` | `toNode(value)` |
| `treeToValue(node, type)` | `treeToValue(node, type)` | `fromNode(node, type)` |
| `readValue(source, type)` | `readValue(source, type)` | `parse(source, type)` |
| `writeValueAsString(value)` | `writeValueAsString(value)` | `format(value)` |
| `writeValueAsBytes(value)` | `writeValueAsBytes(value)` | `formatBytes(value)` |
| `updateValue(target, overrides)` | `updateValue(target, overrides)` | `merge(toNode(overrides), target)` |
| `convertValue(source, type)` | `convertValue(source, type)` | `convert(source, type)` |
| `readerFor(type)` | `readerFor(type)` | 返回 `DocumentObjectReader<T>` |
| `writer()` / pretty writer | `writer()` / `writerWithDefaultPrettyPrinter()` | 返回 `DocumentObjectWriter` |

### 兼容与限制

- 树/值 API 全部基于既有入口，`parse` / `format` / `toNode` / `fromNode` 的行为、
  异常类型、`$ref`、Node 限制、Bean 校验、文件写入选项都保持不变；
- 二进制格式同样支持树 API 与字节 API；文本专用的 `readTree(String)`、`Writer`、
  `writeValueAsString`、`writerWithDefaultPrettyPrinter()` 在二进制格式上抛
  `DocumentSerializationException`；
- `readTree`/`readValue` 走与 `parse` 相同的 Token/Node 路径，因此不额外改变配置语义；
- 接口默认方法保证 Kotlin 自定义 `DocumentMapperApi` 实现无需改动；`DocumentMapper`
  显式覆写全部新方法，Java 直接通过 `JsonMapper` 等具体类型即可调用，无需类型转换。

## 配置示例

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy

val config = DocumentMapperConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.FAIL)
    .failOnUnknownFields(true)
    .writeNulls(false)
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .fileCharset(Charsets.UTF_8)
    .atomicFileWrite(true)
    .createParentDirectories(true)
    .maxInputSize(16L * 1024 * 1024)
    .maxNodeDepth(64)
    .maxNodeCount(100_000)
    .build()
```

`PropertyNamingStrategy` 支持：

```text
AS_IS（默认）
SNAKE_CASE
KEBAB_CASE
UPPER_CAMEL
LOWER_CAMEL
```

`@DocumentName` 显式注解优先于命名策略。

文件读写选项：

```text
fileCharset             文本文件/流字符集，默认 UTF-8
atomicFileWrite         临时文件 + 原子替换
createParentDirectories 自动创建父目录
maxInputSize            单次输入最大字节数，0 表示不限制
maxStringLength         Node 树单个字符串最大长度，0 表示不限制
maxNodeDepth            Node 树最大深度，0 表示不限制
maxNodeCount            Node 树最大节点数，0 表示不限制
resolveReferences       反序列化前解析本地 $ref，默认 false
writeReferences         序列化时输出对象身份 $ref，默认 false
```

本地 `$ref`：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .resolveReferences(true)
        .build(),
)

val node = mapper.fromNode(JsonReader.parse(document), Map::class.java)
```

支持 `#/...` JSON Pointer、循环检测；只支持本地引用。

对象身份引用：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .writeReferences(true)
        .build(),
)
```

同一个可变对象第二次出现时写成：

```json
{"$ref": "#/first"}
```

仅 `toNode` / Node 路径支持；流式 `format` 仍保持原行为。

远程 `$ref` 需要显式提供解析器：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .resolveReferences(true)
        .remoteReferenceResolver { uri ->
            loadDocument(uri) // 返回外部文档 Node
        }
        .build(),
)
```

`remoteReferenceResolver` 默认 `null`，此时远程引用直接报错；URI 校验、IO 与缓存由调用方负责。

## DeserializationFeature

Jackson 风格的反序列化特性开关，默认**全部关闭**（与 Jackson 默认配置一致，升级不改变
既有行为）。通过 `DocumentMapperConfigBuilder` 配置：

```kotlin
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentMapperConfig

val config = DocumentMapperConfig.builder()
    .failOnNullForPrimitives(true)                               // 便捷方法
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)  // 等价写法
    .build()
val mapper = JsonMapper(config)
```

| 特性 | 默认 | 行为 |
|---|---|---|
| `FAIL_ON_NULL_FOR_PRIMITIVES` | 关闭 | 开启后反序列化 `null` 到 Java 基本类型时抛 `DocumentSerializationException`（信息含字段路径与类型）；关闭时使用 `0` / `false` / `'\u0000'` 等基本类型默认值，不抛异常 |

覆盖位置：

```text
fromNode / fromTokens / parse（含 ByteArray/File/Stream）
字段注入、@DocumentCreator 构造参数、Builder 属性、Java record 组件、
基本类型数组元素
```

查询与开关：

```kotlin
val config = DocumentMapperConfig.builder()
    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    .build()

config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)        // true
config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false) // true
config.deserializationFeatures // Set<DeserializationFeature>
```

- `enable(feature)` / `disable(feature)` / `configure(feature, enabled)` 均返回 Builder，
  可链式调用；
- 特性集合会随复制构造器、`JsonConfig` 等格式专属 `Config` 一起传递；
- `disable` 只是从显式开启集合中移除，特性默认关闭。

```kotlin
// 严格校验：文档中的 null 不允许静默变成 0/false
val strict = JsonMapper(
    DocumentMapperConfig.builder()
        .failOnNullForPrimitives(true)
        .build(),
)
strict.parse("""{"name":"a","age":null}""", User::class.java)
// -> DocumentSerializationException: Cannot assign null to primitive type int at $.age
```

## 类级 / 字段级注解覆盖

### @DocumentNaming：命名策略

`@DocumentNaming` 可标注在**类**或**字段/getter** 上，覆盖全局
`namingStrategy`；显式 `@DocumentName` 仍然最高优先级：

```text
@DocumentName
  > 字段级 @DocumentNaming
  > 类级 @DocumentNaming
  > 全局 DocumentMapperConfig.namingStrategy
```

```kotlin
import com.mingliqiye.document.annotation.DocumentNaming
import com.mingliqiye.document.annotation.DocumentNamingStrategy.SnakeCaseStrategy

@DocumentNaming(SnakeCaseStrategy)
class User {
    var userName: String = ""        // 写出 user_name

    @field:DocumentNaming(DocumentNamingStrategy.KebabCaseStrategy)
    var displayName: String = ""     // 写出 display-name

    @field:DocumentName("custom")    // 显式名最高优先级
    var explicit: String = ""
}
```

- 类级策略作用于该类型所有未显式命名的属性/构造参数/record 组件；
- 字段级策略覆盖类级；
- `@DocumentCreator` 构造参数未标注 `@DocumentName` 时，同样优先取参数上的
  `@DocumentNaming`，其次类级策略，最后全局 `namingStrategy`，保证读写字段名对称；
- Builder 模式：目标类上的类级 `@DocumentNaming` 会通过 Builder 的封闭类继承到
  Builder 属性上（Builder 需声明为目标类型的内部类）。

### @DocumentInclude：写出策略

`@DocumentInclude` 可标注在**类**或**字段/getter** 上，控制 null/空值是否写出；
反序列化不受影响：

```text
字段级 @DocumentInclude
  > 类级 @DocumentInclude
  > 全局 DocumentMapperConfig.writeNulls
```

| 策略 | 行为 |
|---|---|
| `ALWAYS` | 总是写出，即使值为 `null` |
| `NON_NULL` | `null` 不写出 |
| `NON_EMPTY` | `null`、空字符串、空集合、空 `Map`、空数组不写出 |
| `USE_DEFAULTS` | 回退到下一优先级，最终使用全局 `writeNulls` |

```kotlin
import com.mingliqiye.document.annotation.DocumentInclude
import com.mingliqiye.document.annotation.DocumentIncludePolicy
import com.mingliqiye.document.annotation.DocumentIncludePolicy.NON_EMPTY

@DocumentInclude(NON_EMPTY)
class Order {
    var id: String = ""                    // 空字符串不写出

    @field:DocumentInclude(DocumentIncludePolicy.ALWAYS)
    var remark: String? = null             // 总是写出 null

    @field:DocumentInclude(DocumentIncludePolicy.NON_NULL)
    var coupon: String? = null             // null 不写出
}
```

- 类级策略作用于该类型所有属性；字段级覆盖类级；
- 嵌套对象与集合元素分别按各自类型的类级/字段级策略处理；
- Node 路径（`toNode`）与流式写出路径（`format` / `format(value, outputStream)` 等）
  行为一致；
- `DocumentRuntimeCodecFactory` 生成的运行期转换器仍只使用其构造参数中的
  `writeNulls`，不读取该注解。

## DocumentModule 模块注册

`DocumentModule` 等价于 Jackson 的 `Module`，把一组转换器/策略打包注册：

```kotlin
import com.mingliqiye.document.serialization.DocumentModule
import com.mingliqiye.document.serialization.ModuleSetupContext

class MoneyModule : DocumentModule {
    override val moduleName: String = "money"

    override fun setupModule(context: ModuleSetupContext) {
        context.addConverter(MoneyConverter())       // 双向：序列化 + 反序列化
        context.addSerializer(MoneySerializer())     // 只序列化
        context.addDeserializer(MoneyDeserializer()) // 只反序列化
        context.setObjectInstantiator(...)           // 可选：实例化策略
        context.setPropertyMetadataResolver(...)     // 可选：属性元数据解析器
    }
}

val mapper = JsonMapper.builder()
    .addModule(MoneyModule())
    .addModules(OtherModule(), ThirdModule())
    .build()
```

三种注册方式分别进入独立的注册表，同一 `getType()` 原始类型为 key：

```kotlin
context.addConverter(converter)       // = addSerializer(converter) + addDeserializer(converter)
context.addSerializer(serializer)     // 只影响序列化
context.addDeserializer(deserializer) // 只影响反序列化
```

优先级/覆盖关系（`DocumentMapperBuilder.build()` 时合并）：

```text
字段级注解转换器（@DocumentSerializer / @DocumentDeserializer / @DocumentConverter）
  > 模块注册的序列化器/反序列化器
  > Builder 上直接 addSerializer/addDeserializer/addConverter 注册
  > 内置 Bean / 标量映射
```

- 模块按 `moduleName` 去重：同名模块后注册的实例覆盖先注册的实例（保持首次注册顺序）；
- 多个模块注册同一类型时，后注册模块覆盖先注册模块；
- 模块注册的 `ObjectInstantiator` / `PropertyMetadataResolver` 优先于 Builder 上
  `.objectInstantiator(...)` / `.propertyMetadataResolver(...)` 直接设置的实例；
- 直接注册也可用于非模块场景：

```kotlin
val mapper = JsonMapper.builder()
    .addConverter(MoneyConverter())
    .propertyMetadataResolver(resolver) // 2.5.1 新增：Builder 直接注册属性元数据解析器
    .build()
```

## 转换器

实现 `DocumentBaseSerializer` 与/或 `DocumentBaseDeserializer`，通过：

- `DocumentMapperBuilder.addSerializer(...)`
- `DocumentMapperBuilder.addDeserializer(...)`
- `@DocumentConverter`
- 字段级配置

注册。

## Bean Validation 集成

`serialization` 只定义扩展点 `DocumentBeanValidator`，不绑定具体校验框架；
使用 Jakarta Bean Validation 时配合 `validation` 模块的
`JakartaDocumentBeanValidator`：

```kotlin
val config = DocumentMapperConfig.builder()
    .beanValidator(JakartaDocumentBeanValidator.create()) // validation 模块
    .build()
val mapper = JsonMapper(config)
```

- 序列化根入口 `toNode` / `writeTo` 校验一次根对象
- 反序列化根出口 `fromNode` / `fromTokens` / `readFrom` 校验一次结果对象
- `format` / `parse` / 文件与流 API 经过上述根路径，同样生效
- 嵌套对象、生成式编解码器与值类转换器回调 Mapper 的嵌套调用不会重复校验
- 未配置 `beanValidator`（默认 `null`）时保持原有行为

校验失败的处理：

```text
DocumentBeanValidator 直接抛 DocumentSerializationException 时原样传播；
抛其他异常时 Mapper 包装为 DocumentSerializationException 并通过 cause 保留原异常。
```

实现 `DocumentBeanValidator` 即可接入任意校验框架：

```kotlin
val validator = DocumentBeanValidator { value ->
    if (value is Order && value.amount < 0) {
        throw DocumentSerializationException("amount must be >= 0")
    }
}
```

限制：

- 只对根对象校验一次；嵌套对象是否级联校验由校验器自身决定（Jakarta 用 `@Valid`）
- `writeTo` 与 `toNode` 都在写出前校验；`merge` 等不经过根出入口的 API 不自动校验

## 多态类型

```kotlin
@DocumentTypeInfo("kind")
interface Animal

@DocumentTypeName("cat")
class Cat : Animal

val config = DocumentMapperConfig.builder()
    .subtype(Animal::class.java, "cat", Cat::class.java)
    .build()

val mapper = JsonMapper(config)
```

字段声明为 `Animal` 时自动写入：

```json
{
  "kind": "cat"
}
```

反序列化按 `kind` 查找已注册子类；缺失或未知类型会抛 `DocumentSerializationException`。

多态只使用显式注册的 registry，不扫描子类。

## 构造器注入

不可变 Java Bean 可以用 `@DocumentCreator` 指定反序列化构造器：

```java
class User {
    private final String name;
    private final int age;

    @DocumentCreator
    User(@DocumentName("name") String name, @DocumentName("age") int age) {
        this.name = name;
        this.age = age;
    }
}
```

- 构造参数用 `@DocumentName` 指定文档字段名
- 没有注解时回退到反射参数名（需要 `-parameters`）
- 缺字段按 `MissingFieldPolicy` 处理
- 未标注构造器继续走原有无参构造逻辑

## Java record

record 组件访问器是 `name()` 而不是 `getName()`。引入可选的 `record-support` 模块后，
Mapper 会自动识别 record 组件：

```java
public record Address(String city, int zipCode) {}

public record User(
        @DocumentName("display_name") String name,
        int age,
        Address address) {}
```

```kotlin
// 依赖：serialization（或任一格式模块）+ record-support，运行在 JDK 17+
implementation("com.mingliqiye.document:record-support:2.5.1") // JDK 17+

val node = mapper.toNode(User("小于", 18, Address("杭州", 310000)))
// {"display_name":"小于","age":18,"address":{"city":"杭州","zipCode":310000}}

val user = mapper.fromNode(node, User::class.java)
```

- 序列化：record 组件访问器作为可读属性写出，支持 `@DocumentName`、`@DocumentIgnore`
  与命名策略
- 反序列化：优先使用 `@DocumentCreator` 构造器；没有标注时自动绑定 canonical
  constructor，按组件名（可用 `@DocumentName` 覆盖）取字段
- record 组件都是只读的，按构造参数注入；缺失字段按 `MissingFieldPolicy` 处理
- `@DocumentIgnore` 标记的组件既不写出，也不从文档读取
- 只读属性见下一节

record 反射访问独立在 `record-support` 模块（JDK 17 字节码）：它直接使用
`Class.isRecord()` / `Class.getRecordComponents()` / `RecordComponent`，并通过
`META-INF/services` 注册 `JavaRecordSupport` SPI 实现；`serialization` 以 JDK 8 目标编译，
运行期用 `ServiceLoader` 加载。

JDK 17+ 且 classpath / module path 上有 `record-support` 时自动启用 record 支持；
未引入该模块、或运行在 JDK 8-15 时加载器安全降级（`isRecord` 恒为 `false`、
`components` 恒为空），record 类型按普通 Bean 处理，不会抛异常。详见
[record-support](../record-support/README.md)。

## 只读属性

没有 setter、也不是可写构造参数的属性（Java `final` 字段、只有 getter 的属性）：

- 序列化照常写出
- 反序列化直接跳过：文档中出现该字段时不会被当作未知字段，即使开启
  `failOnUnknownFields` 也不报错
- `MissingFieldPolicy.FAIL` 不会因为缺少只读字段而报错
- record / `@DocumentCreator` 构造器 Bean 中，只读属性是构造参数，按参数注入

```java
public final class ReadOnlyUser {
    public final String tag = "fixed";   // 无 setter，反序列化跳过

    private final String name;

    @DocumentCreator
    public ReadOnlyUser(@DocumentName("name") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DocumentName("label")
    public String getLabel() {           // 只读属性：写出 label，读入时跳过
        return name.toUpperCase();
    }
}
```

## 合并已有对象

`merge(node, target)` 把文档树合并到已有对象：文档中出现的字段覆盖，未出现的字段
保留原值。

```kotlin
val user = mapper.parse(
    """{"name":"a","age":18,"address":{"city":"杭州","zipCode":310000}}""",
    User::class.java,
)

// 浅合并：address 整体替换为文档中的对象
mapper.merge(ObjectNode.of("age" to NumberNode.of(20)), user)
// user.age == 20；name、address 保留

// 深合并：嵌套 Bean 递归合并，保留文档中没有出现的嵌套字段
mapper.merge(
    ObjectNode.of("address" to ObjectNode.of("city" to StringNode.of("北京"))),
    user,
    MergePolicy.DEEP,
)
// user.address.city == "北京"，user.address.zipCode == 310000
```

`MergePolicy`：

```text
REPLACE           文档字段整体替换（默认）
DEEP              嵌套 Bean 递归合并，保留嵌套对象未出现字段
SKIP_NULLS        文档中的 null 不覆盖已有值
DEEP_SKIP_NULLS   深合并 + 忽略 null
MergePolicy.of(deepMerge, skipNulls)  自定义组合
```

语义与限制：

- 只支持 Node 到 Bean 的合并；流式 `writeTo` 没有合并 API
- 可变 Bean 就地修改并返回同一实例；record / `@DocumentCreator` / `@DocumentBuilder`
  Bean 返回新实例（原实例不变），通过“已有对象序列化结果 + 覆盖字段”重建
- 集合、Map、数组与多态声明字段始终整体替换，不做元素级合并
- 只读属性即使在文档中出现也会跳过
- 文档中的未知字段默认忽略，开启 `failOnUnknownFields` 时报错
- `MissingFieldPolicy` 不参与 merge：未出现的字段一律保留原值
- `node` 必须是对象节点；目标为 `null` 或非 Bean 类型时抛
  `DocumentSerializationException`

## Builder 模式

不可变类也可以通过 Builder 反序列化：

```java
class User {
    @DocumentBuilder
    static UserBuilder builder() {
        return new UserBuilder();
    }
}

class UserBuilder {
    private String name;

    UserBuilder setName(String name) {
        this.name = name;
        return this;
    }

    @DocumentBuild
    User build() {
        return new User(name);
    }
}
```

启用条件：

```text
没有 @DocumentCreator
没有无参构造器
目标类存在 @DocumentBuilder 静态工厂
Builder 存在 @DocumentBuild 或无参 build()
```

## 代码生成

### 运行期属性访问代码生成

```kotlin
val codegen = DocumentAccessorCodegen()
val getter = codegen.getter(User::class.java, "name")
val setter = codegen.setter(User::class.java, "name")
```

优先使用 LambdaMetafactory 生成直接调用代码，失败时回退反射。

### 运行期 Bean 编解码器生成

```kotlin
val factory = DocumentRuntimeCodecFactory(
    namingStrategy = PropertyNamingStrategy.SNAKE_CASE,
    writeNulls = false,
)
val mapper = JsonMapper.builder()
    .addConverter(factory.converter(User::class.java))
    .build()
```

生成器按字段元数据与 [DocumentAccessorCodegen] 访问器构建运行期转换器，支持
`@DocumentName`、`@DocumentIgnore` 与无参构造器 + setter。

### 编译期源码生成

```kotlin
val source = DocumentCodecSourceGenerator().enumConverter(
    Status::class.java,
    "com.example.generated",
)
```

生成的源码可直接写入源码目录，由编译器编译；也可以生成模块：

```kotlin
val module = DocumentCodecSourceGenerator().module(
    packageName = "com.example.generated",
    moduleName = "GeneratedDocumentModule",
    converterTypes = listOf("com.example.generated.StatusDocumentConverter"),
)
```

## 构建

```bash
# serialization 默认测试（JDK 8 工具链）
gradle :serialization:test

# Java record 映射测试：独立 JDK 17 模块 record-support
gradle :record-support:test

# 代码格式
gradle :serialization:ktlintFormat
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
'

流式写出：

```text
format(value, OutputStream) 会优先通过 DocumentTokenWriter 直接写出
当前实现：JSON、Ion 文本、XML、YAML、CSV、Properties、TOML、CBOR（默认模式）、Smile
其他格式或暂不支持的组合会回退到先构造 String/ByteArray
OutputStream/Writer 由调用方关闭；本库只 flush
```