---
title: 配置
---

# 配置

> `mingli-document` 的配置分两层：**通用配置** `DocumentMapperConfig` 与
> **格式专属配置**（`JsonConfig`、`YamlConfig`、`XmlConfig`、`CsvConfig`、
> `IonConfig`、`CborConfig`）。本页给出全部字段、默认值、常用组合与已知限制。

## 目录

- [配置在哪里生效](#配置在哪里生效)
- [DocumentMapperConfig 全字段](#documentmapperconfig-全字段)
- [Builder 方法](#builder-方法)
- [默认值一览](#默认值一览)
- [格式专属配置](#格式专属配置)
- [格式配置与通用配置的关系](#格式配置与通用配置的关系)
- [格式构建器覆盖范围](#格式构建器覆盖范围)
- [常用组合](#常用组合)
- [限制与引用](#限制与引用)
- [多态注册](#多态注册)
- [字段级转换器](#字段级转换器)
- [配置的不可变性与线程安全](#配置的不可变性与线程安全)
- [完整示例](#完整示例)

## 配置在哪里生效

```text
Mapper 构造          JsonMapper(config) / YamlMapper(config) ...
Mapper Builder       JsonMapper.builder().config(config).build()
格式专属 Config       JsonMapper(JsonConfig.builder()...build())
模块                  addModule(...)（kotlinModule、代码生成模块等）
类级/字段级注解       优先级高于配置（@DocumentName、@DocumentNaming、@DocumentInclude 等）
```

同一个配置对象可以传给多个 Mapper；配置构建完成后不可变。

## DocumentMapperConfig 全字段

`com.mingliqiye.document.serialization.DocumentMapperConfig`：

| 字段 | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `missingFieldPolicy` | `MissingFieldPolicy` | `KEEP_DEFAULT` | 文档缺少 Bean 属性时的策略 |
| `failOnUnknownFields` | `Boolean` | `false` | 文档出现未声明字段时是否抛异常 |
| `writeNulls` | `Boolean` | `true` | Bean 属性为 null 时是否写出 |
| `namingStrategy` | `PropertyNamingStrategy` | `AS_IS` | 未标注 `@DocumentName` 的字段名转换策略 |
| `fileCharset` | `Charset` | `UTF_8` | 文本文件/流/字节数组的字符集 |
| `atomicFileWrite` | `Boolean` | `false` | 写文件时使用临时文件 + 原子替换 |
| `createParentDirectories` | `Boolean` | `false` | 写文件时自动创建父目录 |
| `maxInputSize` | `Long` | `0`（不限制） | 单次输入最大字节数 |
| `maxStringLength` | `Int` | `0`（不限制） | Node 树单个字符串最大长度 |
| `maxNodeDepth` | `Int` | `0`（不限制） | Node 树最大深度 |
| `maxNodeCount` | `Long` | `0`（不限制） | Node 树最大节点数 |
| `resolveReferences` | `Boolean` | `false` | 反序列化前解析本地/远程 `$ref` |
| `writeReferences` | `Boolean` | `false` | `toNode` 时按对象身份写出 `$ref` |
| `remoteReferenceResolver` | `RemoteReferenceResolver?` | `null` | 远程 `$ref` 解析器；未配置时远程引用报错 |
| `deserializationFeatures` | `Set<DeserializationFeature>` | 空集合（全部关闭） | 显式开启的反序列化特性，如 `FAIL_ON_NULL_FOR_PRIMITIVES` |
| `fieldConverters`（内部） | `Map<FieldKey, Converter>` | 空 | 通过 `field(...)` 注册的字段级转换器 |
| `subtypeRegistries`（内部） | `Map<Class, SubtypeRegistry>` | 空 | 通过 `subtype(...)` 注册的多态绑定 |

`MissingFieldPolicy` 取值：

```text
KEEP_DEFAULT   保留对象构造后的默认值（默认）
SET_NULL       引用类型设为 null；基本类型保留默认值
SET_EMPTY      String 设空串，集合/Map/数组设空集合，其他引用类型设 null
FAIL           缺少可写字段时抛 DocumentSerializationException
```

`PropertyNamingStrategy` 取值：

```text
AS_IS（默认） SNAKE_CASE KEBAB_CASE UPPER_CAMEL LOWER_CAMEL
```

### 校验规则

`DocumentMapperConfigBuilder.build()` 时会做参数校验：

| 参数 | 校验 |
|---|---|
| `maxInputSize` / `maxStringLength` / `maxNodeDepth` / `maxNodeCount` | 必须 `>= 0`，否则 `IllegalArgumentException` |
| `subtype(...)` 的 `typeName` | 不能为空 |
| `subtype(...)` 的 `subtypeClass` | 必须可赋值给 `baseClass` |
| 同一基类的重复类型名 | 冲突时报错 |
| `typeProperty(...)` | 属性名不能为空，且该基类必须注册过子类型 |

### 写文件选项

```kotlin
val config = DocumentMapperConfig.builder()
    .atomicFileWrite(true)          // 临时文件 + 原子替换
    .createParentDirectories(true)  // 自动建父目录
    .fileCharset(Charsets.UTF_8)
    .build()
```

- 原子写实现：父目录创建临时文件 → 写入 → `ATOMIC_MOVE`（不支持时回退
  `REPLACE_EXISTING`）→ 清理临时文件；
- 父目录无法解析时抛 `DocumentSerializationException`；
- 文本格式用 `fileCharset` 编码，JSON 的字节 API 固定 UTF-8。

## Builder 方法

`DocumentMapperConfig.builder()` 返回 `DocumentMapperConfigBuilder`：

| 方法 | 参数 |
|---|---|
| `field(declaringClass, name, converter)` | 给指定字段绑定转换器 |
| `subtype(baseClass, typeName, subtypeClass)` | 注册多态子类型 |
| `subtype(baseClass, typeName, subtypeClass, typeProperty)` | 注册子类型并覆盖类型属性名 |
| `typeProperty(baseClass, typeProperty)` | 覆盖多态类型属性名 |
| `missingFieldPolicy(policy)` | 缺字段策略 |
| `failOnUnknownFields(fail)` | 未知字段是否报错 |
| `writeNulls(write)` | 是否写出 null |
| `namingStrategy(strategy)` | 命名策略 |
| `fileCharset(charset)` | 文本字符集 |
| `atomicFileWrite(atomic)` | 原子写 |
| `createParentDirectories(create)` | 自动建父目录 |
| `maxInputSize(max)` | 最大输入字节数 |
| `maxStringLength(max)` | 最大字符串长度 |
| `maxNodeDepth(max)` | 最大树深度 |
| `maxNodeCount(max)` | 最大节点数 |
| `resolveReferences(resolve)` | 解析 `$ref` |
| `writeReferences(write)` | 写出对象身份 `$ref` |
| `remoteReferenceResolver(resolver)` | 远程 `$ref` 解析器 |
| `enable(feature)` / `disable(feature)` | 开启 / 关闭反序列化特性 |
| `configure(feature, enabled)` | 按布尔值设置反序列化特性 |
| `failOnNullForPrimitives(fail)` | 便捷设置 `FAIL_ON_NULL_FOR_PRIMITIVES` |
| `build()` | 构建不可变配置 |

`DocumentMapperConfig.empty()` 返回全默认空配置。

## 默认值一览

| 行为 | 默认 |
|---|---|
| 缺字段 | `KEEP_DEFAULT` |
| 未知字段 | 忽略 |
| null 字段 | 写出 |
| 命名 | 原样 |
| 文件字符集 | UTF-8 |
| 原子写 / 建父目录 | 关闭 |
| 输入大小 / 深度 / 节点数 / 字符串长度 | 不限制 |
| `$ref` 解析 / 写出 | 关闭 |
| 远程 `$ref` | 禁用 |
| 反序列化特性 | 全部关闭（`null` → 基本类型使用默认值） |
| 多态 | 未注册 |
| JSON 注释 | 允许（`allowComments` 默认 true） |
| JSON 重复 key | `LAST_WINS` |
| JSON 最大嵌套深度 | 512（`maxDepth`） |
| JSON pretty | 关闭 |
| YAML 自定义 tag | 拒绝 |
| YAML `maxAliases` | 0（SnakeYAML 默认） |
| XML 属性映射 / 命名空间 | 关闭 / `PREFIX` |
| XML 混合内容 | `PRESERVE` |
| CSV 分隔符 / 表头 | `,` / 有表头 |
| CSV 嵌套 | 不支持 |
| CBOR tag 保真 / Ion 类型保真 | 关闭 |

## 格式专属配置

格式专属 Config 都**继承** `DocumentMapperConfig`，因此格式 Mapper 同时识别通用字段
与格式字段。

### JsonConfig

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper(
    JsonConfig.builder()
        .allowComments(true)
        .pretty(false)
        .duplicateKeyPolicy(DuplicateKeyPolicy.LAST_WINS)
        .maxDepth(512)
        .build(),
)
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `allowComments` | `true` | 允许 `//` 与 `/* */` 注释（JSONC） |
| `pretty` | `false` | `format()` 默认是否缩进 |
| `duplicateKeyPolicy` | `LAST_WINS` | `LAST_WINS` / `FIRST_WINS` / `FAIL` |
| `maxDepth` | `512` | 对象/数组统一计数的嵌套深度上限；必须 `> 0` |

`DuplicateKeyPolicy.FAIL` 在树、Token、Bean 三条解析路径都会抛
`JsonParseException`。

### YamlConfig

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper

val mapper = YamlMapper(
    YamlConfig.builder()
        .pretty(true)
        .maxAliases(50)
        .allowCustomTags(false)
        .build(),
)
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `pretty` | `false` | `format()` 默认输出块格式 YAML |
| `maxAliases` | `0` | 单文档非标量 alias 上限；0 = SnakeYAML 默认 |
| `allowCustomTags` | `false` | 是否允许未知自定义 tag（`!foo`、`!!foo`、`!<tag:...>`） |

开启 `allowCustomTags` 后按节点种类还原：标量 → `StringNode`，序列 → `ArrayNode`，
映射 → `ObjectNode`；构造阶段不会实例化任意 Java 类型。写出侧不保留自定义 tag。

### XmlConfig

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlMixedContentPolicy
import com.mingliqiye.document.format.xml.XmlNamespaceMode

val mapper = XmlMapper(
    XmlConfig.builder()
        .rootName("user")
        .attributeMode(true)
        .namespaceMode(XmlNamespaceMode.CLARK)
        .mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED)
        .build(),
)
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `rootName` | `"root"` | 写出时的根元素名；可被 `@DocumentRootName` 覆盖 |
| `attributePrefix` | `null` | 属性字段前缀；`null` 表示不启用属性映射 |
| `attributeMode`（派生） | `false` | 等价于 `attributePrefix != null` |
| `namespaceAware` | `false` | 是否命名空间感知 |
| `namespaceMode` | `PREFIX` | `PREFIX`（`prefix:local`）或 `CLARK`（`{uri}local`） |
| `textKey` | `"#text"` | 混合内容/标量属性包装的文本字段名 |
| `itemsKey` | `"#items"` | 带属性数组元素的数组字段名 |
| `contentKey` | `"#content"` | 有序混合内容数组字段名 |
| `mixedContentPolicy` | `PRESERVE` | `PRESERVE` / `PRESERVE_ORDERED` / `REJECT` |

Builder 还有 `attributePrefix(prefix)`、`namespaceAware(aware)`、`textKey/itemsKey/contentKey`。
`build()` 会校验三个 key 互不相同，且不能以属性前缀开头。

属性映射协议：

- 带属性的标量元素写成 `{ @属性字段..., "#text": 标量 }`；
- 带属性的数组元素写成 `{ @属性字段..., "#items": 数组 }`；
- Bean 侧用 `@DocumentName("@id")`、`@DocumentName("#text")` 接收属性/文本。

### CsvConfig

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvNestedMode

val mapper = CsvMapper(
    CsvConfig.builder()
        .delimiter(';')
        .header(true)
        .nestedMode(CsvNestedMode.JSON)
        .build(),
)
```

| 字段 | 默认 | 说明 |
|---|---|---|
| `delimiter` | `','` | 字段分隔符 |
| `header` | `true` | 是否读写表头；配置 Schema 时由 `CsvSchema.header` 优先 |
| `schema` | `null` | `CsvSchema`；非空时按 Schema 列顺序与类型读写 |
| `nestedMode` | `null` | `JSON` / `COLUMNS` / `null`（不支持嵌套） |

细节见 [CSV 格式](../formats/csv.md) 与 [modules/schema](../modules/schema.md)。

### IonConfig

| 字段 | 默认 | 说明 |
|---|---|---|
| `preserveTypes` | `false` | 保留 timestamp / symbol / clob / blob，包装为 `{"$ionType":..., "value":...}` |

开启后禁用 Token 快路径，统一走 Node 树以保真。

### CborConfig

| 字段 | 默认 | 说明 |
|---|---|---|
| `preserveTags` | `false` | 保留 CBOR tag，包装为 `{"$cborTag": <int>, "value": <Node>}` |

CborConfig 的 Builder 结构与其他格式不同，它**不重复包装通用选项**，而是复用现成配置：

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig

val base = DocumentMapperConfig.builder()
    .writeNulls(false)
    .maxNodeDepth(64)
    .maxInputSize(8L * 1024 * 1024)
    .build()

val mapper = CborMapper(
    CborConfig.builder()
        .base(base)
        .preserveTags(true)
        .build(),
)
```

**只有 CborConfig 提供这种通用配置复用入口**，其他格式见下一节。

## 格式配置与通用配置的关系

格式 Mapper 读取格式专属字段的方式是类型判断：

```kotlin
// JsonMapper 内部
private val jsonConfig: JsonConfig? = config as? JsonConfig
```

因此：

- 传 `JsonConfig` → 既能用 JSON 专属选项，也能用构建器里暴露的通用选项；
- 传普通 `DocumentMapperConfig` → 通用选项生效，JSON 专属选项取**默认值**
  （例如注释仍然允许、深度仍为 512）。

```kotlin
// 只用通用配置：JSON 专属选项全为默认
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .writeNulls(false)
        .maxInputSize(1024 * 1024)
        .build(),
)
```

::: warning 这是常见误区
“我设置了 `maxInputSize`，但 JSON 注释/重复 key 配置没了”不是 bug：
两种 Config 不能直接拼在一起。需要 JSON 专属选项时用 `JsonConfig`；
需要通用选项时用 `DocumentMapperConfig`；Cbor 例外，可用 `CborConfig.base(config)`。
:::

## 格式构建器覆盖范围

格式 ConfigBuilder 暴露的通用选项**并不一致**（以源码为准）：

| 通用选项 | 通用 Builder | Json | Yaml | Xml | Csv | Ion | Cbor |
|---|---|---|---|---|---|---|---|
| `field(...)` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌（用 base） |
| `missingFieldPolicy` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `failOnUnknownFields` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `writeNulls` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `namingStrategy` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `fileCharset` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `atomicFileWrite` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `createParentDirectories` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `maxInputSize` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `maxStringLength` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `maxNodeDepth` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `maxNodeCount` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `resolveReferences` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `writeReferences` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `remoteReferenceResolver` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| `subtype` / `typeProperty` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |

实用建议：

- 需要「格式专属选项 + 通用选项」时，优先检查该格式的构建器是否暴露；
  Ion 暴露最多，Cbor 用 `base(...)`，其余格式只能二选一；
- `maxStringLength` 目前只有通用 `DocumentMapperConfig` 暴露；
- 只使用某格式的默认专属选项时，直接传 `DocumentMapperConfig` 是最灵活的方式。

## 常用组合

### 严格模式（不可信输入）

```kotlin
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.MissingFieldPolicy

val strict = JsonMapper(
    JsonConfig.builder()
        .allowComments(false)
        .duplicateKeyPolicy(com.mingliqiye.document.format.json.DuplicateKeyPolicy.FAIL)
        .maxDepth(64)
        .missingFieldPolicy(MissingFieldPolicy.FAIL)
        .failOnUnknownFields(true)
        .writeNulls(false)
        .build(),
)
```

### 数据库/配置风格字段名

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.format.yaml.YamlMapper

val mapper = YamlMapper(
    DocumentMapperConfig.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .build(),
)
```

### 生产配置文件读写

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .atomicFileWrite(true)
        .createParentDirectories(true)
        .maxInputSize(4L * 1024 * 1024)
        .maxNodeDepth(64)
        .maxNodeCount(100_000)
        .maxStringLength(1_000_000)
        .build(),
)
```

### 多态 + Kotlin 模块

```kotlin
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

val config = JsonConfig.builder()
    .subtype(Animal::class.java, "cat", Cat::class.java)
    .subtype(Animal::class.java, "dog", Dog::class.java)
    .typeProperty(Animal::class.java, "kind")
    .pretty(false)
    .build()

val mapper = JsonMapper.builder()
    .config(config)
    .addModule(kotlinModule())
    .build()
```

### `$ref` 解析

```kotlin
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .resolveReferences(true)
        .writeReferences(true)
        .build(),
)
```

## 限制与引用

### maxInputSize

「单次输入最大字节数」：

- `parse(String)` 用字符串长度检查（字符数）；
- `parse(ByteArray)` / 文件 / 流用字节数；
- `0` 表示不限制；
- 超限抛 `DocumentSerializationException: Input size N exceeds maxInputSize M`。

### maxNodeDepth / maxNodeCount / maxStringLength

只在 **Node 路径**校验（`toNode` / `fromNode` / `merge`，以及 YAML/XML/TOML/CSV 等
回退到 Node 的 Token 路径）：

- `maxNodeDepth`：根为 1，逐层 +1；
- `maxNodeCount`：遍历到的节点总数（对象 key 字符串也参与字符串长度检查）；
- `maxStringLength`：单个 `StringNode` 值与对象字段名长度上限；
- 循环引用会被检测并抛「Node tree contains a cycle」。

::: tip 与 JSON `maxDepth` 的区别
`JsonConfig.maxDepth` 限制 JSON **语法嵌套**（对象/数组层数，默认 512）；
`maxNodeDepth` 限制解析后的 **Node 树**。二者独立，可同时设置。
:::

### resolveReferences

开启后，反序列化前把 `$ref` 展开成真实结构：

- 支持本地 JSON Pointer：`#`、`#/a/b`、`#/items/0`，含 `~0`/`~1` 转义；
- 支持远程引用：`other.json#/x`，但必须先配置 `remoteReferenceResolver`；
- 结构性循环（引用目标在当前解析链上）：展开一次，环上的回边保留为 `$ref` 节点
  （Node 树是不可变树，无法表达真正的环）；
- 纯别名环（`$ref` 指向的仍是 `$ref` 对象）与缺失引用会抛
  `DocumentSerializationException`。

```kotlin
import com.mingliqiye.document.serialization.RemoteReferenceResolver

val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .resolveReferences(true)
        .remoteReferenceResolver(RemoteReferenceResolver { uri ->
            loadDocument(uri) // 返回外部文档 Node，URI 校验/IO/缓存由调用方负责
        })
        .build(),
)
```

`RemoteReferenceResolver` 是 `fun interface`：`resolve(uri: String): Node`，
入参是**不含 fragment 的 URI**。远程解析默认禁用，未配置时远程 `$ref` 直接报错。

### writeReferences

`toNode` 时按**对象身份**（而不是 `equals`）写出 `$ref`：同一对象只在首次出现处展开，
再次出现写成 `{"$ref":"#/首次路径"}`，循环引用也会变成引用而不再抛异常。

```kotlin
data class Node2(val name: String, var next: Node2?)

val a = Node2("a", null)
a.next = a

val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .writeReferences(true)
        .build(),
)

mapper.format(a) // 形如 {"name":"a","next":{"$ref":"#"}}，引用指向根节点
```

限制：`toNode` / `format`（内部走 `toNode`）支持引用写出；`format(value, OutputStream)`
与 `formatBytes` 的 Token 流式路径不参与 `$ref` 写出。

## 多态注册

```kotlin
val config = DocumentMapperConfig.builder()
    .subtype(Animal::class.java, "cat", Cat::class.java)
    .subtype(Animal::class.java, "dog", Dog::class.java)
    .typeProperty(Animal::class.java, "kind")
    .build()
```

校验点（`build()` 时）：

- 类型名不能为空；
- 子类必须可赋值给基类；
- 同一基类下类型名不能重复（显式注解名与注册名都会占用别名）；
- `typeProperty` 只能给注册过子类型的基类设置；
- 基类上的 `@DocumentTypeInfo.property` 不能为空白。

Kotlin sealed 层级可用 `kotlinSealedSubtypes(...)` 批量注册，见
[Bean 序列化](./serialization.md)。

## 字段级转换器

```kotlin
val config = DocumentMapperConfig.builder()
    .field(User::class.java, "name", MyNameConverter())
    .build()
```

`field(declaringClass, name, converter)` 把转换器绑定到「声明类 + Java 属性名」。
优先级低于字段注解、高于全局注册的转换器。

## 反序列化特性（DeserializationFeature）

参考实现 风格的特性开关，默认全部关闭（与 参考实现 默认一致）：

| 特性 | 默认 | 行为 |
|---|---|---|
| `FAIL_ON_NULL_FOR_PRIMITIVES` | 关闭 | 开启后 `null` → Java 基本类型抛 `DocumentSerializationException`（含路径与类型）；关闭时使用基本类型默认值 |

```kotlin
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentMapperConfig

val strict = DocumentMapperConfig.builder()
    .failOnNullForPrimitives(true)                              // 便捷方法
    // 等价：
    // .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    // .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    .build()

val config = DocumentMapperConfig.builder()
    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .build()

config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)        // false
config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)  // true
```

- 覆盖 `fromNode` / `fromTokens` / `parse` 与文件、流入口；
- 覆盖字段注入、`@DocumentCreator` 构造参数、Builder 属性、Java record 组件、
  基本类型数组元素；
- 特性集合随复制构造和格式专属 `Config`（`JsonConfig` 等）一起传递；
- 默认关闭，因此升级不改变既有行为。

## 类级 / 字段级命名（@DocumentNaming）

```kotlin
import com.mingliqiye.document.annotation.DocumentNaming
import com.mingliqiye.document.annotation.DocumentNamingStrategy.KebabCaseStrategy
import com.mingliqiye.document.annotation.DocumentNamingStrategy.SnakeCaseStrategy

@DocumentNaming(SnakeCaseStrategy)
class User {
    var userName: String = ""       // user_name

    @field:DocumentNaming(KebabCaseStrategy)
    var displayName: String = ""    // display-name

    @field:DocumentName("custom")   // 最高优先级
    var explicit: String = ""
}
```

优先级（从高到低）：

```text
@DocumentName > 字段级 @DocumentNaming > 类级 @DocumentNaming
  > 全局 namingStrategy
```

`DocumentNamingStrategy` 取值：`AsIsStrategy`、`SnakeCaseStrategy`、`KebabCaseStrategy`、
`UpperCamelStrategy`、`LowerCamelStrategy`。

## 类级 / 字段级写出（@DocumentInclude）

```kotlin
import com.mingliqiye.document.annotation.DocumentInclude
import com.mingliqiye.document.annotation.DocumentIncludePolicy
import com.mingliqiye.document.annotation.DocumentIncludePolicy.NON_EMPTY

@DocumentInclude(NON_EMPTY)
class Order {
    var id: String = ""

    @field:DocumentInclude(DocumentIncludePolicy.ALWAYS)
    var remark: String? = null
}
```

| 策略 | 行为 |
|---|---|
| `ALWAYS` | 总是写出（包括 `null`） |
| `NON_NULL` | `null` 不写出 |
| `NON_EMPTY` | `null`/空字符串/空集合/空 `Map`/空数组不写出 |
| `USE_DEFAULTS` | 回退到下一优先级，最终使用全局 `writeNulls` |

优先级（从高到低）：

```text
字段级 @DocumentInclude > 类级 @DocumentInclude > 全局 writeNulls
```

- 只影响序列化，反序列化不受影响；
- 嵌套对象与集合元素分别按各自类型的策略处理；
- `toNode` 与流式写出（`format` 等）行为一致。

## 配置的不可变性与线程安全

- `DocumentMapperConfig` 与各格式 Config 构建后不可变；
- 同一配置可安全地传给多个 Mapper / 多线程复用；
- Builder 是可变对象，**不要**在多线程间共享同一个 Builder；
- 配置对象里保存的转换器实例必须无状态（会被缓存复用）。

## 完整示例

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.kotlin.kotlinModule

// 1) 通用 + JSON 专属不能直接混合，因此把两类需求拆开：
//    - 需要严格校验与命名策略时，用通用配置（JSON 专属取默认）
//    - 需要 JSONC/重复 key/深度时，用 JsonConfig
val reader = JsonMapper(
    DocumentMapperConfig.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .missingFieldPolicy(MissingFieldPolicy.FAIL)
        .failOnUnknownFields(true)
        .writeNulls(false)
        .maxInputSize(1024 * 1024)
        .maxNodeDepth(64)
        .build(),
)

val jsoncReader = JsonMapper(
    JsonConfig.builder()
        .allowComments(true)
        .duplicateKeyPolicy(DuplicateKeyPolicy.FAIL)
        .maxDepth(64)
        .build(),
)

// 2) 带 Kotlin 模块的 Mapper：Builder 上同时给 config 与 module
val mapper = JsonMapper.builder()
    .config(
        JsonConfig.builder()
            .allowComments(false)
            .pretty(true)
            .build(),
    )
    .addModule(kotlinModule())
    .build()

println(mapper.formatPretty(mapOf("ok" to true)))
```

下一步：[Token 流式 API](./token-stream.md) 与
[Fuzz 与安全](./fuzz-security.md)。
