---
title: 常见问题
---

# 常见问题

> 汇总使用中最常遇到的问题、坑与答案。每条都对应源码中的真实行为，不确定时请以源码
> 与各模块 README 为准。

## 目录

- [基础与选型](#基础与选型)
- [Bean 映射](#bean-映射)
- [注解与命名](#注解与命名)
- [配置与限制](#配置与限制)
- [文件与流](#文件与流)
- [Kotlin 与代码生成](#kotlin-与代码生成)
- [各格式问题](#各格式问题)
- [安全与性能](#安全与性能)

## 基础与选型

### Q1：一共支持哪些格式？坐标怎么选？

11 种：JSON/JSONC、YAML、XML、Properties、TOML、CSV、Ion、CBOR、Smile、Avro、Protobuf。

格式模块坐标是 `com.mingliqiye.document.format:<name>`（如 `format:json`），
普通模块是 `com.mingliqiye.document:<name>`。完整清单见
[安装与依赖](./installation.md)。

### Q2：需要什么 JDK / Kotlin 版本？

- 运行：JDK 8+；
- Java record 映射：JDK 16+ 运行时；
- Kotlin data class：Kotlin 2.0（`serialization-kotlin` 用 Kotlin 2.3.0 编译）；
- 构建源码：Gradle 8.x；
- KSP：`2.3.0-1.0.21`。

### Q3：有没有 Spring Boot Starter？

没有。仓库里只有库模块（`core`/`serialization`/`format-*` 等），没有自动配置。
在 Spring 里把 `JsonMapper` 声明为 `@Bean` 单例即可：

```kotlin
@Configuration
class DocumentConfig {
    @Bean
    fun jsonMapper(): JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()
}
```

### Q4：和 Jackson / Gson 有什么本质区别？

- Jackson 的树是 JSON 专用；本库的 `Node` 树是**格式无关**的中间层；
- 本库没有「一个 ObjectMapper 读所有格式」，而是每种格式一个 Mapper；
- 多态需要显式注册，不扫描 classpath；
- 提供 Token 流、统一 IO、跨格式转换、格式专属安全限制。

详细对照见 [迁移指南](./migration.md)。

### Q5：Mapper 应该每次 new 吗？

不应该。Mapper 是只读对象，内部缓存元数据，构建一次全局复用即可；线程安全。

## Bean 映射

### Q6：为什么我的字段没有被序列化 / 反序列化？

最常见原因：**属性不可见**。本库通过 JavaBeans Introspector + `public` 字段识别属性：

- `private` 字段且没有 getter/setter -> 不识别；
- 只有 getter -> 可写出，读入时跳过（只读属性）；
- Kotlin data class -> 需要 `kotlinModule()`。

### Q7：Kotlin data class 报错「无法实例化」怎么办？

注册 Kotlin 模块：

```kotlin
val mapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()
```

否则没有无参构造器，Mapper 无法实例化。

### Q8：Java record 为什么没生效？

record 反射 API 需要 **JDK 16+ 运行时**。主代码仍按 JDK 8 编译没问题，但运行环境
低于 16 时会走普通 Bean 逻辑。

### Q9：如何忽略某个字段？

```kotlin
@field:DocumentIgnore
var internalId: Long = 0
```

Kotlin 字段注解建议加 `@field:` use-site target。

### Q10：如何修改字段名？

`@field:DocumentName("display_name")`，优先级高于命名策略与属性名。

### Q11：如何统一转 snake_case？

```kotlin
JsonMapper(
    DocumentMapperConfig.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .build(),
)
```

注意：Kotlin data class 构造参数始终按参数名匹配，`namingStrategy` 不作用于构造参数，
需要用 `@DocumentName`。

### Q12：类型是 `List<User>`，怎么反序列化？

Bean 属性里直接声明 `List<User>` 即可（泛型信息来自 getter/setter/字段）。
顶层泛型需要 `DocumentTypeReference`：

```kotlin
mapper.parse(json, DocumentTypeReference.listType(User::class.java))
```

### Q13：如何把部分字段更新到已有对象？

```kotlin
mapper.merge(node, target)                 // 浅合并
mapper.merge(node, target, MergePolicy.DEEP) // 深合并
```

注意 record / 构造器 Bean / Builder Bean 会返回**新实例**，别丢掉返回值。

## 注解与命名

### Q14：日期字段怎么格式化？

```kotlin
@field:DocumentDateTimeFormat("yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
var createdAt: LocalDateTime = LocalDateTime.now()
```

不标注时会按 Bean 处理，结果不可控。

### Q15：数字要千分位/百分比怎么办？

```kotlin
@field:DocumentNumberFormat("#,##0.00")
var amount: BigDecimal = BigDecimal.ZERO
```

写出为字符串，读取时按目标数字类型还原。

### Q16：枚举为什么解析失败？

普通映射要求文档字符串**完全等于**枚举 `name()`，未知值抛
`DocumentSerializationException`。如果需要「未知值返回 null」，使用
`@DocumentEnumCodec` 生成的枚举转换器（见 [代码生成](./codegen.md)）。

### Q17：`@DocumentTypeInfo` 加了，多态还是不行？

该注解只是声明类型属性名；**必须注册子类型**：

```kotlin
DocumentMapperConfig.builder()
    .subtype(Animal::class.java, "cat", Cat::class.java)
    .build()
```

Kotlin sealed 用 `kotlinSealedSubtypes(Shape::class)`。本库不扫描 classpath。

### Q18：字段名大小写不一致，能自动匹配吗？

不能。字段名必须精确匹配（或通过 `@DocumentName` / 命名策略映射）。
JSON 的重复 key 策略只处理同名字段。

## 配置与限制

### Q19：为什么我给 JSON 设了 `maxInputSize`，但注释/重复 key 配置没了？

因为 `JsonConfig` 与 `DocumentMapperConfig` 是**两种配置**，不能直接叠加：

- 传 `DocumentMapperConfig` -> 通用选项生效，JSON 专属取默认；
- 传 `JsonConfig` -> JSON 专属生效，构建器未暴露的通用选项取默认。

Cbor 例外（`CborConfig.builder().base(config)`）。详见
[配置](./configuration.md)。

### Q20：`maxInputSize` 单位是字节吗？为什么中文没拦住？

配置单位是字节，但 `parse(String)` / `Reader` 用**字符数**近似检查。
多字节字符会低估实际字节数；建议同时设置 `maxNodeDepth`/`maxNodeCount`。

### Q21：`maxNodeDepth` 和 `JsonConfig.maxDepth` 有什么区别？

- `JsonConfig.maxDepth`：JSON 词法嵌套（对象/数组层数，默认 512）；
- `maxNodeDepth`：解析后的 Node 树深度（根为 1，默认不限制）。

两者独立，可同时设置。其他格式没有词法深度配置，一般用 `maxNodeDepth`。

### Q22：为什么 `resolveReferences(true)` 没生效？

JSON 的 `parse(String)` 固定走 Token 快路径，不做 `$ref` 展开。需要显式走 Node 路径：

```kotlin
val node = json.parse(text, Node::class.java)
val bean = json.fromNode(node, User::class.java)
```

### Q23：循环引用怎么处理？

默认抛 `DocumentSerializationException`；开启 `writeReferences(true)` 后写出
`{"$ref":"#/..."}`。注意流式写出路径不支持引用写出。

### Q24：缺字段为什么没报错？

默认 `MissingFieldPolicy.KEEP_DEFAULT`（保留默认值）。要严格：

```kotlin
DocumentMapperConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.FAIL)
    .failOnUnknownFields(true) // 未知字段也报错
    .build()
```

### Q25：null 字段为什么被写出了？

默认 `writeNulls = true`。关闭：

```kotlin
DocumentMapperConfig.builder().writeNulls(false).build()
```

### Q26：`failOnUnknownFields(true)` 会把只读字段当未知字段吗？

不会。只读属性、`@DocumentIgnore` 属性都属于「已知字段」，不会报错。

## 文件与流

### Q27：调用 `format(user, outputStream)` 会把流关闭吗？

不会。调用方负责关闭，本库只 `flush()`。同样，传入的 `InputStream`/`Reader`/`Writer`
也不会被关闭。

### Q28：写文件如何保证原子性？

```kotlin
DocumentMapperConfig.builder()
    .atomicFileWrite(true)
    .createParentDirectories(true)
    .build()
```

临时文件 + `ATOMIC_MOVE`，不支持时回退普通替换。

### Q29：文件不存在时抛出什么异常？

JDK `IOException`（通常是 `NoSuchFileException`）原样抛出，不会被包装成库异常。
父目录不存在且未开启 `createParentDirectories` 时同样如此。

### Q30：大文件能流式解析吗？

读取侧目前都是**整段读入**（受 `maxInputSize` 保护）。写出侧支持部分格式的流式写出
（JSON、CBOR、Smile、Ion 的 Token writer；YAML/XML 直接写 Writer）。
处理超大文件请按记录拆分，或用 CBOR sequence / Smile 多消息流等格式级多值 API。

### Q31：哪些格式支持直接写 `OutputStream` 不构造中间字符串？

- Token 流式：JSON、Ion（文本）、CBOR（默认模式）、Smile（默认模式）；
- 直接 Writer：YAML、XML；
- 其余（Properties、TOML、CSV）当前回退为完整字符串。

以源码行为为准，详见 [文件与流](./io.md#流式写出覆盖范围)。

## Kotlin 与代码生成

### Q32：`kotlinModule()` 可以调用多次吗？

可以。同一个 Mapper 多次 `addModule(kotlinModule(...))` 会按模块名合并/去重，
value class 集合不同则模块名不同、可叠加。推荐一次性注册完。

### Q33：value class 反序列化报错？

value class 在 **boxed 位置**（根对象、`List`/`Map`/数组元素、可空原始类型）需要注册：

```kotlin
kotlinModule(UserId::class, Score::class)
```

直接作为 data class 构造参数时不需要。

### Q34：sealed class 序列化报「No subtype registered」？

没有注册子类型。用 `DocumentMapperConfig.builder().kotlinSealedSubtypes(Shape::class)`，
或手动 `subtype(...)`。未注册的叶子子类会直接报错。

### Q35：KSP 没生成代码？

检查：

- 是否应用了 `com.google.devtools.ksp` 插件；
- 是否 `ksp("com.mingliqiye.document:codegen")`；
- 枚举是否 public（顶层或嵌套）；
- KSP 版本是否 `2.3.0-1.0.21`（匹配 Kotlin 2.3.0）；
- 多个枚举的 `moduleName` 配置是否一致。

### Q36：`DocumentRuntimeCodecFactory` 和 KSP 有什么区别？

- KSP：编译期生成**枚举**转换器与模块；
- RuntimeCodecFactory：运行期给**简单可变 POJO** 生成 Bean 转换器
  （无参构造 + setter，不支持构造器注入/Builder/多态）。

## 各格式问题

### Q37：JSON 带注释也能解析？

`JsonMapper()` 默认 `allowComments = true`（JSONC 行为）。要严格 JSON：

```kotlin
JsonMapper(JsonConfig.builder().allowComments(false).build())
```

### Q38：JSON 重复 key 是怎么处理的？

默认 `LAST_WINS`（后者覆盖）。可选 `FIRST_WINS`、`FAIL`。

### Q39：YAML 里的 `!env` 这种 tag 报错？

默认走 SnakeYAML `SafeConstructor`，未知 tag 直接拒绝。
用 `YamlConfig.builder().allowCustomTags(true)` 开启（标量->StringNode，
序列->ArrayNode，映射->ObjectNode）。开启后写出仍不保留 tag。

### Q40：YAML 多文档怎么读写？

```kotlin
val nodes = mapper.parseAllToNodes(text)
val list = mapper.parseAll(text, User::class.java)
val out = mapper.formatAll(listOf(userA, userB))
```

### Q41：XML 根元素名怎么控制？

类上加 `@DocumentRootName("user")`，或配置 `XmlConfig.builder().rootName("user")`。
注解优先。

### Q42：XML 属性/命名空间怎么映射？

```kotlin
XmlConfig.builder()
    .attributeMode(true)                          // 属性写成 @id 字段
    .namespaceMode(XmlNamespaceMode.CLARK)        // {uri}local 名称
    .build()
```

Bean 侧用 `@DocumentName("@id")` 接收属性。

### Q43：CSV 能写嵌套对象吗？

默认不行。用 `CsvNestedMode.JSON`（嵌套值写成 JSON 文本单元格）或
`CsvNestedMode.COLUMNS`（点分列名）；也可以用 `CsvSchemaBuilder.objectField` /
`arrayField` 声明多级 Schema。CSV 根节点固定为数组。

### Q44：TOML 的日期时间怎么变成了字符串？

TOML 的日期时间在 Node 中映射为 ISO-8601 字符串（Node 没有专门的时间类型），
反序列化时用 Java 类型接收即可，需要特定格式再加 `@DocumentDateTimeFormat`。

### Q45：Protobuf 为什么必须先给 Descriptor？能复用 Mapper 吗？

Protobuf 的字段编号、类型、嵌套都来自 `Descriptor`，没有它无法编码/解码。
不同 Java 类只要对应同一个 message 就可以共享 Mapper：

```kotlin
val factory = ProtobufMapperFactory()
val mapper = factory.mapper(descriptor)
```

或者一个 Mapper 在调用时传 Descriptor：

```kotlin
mapper.formatBytes(value, descriptor)
mapper.parseToNode(bytes, descriptor)
```

### Q46：Avro 为什么必须先给 Schema？

Avro 是 schema-based 编码，wire 格式本身不带字段名（OCF 容器会内嵌 Schema）。
裸二进制读写必须由调用方提供 Schema；需要 schema evolution 时用 writer/reader 双 Schema。

### Q47：CBOR / Smile 怎么读写多个值？

用多值 API：

```kotlin
cbor.formatAll(listOf(a, b))
cbor.parseAll(bytes, User::class.java)

smile.formatAll(listOf(a, b))
smile.parseAllToNodes(bytes)
```

### Q48：二进制格式能 `parse(String)` 吗？

不能，抛 `DocumentSerializationException`。二进制格式只支持 `ByteArray`、文件、
`InputStream`/`OutputStream`。

### Q49：怎么把 Node 直接写成另一种格式？

```kotlin
val node = JsonMapper().parse(json, Node::class.java)
val yaml = YamlMapper().format(node)
val xml = XmlMapper().format(node)
```

不需要先转成 Bean。

### Q50：Node 是不可变的，如何修改？

重建受影响节点：

```kotlin
val updated = ObjectNode.from(node.asObject().asMap() + ("age" to NumberNode.of(19)))
```

没有 `put`/`set` API。

## 安全与性能

### Q51：Mapper 是线程安全的吗？

是。配置不可变，内部缓存用 `ConcurrentHashMap`；自定义转换器必须无状态。
不要跨线程修改同一个待序列化实体。

### Q52：性能怎么样？有基准数据吗？

JMH 基准在 `benchmarks` 模块：

```bash
gradle :benchmarks:jmh
```

具体数字请以本地运行结果为准（机器差异很大），本指南不引用可能过时的数值。

### Q53：如何让不可信输入更安全？

- 设置 `maxInputSize` + `maxNodeDepth`（+ `maxNodeCount`/`maxStringLength`）；
- JSON 用 `JsonConfig.maxDepth`，考虑 `DuplicateKeyPolicy.FAIL`；
- YAML 设置 `maxAliases`，保持 `allowCustomTags(false)`；
- 不要给不可信输入配置远程 `$ref` 解析器；
- 跑 `gradle :fuzz:test`。详见 [Fuzz 与安全](./fuzz-security.md)。

### Q54：`NaN` / `Infinity` 为什么被拒绝？

Node 的数字模型基于 `BigDecimal`，无法表示非有限浮点；`NumberNode` 与
CBOR/Smile/Avro/Protobuf/Smile 都会拒绝。

### Q55：能用在 Android 上吗？

官方未声明 Android 支持。核心 Bean 映射依赖 `java.beans.Introspector`
（Android 运行时通常不提供完整 `java.beans` 包），请在目标平台自测；
纯 Node 树 + 格式解析的用法理论上可用，但同样需要验证。

### Q56：如何校验 JSON 结构？

`format-json` 提供轻量 JSON Schema 校验：

```kotlin
val schema = JsonSchema.compile(schemaText)
schema.isValid(node)
schema.validate(node)
schema.validateOrThrow(node)
```

支持 draft-07 常用子集（type/enum/properties/items/组合/format/本地 `$ref`），
不支持远程 `$ref`、`dependentSchemas` 等高级关键字。

如果你的问题不在列表中，先看对应格式页 [/formats/](../formats/) 与模块页
[/modules/](../modules/)，再用仓库测试用例定位最小复现。
