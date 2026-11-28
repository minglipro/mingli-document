# serialization 模块

`serialization` 是库的核心映射模块：它定义 `DocumentMapper` / `DocumentMapperApi`，
负责 Java Bean、集合、Map、泛型与 `Node` 树之间的双向转换，并提供注解处理、命名策略、
多态、构造器/Builder 实例化、合并、`$ref`、Token 与流式读写、安全限制等能力。

- 坐标：`com.mingliqiye.document:serialization`
- 当前版本：`2.4.0`
- 包名：`com.mingliqiye.document.serialization`
- 目标字节码：JDK 8
- 公开依赖：`core`、`exception`、`annotation`、`com.mingliqiye.utils.aliasfor:alias-for:1.0.3`
- 测试依赖：`format-json`（仅测试用）

## 职责

| 能力 | 关键类型 |
|---|---|
| 统一 Mapper 抽象 | `DocumentMapperApi`、`DocumentMapper`、`BinaryDocumentMapper` |
| 构建器 / 配置 / 策略 | `DocumentMapperBuilder`、`DocumentMapperConfig(Builder)`、`PropertyNamingStrategy`、`MissingFieldPolicy`、`MergePolicy` |
| 转换器 / 模块 | `DocumentBaseConverter`、`DocumentBaseSerializer`、`DocumentBaseDeserializer`、`DocumentModule`、`ModuleSetupContext` |
| 字段元数据 / 扩展实例化 / 多态 / 引用 | `DocumentProperty`、`PropertyMetadataResolver`、`ObjectInstantiator`、`SubtypeRegistry`、`RemoteReferenceResolver` |
| 反序列化特性 | `DeserializationFeature`（`FAIL_ON_NULL_FOR_PRIMITIVES`） |
| Token / 流式 | `DocumentTokenType`、`DocumentToken`、`DocumentTokenReader/Writer`、`DocumentValueReader/Writer`、`NodeTokenReader` |
| 代码生成（运行期/源码） | `DocumentAccessorCodegen`、`DocumentRuntimeCodecFactory`、`DocumentCodecSourceGenerator` |
| 异常 | `DocumentSerializationException` |

## 依赖关系

```text
annotation ──api──┐
exception ──api───┼── api ── serialization ── api ── serialization-kotlin
core ───────api───┘                 ▲
alias-for ──api─────────────────────┘
```

- `format-*` 继承 `DocumentMapper` / `BinaryDocumentMapper`，通过构造器注入
  `parseText`/`writeText`（或 `parseBytes`/`writeBytes`），不接触映射逻辑；
- `serialization-kotlin` 通过 `DocumentModule` 注入实例化策略与属性元数据解析器；
  `serialization` 不依赖具体格式（唯一格式依赖在测试范围）。

## 三条数据路径

| 路径 | 流程 | 适用 |
|---|---|---|
| Node 路径 | `parseText/parseBytes → Node → fromNode`；反向 `toNode → writeText/writeBytes` | 所有格式；中间树可跨 Mapper 复用；节点限制与 `$ref` 解析在此生效 |
| Token 路径 | `DocumentTokenReader → fromTokens`；反向 `writeTo → 格式 Token 写出器` | 二进制格式直读 Bean；`parse(...)` 优先尝试，不可用时回退 Node |
| 流式写出 | `format(value, OutputStream)` 优先 `createStreamingTokenWriter`，否则先构造完整输出 | JSON、Ion 文本、CBOR（默认）、Smile（compact）走 Token；XML/YAML 直接覆写流式写出 |

- 格式模块通过 `protected` 钩子接入：`createTokenReader`、`createTextTokenReader`、
  `createStreamingTokenWriter`；
- `OutputStream`/`Writer` 由调用方关闭，库只 `flush`；详见 [Token 参考](../reference/token.md)。

## DocumentMapperApi

格式无关的公共接口，`DocumentMapper` 实现它：

| 方法 | 返回 | 说明 |
|---|---|---|
| `toNode(value: Any?)` | `Node` | 实体 / 集合 / Map / 数组 → 文档树 |
| `fromNode(node, Class<T>)` | `T` | 树 → 实体 |
| `fromNode(node, DocumentTypeReference<T>)` | `T` | 树 → 泛型实体 |
| `parse(source: String, Class/TypeRef)` | `T` | 文本 → 实体 |
| `parse(source: ByteArray, Class/TypeRef)` | `T` | 字节 → 实体；**默认实现抛不支持异常** |
| `format(value: Any?)` | `String` | 实体 → 文本 |
| `formatPretty(value: Any?)` | `String` | 实体 → 格式化文本 |
| `formatBytes(value: Any?)` | `ByteArray` | 实体 → 字节；**默认实现抛不支持异常** |
| `formatPretty(value, File/Path/OutputStream/Writer)` | `Unit` | 格式化文本写出 |
| `parse(file: File, Class/TypeRef)` | `T` | 文件 → 实体 |
| `parse(path: Path, Class/TypeRef)` | `T` | 路径 → 实体 |
| `parse(input: InputStream, Class/TypeRef)` | `T` | 流 → 实体（文本按配置字符集解码） |
| `parse(reader: Reader, Class/TypeRef)` | `T` | 字符流 → 实体（仅文本格式） |
| `format(value, File/Path/OutputStream/Writer)` | `Unit` | 实体 → 文件/流 |
| `convert(source: Any?, Class<T>)` | `T` | 任意对象/`Node` → 目标类型（先 `toNode` 再 `fromNode`） |
| `convert(source, DocumentTypeReference<T>)` | `T` | 泛型版 `convert` |
| `merge(node, target)` / `merge(node, target, policy)` | `T` | 文档树合并到已有对象；默认实现抛不支持 |
| `readTree(String/ByteArray/File/Path/InputStream/Reader)` | `Node` | 树 API；内部按 `parse(source, Any::class.java)` 取节点树，顶层 `null` 返回 `NullNode.INSTANCE` |
| `writeTree(node)` / `writeTreeAsBytes(node)` | `String` / `ByteArray` | 树写出；等价 `format` / `formatBytes` |
| `valueToTree(value)` / `treeToValue(node, Class/TypeRef)` | `Node` / `T` | 等价 `toNode` / `fromNode` |
| `readValue(source, Class/TypeRef)` | `T` | 六种来源的 `parse` 别名 |
| `writeValueAsString(value)` / `writeValueAsBytes(value)` | `String` / `ByteArray` | 等价 `format` / `formatBytes` |
| `updateValue(target, overrides)` | `T` | `merge(toNode(overrides), target)` |
| `convertValue(source, Class/TypeRef)` | `T` | 等价 `convert` |
| `readerFor(Class/TypeRef)` / `writer()` / `writerWithDefaultPrettyPrinter()` | `DocumentObjectReader<T>` / `DocumentObjectWriter` | 轻量 Reader / Writer 包装 |

Kotlin 的 reified 扩展（`parse<T>(String/ByteArray/File/Path/InputStream/Reader)`、
`fromNode<T>(node)`、`convert<T>(source)`、`readerFor<T>()`）位于
`serialization-kotlin` 模块的 `com.mingliqiye.document.serialization.kotlin` 包，
例如 `mapper.parse<List<User>>(text)`；非 reified 版本仍在 `DocumentMapperApi`。

## DocumentMapper 额外 API

`DocumentMapper` 是 `format-*` 的公共父类，构造器为 `protected`：

```kotlin
public abstract class DocumentMapper protected constructor(
    public val formatName: String,
    parseText: (String) -> Node,
    writeText: (Node, Boolean) -> String,
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
    parseBytes: ((ByteArray) -> Node)? = null,
    writeBytes: ((Node, Boolean) -> ByteArray)? = null,
)
```

| 成员 | 说明 |
|---|---|
| `formatName` | 格式名，例如 `json`、`yaml` |
| `fromNode(node, Type)` | 以反射 `Type` 为目标的反序列化 |
| `readFrom(reader: DocumentValueReader, TypeRef<T>)` | 直接从流式读取器反序列化，不建完整树 |
| `fromTokens(reader, Class/TypeRef/Type)` | 从 Token 流反序列化；会校验空流与尾随内容 |
| `writeTo(value, writer: DocumentValueWriter)` | 直接写出实体，不构建完整树；不做对象身份 `$ref` |
| `merge(node, target, policy)` | 完整合并实现 |
| `validateNodeLimits(node)`（`protected`） | 供格式层 Token 路径复用限制校验 |
| `usesTextFormat` / `prettyOutputEnabled`（`protected open`） | 文本语义 / 默认 pretty；二进制格式覆写 |
| `createTextTokenReader` / `createTokenReader` / `createStreamingTokenWriter` | 格式层接入 Token 的 `protected open` 钩子，默认 `null` |

`BinaryDocumentMapper` 是二进制格式基类：文本 `parse(String)` / `format` 抛出
`DocumentSerializationException`，字节 API 由构造器注入的 `parseBytes` / `writeBytes`
提供，`usesTextFormat = false`（因此不支持 `Reader`/`Writer` 重载）。

## DocumentMapperConfig

配置是不可变对象，通过 `DocumentMapperConfig.builder()` 构建：

```kotlin
val config = DocumentMapperConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.FAIL)
    .failOnUnknownFields(true)
    .writeNulls(false)
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .fileCharset(Charsets.UTF_8)
    .atomicFileWrite(true)
    .createParentDirectories(true)
    .maxInputSize(16L * 1024 * 1024)
    .maxStringLength(1_000_000)
    .maxNodeDepth(64)
    .maxNodeCount(100_000)
    .build()
```

| 配置项 | 类型 | 默认值 | 作用 |
|---|---|---|---|
| `missingFieldPolicy` | `MissingFieldPolicy` | `KEEP_DEFAULT` | 文档缺少 Bean 字段时的处理 |
| `failOnUnknownFields` | `Boolean` | `false` | 遇到未知字段是否抛异常 |
| `writeNulls` | `Boolean` | `true` | Bean 字段为 null 时是否写出 |
| `namingStrategy` | `PropertyNamingStrategy` | `AS_IS` | 未标注 `@DocumentName` 时的字段名转换 |
| `fileCharset` | `Charset` | UTF-8 | 文本文件/流读写字符集 |
| `atomicFileWrite` / `createParentDirectories` | `Boolean` | `false` | 原子写文件 / 自动创建父目录 |
| `maxInputSize` / `maxStringLength` / `maxNodeDepth` / `maxNodeCount` | `Long/Int` | `0`（不限） | 输入字节、字符串长度、树深度、节点数限制 |
| `resolveReferences` / `writeReferences` | `Boolean` | `false` | 输入解析 `$ref` / 输出对象身份 `$ref` |
| `remoteReferenceResolver` | `RemoteReferenceResolver?` | `null` | 远程 `$ref` 解析器；`null` 时远程引用报错 |
| `deserializationFeatures` | `Set<DeserializationFeature>` | 空集合 | 显式开启的反序列化特性（`FAIL_ON_NULL_FOR_PRIMITIVES` 等） |

### DocumentMapperConfigBuilder

| 方法 | 说明 |
|---|---|
| `field(declaringClass, name, converter)` | 为指定字段绑定转换器 |
| `subtype(baseClass, typeName, subtypeClass)` | 注册多态子类型 |
| `subtype(baseClass, typeName, subtypeClass, typeProperty)` | 注册并覆盖类型属性名 |
| `typeProperty(baseClass, typeProperty)` | 覆盖多态类型属性名 |
| `missingFieldPolicy(policy)` | 设置缺失字段策略 |
| `failOnUnknownFields(fail)` / `writeNulls(write)` | 严格模式 / null 写出 |
| `namingStrategy(strategy)` | 字段命名策略 |
| `fileCharset(charset)` / `atomicFileWrite(boolean)` / `createParentDirectories(boolean)` | 文件读写 |
| `maxInputSize(long)` / `maxStringLength(int)` / `maxNodeDepth(int)` / `maxNodeCount(long)` | 安全限制；负数抛 `IllegalArgumentException` |
| `resolveReferences(boolean)` / `writeReferences(boolean)` / `remoteReferenceResolver(resolver)` / `build()` | `$ref` / 生成不可变配置 |
| `enable(feature)` / `disable(feature)` / `configure(feature, enabled)` | 反序列化特性开关 |
| `failOnNullForPrimitives(fail)` | 便捷设置 `FAIL_ON_NULL_FOR_PRIMITIVES` |

`DocumentMapperConfig.empty()` 提供全默认配置；各格式的 `XxxConfig` 继承
`DocumentMapperConfig`，在拷贝构造中保留通用配置（含 `deserializationFeatures`）。

## DeserializationFeature

默认全部关闭（与 Jackson 默认一致）：

| 特性 | 默认 | 行为 |
|---|---|---|
| `FAIL_ON_NULL_FOR_PRIMITIVES` | 关闭 | 开启后 `null` → Java 基本类型（字段注入/构造参数/Builder/record/基本类型数组元素）抛 `DocumentSerializationException`；关闭时使用基本类型默认值 |

```kotlin
val strict = DocumentMapperConfig.builder()
    .failOnNullForPrimitives(true)
    .build()
strict.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES) // true
```

## 类级/字段级注解

`serialization` 识别两个覆盖型注解（定义在 `annotation` 模块）：

| 注解 | 覆盖对象 | 优先级 |
|---|---|---|
| `@DocumentNaming` | 字段名 | `@DocumentName` > 字段级 > 类级 > 全局 `namingStrategy` |
| `@DocumentInclude` | 写出策略 | 字段级 > 类级 > 全局 `writeNulls`（`ALWAYS` / `NON_NULL` / `NON_EMPTY` / `USE_DEFAULTS`） |

详见 [注解参考](../reference/annotations.md#documentnaming) 与
[Bean 序列化](../guide/serialization.md)。

## 策略枚举

### MissingFieldPolicy

| 值 | 行为 |
|---|---|
| `KEEP_DEFAULT` | 保留对象构造后的默认值（默认） |
| `SET_NULL` | 引用类型字段设为 `null`；基本类型保留默认值 |
| `SET_EMPTY` | `String` 设空串，集合/Map/数组设空集合，其他引用类型设 `null` |
| `FAIL` | 缺少可写字段时抛 `DocumentSerializationException` |

### PropertyNamingStrategy

`AS_IS`（默认）、`SNAKE_CASE`（`userName` → `user_name`）、`KEBAB_CASE`
（`user-name`）、`UPPER_CAMEL`（`UserName`）、`LOWER_CAMEL`（`userName`）。
单词边界支持驼峰、连续大写缩写末尾（`URLValue` → `URL` + `Value`）、数字后大写与
`_`/`-`/空格/`.`；显式 `@DocumentName` 始终优先。

### MergePolicy

`REPLACE`（默认）、`DEEP`（深合并）、`SKIP_NULLS`（忽略 null）、
`DEEP_SKIP_NULLS`、`MergePolicy.of(deepMerge, skipNulls)`。

## 类型支持

基本类型及包装类型、`String`、枚举、`BigDecimal`/`BigInteger`、数组、`Collection`、
`Map`；标准 Java Bean（getter/setter 或 public 字段 + 无参构造）；`@DocumentCreator`
构造器注入；`@DocumentBuilder`/`@DocumentBuild`；Java record（JDK 16+ 运行时）；只读属性；
显式注册的多态类型；Kotlin data class / value class / sealed（需 `serialization-kotlin`）。

## 功能详解

### 字段命名与注解

`DocumentProperty` 是字段元数据抽象；命名策略只作用于未标注 `@DocumentName` 的字段：

```kotlin
class User {
    var userName: String = ""       // SNAKE_CASE 下写出 user_name
    @field:DocumentName("id")
    var userId: Long = 0            // 显式注解优先，写出 id
}
```

### 多态

```kotlin
@DocumentTypeInfo("kind")
interface Animal

@DocumentTypeName("cat")
class Cat : Animal

val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .subtype(Animal::class.java, "cat", Cat::class.java)
        .build(),
)
mapper.format(Cat()) // {"kind":"cat"}
mapper.parse("""{"kind":"cat"}""", Animal::class.java)
```

只使用显式注册表（扫描子类用 `kotlinSealedSubtypes`）；注册名与注解名都作为反序列化别名。

### 构造器注入与 Builder

见 [annotation](./annotation.md) 的 `@DocumentCreator` / `@DocumentBuilder` /
`@DocumentBuild`。优先级（源码逻辑）：
`@DocumentCreator 构造器 > @DocumentBuilder Builder > 无参构造 + setter`。

### Java record

- 序列化：record 组件访问器作为可读属性写出；
- 反序列化：优先 `@DocumentCreator`，否则绑定 canonical constructor，按组件名取值；
- 运行在 JDK 8 时该分支不执行，普通 Bean 行为不变；测试由 `recordTest` 在 JDK 17 执行。

### 只读属性

序列化正常写出；反序列化跳过且不算未知字段，`MissingFieldPolicy.FAIL` 不因此报错；
record / `@DocumentCreator` Bean 中只读属性是构造参数，按参数注入。

### 合并

```kotlin
val user = mapper.parse(json, User::class.java)

mapper.merge(ObjectNode.of("age" to NumberNode.of(20)), user)
// 浅合并：age 覆盖，name/address 保留

mapper.merge(patch, user, MergePolicy.DEEP)
// 深合并：嵌套 Bean 递归合并
```

- 只支持 Node → Bean；可变 Bean 就地修改并返回同一实例，不可变 Bean 返回新实例；
- 集合、Map、数组、多态声明字段始终整体替换；
- `MissingFieldPolicy` 不参与 merge；`node` 必须是对象节点，否则抛
  `DocumentSerializationException`。

### `$ref`

输入（`resolveReferences = true`）：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder().resolveReferences(true).build(),
)
mapper.parse("""{"user":{"$ref":"#/users/0"}}""", Map::class.java)
```

- 支持 `#`、`#/...` JSON Pointer、`~0`/`~1` 转义；
- 结构性循环按目标结构展开一次，回边保留为 `$ref` 节点；纯别名环报错；
- 本地缺失引用报错；远程引用需 `remoteReferenceResolver`。

输出（`writeReferences = true`）：

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder().writeReferences(true).build(),
)
val shared = mapOf("v" to 1)
mapper.toNode(mapOf("a" to shared, "b" to shared))
// {"a":{"v":1},"b":{"$ref":"#/a"}}
```

- 只作用于 `toNode` / Node 路径；`writeTo` 流式路径不参与；
- 只追踪 Bean、Map、Collection、数组；标量/枚举/二进制不登记；
- 关闭时保持原有循环检测行为（循环引用抛异常）。

### 文件与流

文本格式文件/流默认 UTF-8（`fileCharset` 可改）；库自己打开/关闭文件，但调用方传入的
`InputStream`/`OutputStream`/`Reader`/`Writer` **不会被关闭**，库只 flush；二进制格式不支持
`Reader`/`Writer`。`atomicFileWrite`、`createParentDirectories`、`maxInputSize` 分别控制
原子写、父目录创建与输入长度上限。

### 安全限制

`NodeLimits` 在 `toNode`、`fromNode` 等入口校验 `maxNodeDepth`、`maxNodeCount`、
`maxStringLength`，超限或节点树成环时抛 `DocumentSerializationException`。另外序列化有
内置递归上限 `MAX_SERIALIZATION_DEPTH = 128`，超过抛
`DocumentSerializationException("Maximum serialization depth exceeded")`。

### 代码生成辅助（本模块内）

| 类型 | 作用 |
|---|---|
| `DocumentAccessorCodegen` | 用 `LambdaMetafactory` 生成 getter/setter，失败回退反射 |
| `DocumentRuntimeCodecFactory` | 运行期 Bean 转换器（支持 `@DocumentName`、`@DocumentIgnore`、命名策略、`writeNulls`；不支持构造器注入/Builder/多态） |
| `DocumentCodecSourceGenerator` | 生成枚举转换器与 `DocumentModule` 的 Kotlin 源码字符串 |

KSP 版本见 [codegen 模块](./codegen.md)。

## 模块与扩展点

```kotlin
val mapper = JsonMapper.builder()
    .addSerializer(MySerializer())
    .addDeserializer(MyDeserializer())
    .addConverter(MyConverter())
    .addModule(object : DocumentModule {
        override val moduleName = "my-module"
        override fun setupModule(context: ModuleSetupContext) {
            context.addConverter(MyConverter())
            context.setObjectInstantiator(MyInstantiator)
            context.setPropertyMetadataResolver(MyResolver)
        }
    })
    .config(DocumentMapperConfig.builder().build())
    .build()
```

- `addModule` / `addModules` 以 `moduleName` 去重：同名模块后者覆盖；模块注册项在
  Builder 直接注册之后合并，**模块注册覆盖直接注册**；后注册模块覆盖先注册模块；
- 模块可通过 `context.addConverter` / `addSerializer` / `addDeserializer` 注册三种
  转换器，分别进入双向/序列化/反序列化注册表；
- `ObjectInstantiator` 接管无无参构造器的类型；`PropertyMetadataResolver` 补充 Java
  反射不可见的属性注解；模块注册的实例优先于 Builder 上
  `.objectInstantiator(...)` / `.propertyMetadataResolver(...)` 直接设置；
- 转换器查找优先级：字段注解（单向 > 双向）> 模块/Builder 注册 > 内置映射。

## 示例：完整流程

```kotlin
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.PropertyNamingStrategy

class Address { var city: String = "" }

class User {
    @field:DocumentName("id")
    var userId: Long = 0
    var userName: String = ""
    var address: Address? = null
}

val mapper = JsonMapper.builder()
    .config(DocumentMapperConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build())
    .build()

val node = mapper.toNode(User().apply {
    userId = 1
    userName = "小于"
    address = Address().apply { city = "杭州" }
})

println(mapper.format(node))       // {"id":1,"user_name":"小于","address":{"city":"杭州"}}
println(mapper.formatPretty(node))
println(mapper.fromNode(node, User::class.java).userName) // 小于
```

> `format(node)` 直接输出 `Node`，说明 Mapper 的实体 API 与节点 API 可以混用。

## 限制

- `merge` 只支持 Node → Bean，流式 `writeTo` 没有合并 API；`writeReferences` 只作用于
  Node 路径，`writeTo` 仍按原行为展开并检测循环；
- 多态只使用显式注册表，不会反射扫描子类（Kotlin sealed 由 `kotlinSealedSubtypes` 展开）；
- 自定义转换器在 Mapper 内缓存实例，必须无状态；
- `namingStrategy` 不作用于 Kotlin data class 构造参数（见
  [serialization-kotlin](./serialization-kotlin.md)）；
- Java record 支持依赖 JDK 16+ 运行时反射 API，主代码仍按 JDK 8 编译；二进制 Mapper
  不支持 `Reader`/`Writer` 重载；内置序列化深度上限 128；
- `DocumentMapperApi` 默认的 `parse(ByteArray)` / `formatBytes` 抛
  `DocumentSerializationException`；配置期错误抛 `IllegalArgumentException`。

## 坐标与构建

```kotlin
implementation("com.mingliqiye.document:serialization:2.4.0")
implementation(platform("com.mingliqiye.document:mingli-document-bom:2.4.0")) // 或使用 BOM
```

```bash
gradle :serialization:test
gradle :serialization:recordTest     # Java 17 运行时
gradle :serialization:ktlintCheck
```

相关文档：[注解](./annotation.md) · [API](../reference/api.md) · [异常](../reference/exceptions.md) · [Token](../reference/token.md) · [serialization-kotlin](./serialization-kotlin.md)
