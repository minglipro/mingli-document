---
title: CBOR
description: format-cbor 模块：CborMapper、参考实现 CBOR 配置特性、canonical 编码、tag 保真与 CBOR sequence 多值读写
---

# CBOR

`format-cbor` 提供 CBOR（RFC 8949）二进制读写，核心类型是 `CborMapper`。

CBOR 是纯二进制格式，Mapper 只实现字节 API：`parse(String)` / `format(...)` 等文本入口会抛 `DocumentSerializationException`。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:cbor:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `cbor` |
| 版本 | `2.5.1` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `第三方底层库坐标.dataformat:CBOR 底层库:2.20.0` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.cbor` |

## 特性

- **CBOR 二进制**：map / array / 文本字符串 / 字节字符串 / 整数 / 浮点 / 十进制分数 / null；
- **对齐 参考实现 CBOR 配置**：完整提供 `CBORParser.Feature` / `CBORGenerator.Feature`（参考实现 2.20+）
  与 `STRICT_DUPLICATE_DETECTION`，默认值对齐 参考实现 2.x；
- **`BinaryNode` ↔ CBOR byte string**：字节串与文本串严格区分，Java `byte[]` 可无损往返；
- **Token 直读/直写**：默认路径直接从 CBOR 事件解码 Bean / 直接写 Token 流；
- **整数最短编码**：能放进 `Int` / `Long` 时用最短形式，更大的整数写成 bignum（tag 2/3）；
- **十进制精度**：小数写成 CBOR 十进制分数（tag 4），保留 `BigDecimal` 精度；
- **canonical 编码**：对象 key 按 UTF-8 长度升序、同长度按字节序排序；数组保持原顺序；
- **可选 tag 保真**：`CborConfig.preserveTags` 保留 CBOR tag；
- **CBOR sequence**：读写多个自描述数据项（RFC 8742）；
- **深度限制**：`maxNodeDepth` / `CborReader(maxDepth)` / `maxItems`；
- **文件/流读写**：二进制语义，`OutputStream` 可直接流式写。

## 配置：`CborConfig`

`CborConfig` 继承 `DocumentMapperConfig`，在通用配置上增加 CBOR 专属选项。

### 专属字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `preserveTags` | `Boolean` | `false` | 是否保留 CBOR tag（包装对象协议，见 [Tag 保真](#tag-保真)） |
| `bufferPool` | `ByteArrayPool?` | `null` | 可选输出缓冲区池；`null` 表示不池化 |
| `readFeatures` | `Set<CborReadFeature>` | `DEFAULT_READ_FEATURES`（空集合） | CBOR 读取特性 |
| `writeFeatures` | `Set<CborWriteFeature>` | `DEFAULT_WRITE_FEATURES`（`WRITE_MINIMAL_INTS`） | CBOR 写出特性 |

查询特性：`config.isEnabled(feature)`；同时提供便捷属性：
`standardNegativeBigIntDecoding`、`undefinedAsEmbeddedObject`、`simpleValueAsEmbeddedObject`、
`strictDuplicateDetection`、`writeMinimalInts`、`writeTypeHeader`、`lenientUtfEncoding`、
`stringReferences`、`writeMinimalDoubles`、`standardNegativeBigIntEncoding`、
`writeStrictDuplicateDetection`。

常量：

| 常量 | 值 | 含义 |
|---|---|---|
| `CborConfig.TAG_FIELD` | `"$cborTag"` | tag 包装对象中保存 tag 整数的字段名 |
| `CborConfig.VALUE_FIELD` | `"value"` | tag 包装对象中保存实际值的字段名 |
| `CborConfig.UNDEFINED_FIELD` | `"$cborUndefined"` | `undefined` 标记对象的字段名 |
| `CborConfig.SIMPLE_VALUE_FIELD` | `"$cborSimpleValue"` | CBOR simple value 标记对象的字段名 |

### 参考实现 CBOR 读取特性（`CborReadFeature`）

对齐 参考实现 `CBORParser.Feature`（参考实现 2.20+；参考实现 3.x 中更名为 `CBORReadFeature`），
并补充通用 `JsonParser.Feature.STRICT_DUPLICATE_DETECTION`。默认值对齐 参考实现 2.x。

| 特性 | 默认（2.x） | 默认（3.x） | 行为 |
|---|---|---|---|
| `DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING` | 关闭 | 开启 | tag 3 负数 bignum 按 RFC 8949 解码；关闭时保持 参考实现 ≤2.19 历史解码 |
| `READ_UNDEFINED_AS_EMBEDDED_OBJECT` | 关闭 | 开启 | `undefined` 读成标记对象 `{"$cborUndefined": true}`；关闭时读成 `NullNode` |
| `READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT` | 关闭 | 开启 | simple value 读成标记对象 `{"$cborSimpleValue": <整数>}`；关闭时读成数字 |
| `STRICT_DUPLICATE_DETECTION` | 关闭 | 关闭 | map 重复 key 抛 `CborParseException`；关闭时后者覆盖前者 |

### 参考实现 CBOR 写出特性（`CborWriteFeature`）

对齐 参考实现 `CBORGenerator.Feature`（参考实现 2.20+；参考实现 3.x 中更名为 `CBORWriteFeature`），
并补充通用 `JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION`。默认值对齐 参考实现 2.x。

| 特性 | 默认（2.x） | 默认（3.x） | 行为 |
|---|---|---|---|
| `WRITE_MINIMAL_INTS` | 开启 | 开启 | 整数最短编码；关闭时 int 范围写 4 字节、long 范围写 8 字节，更大的整数仍写 bignum |
| `WRITE_TYPE_HEADER` | 关闭 | 关闭 | 文档开头写自描述 tag（`d9d9f7`），sequence 只写一次 |
| `LENIENT_UTF_ENCODING` | 关闭 | 关闭 | 非法代理对写 `U+FFFD`；关闭时抛 `CborException` |
| `STRINGREF` | 关闭 | 关闭 | 生成 stringref 共享字符串引用（命名空间 tag 256、引用 tag 25） |
| `WRITE_MINIMAL_DOUBLES` | 关闭 | 关闭 | Token 直写路径的 `double` 尽量写 4 字节 float；关闭时沿用十进制分数（tag 4）。**模块差异**：参考实现 默认写 8 字节 double |
| `ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING` | 关闭 | 开启 | tag 3 负数 bignum 按 RFC 8949 编码；关闭时保持 参考实现 ≤2.19 历史编码 |
| `STRICT_DUPLICATE_DETECTION` | 关闭 | 关闭 | Token 写出重复字段名抛 `CborException`；Bean/Map/Node 序列化不会产生重复字段名 |

参考实现 3.x 默认开启三个读取特性与 `ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING`，
需要 3.x 行为时显式 `enable(...)`；本模块默认保持 参考实现 2.x 行为以兼容既有字节输出。

### Builder 方法

`CborConfig.builder()` 返回 `CborConfigBuilder`：

| 方法 | 说明 |
|---|---|
| `preserveTags(Boolean)` | 是否保留 CBOR tag |
| `bufferPool(ByteArrayPool?)` | 输出缓冲区池 |
| `readFeatures(Set<CborReadFeature>)` / `writeFeatures(Set<CborWriteFeature>)` | 整体设置特性集合 |
| `enable(CborReadFeature)` / `disable(CborReadFeature)` / `configure(CborReadFeature, Boolean)` | 读取特性开关 |
| `enable(CborWriteFeature)` / `disable(CborWriteFeature)` / `configure(CborWriteFeature, Boolean)` | 写出特性开关 |
| `field(Class, String, DocumentBaseConverter)` | 为字段绑定转换器 |
| `missingFieldPolicy(MissingFieldPolicy)` | 缺字段策略 |
| `failOnUnknownFields(Boolean)` | 未知字段是否报错 |
| `writeNulls(Boolean)` | null 字段是否写出 |
| `maxNodeDepth(Int)` | Node 树最大深度（0 表示不限制） |
| `subtype(base, typeName, subClass)` / `subtype(base, typeName, subClass, typeProperty)` | 注册多态子类型 |
| `typeProperty(base, typeProperty)` | 覆盖多态类型属性名 |
| `resolveReferences(Boolean)` / `writeReferences(Boolean)` | 本地 `$ref` 解析/写出 |
| `base(DocumentMapperConfig)` | 直接复用完整通用配置；与上述通用便捷方法互斥 |
| `build()` | 构建 `CborConfig` |

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.cbor.CborReadFeature
import com.mingliqiye.document.format.cbor.CborWriteFeature
import com.mingliqiye.document.serialization.DocumentMapperConfig

val mapper = CborMapper(
    CborConfig.builder()
        .enable(CborReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
        .disable(CborWriteFeature.WRITE_MINIMAL_INTS)
        .enable(CborWriteFeature.WRITE_TYPE_HEADER)
        .writeNulls(false)
        .maxNodeDepth(64)
        .build(),
)
```

> 注意：
> - 开启 `READ_UNDEFINED_AS_EMBEDDED_OBJECT` 或 `READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT` 时，
>   `parse(ByteArray)` 的 Token 快路径会被禁用（改用 Node 路径表示标记对象）；
> - `WRITE_MINIMAL_DOUBLES` 只在 Token 直写路径（Bean 的 `Double` 字段）生效，Node 数字仍走
>   整数/十进制分数策略；
> - 需要 `namingStrategy`、`fileCharset`、`atomicFileWrite` 等其它通用选项时，用
>   `base(DocumentMapperConfig)` 传入完整通用配置。

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `CborMapper()` | 默认：不带 tag 保真、Token 快路径 |
| `CborMapper(config: DocumentMapperConfig)` | 通用配置 |
| `CborMapper(config: CborConfig)` | CBOR 配置，可开启 tag 保真 |

`CborMapper.builder()` 返回 `CborMapperBuilder`，用法与其他格式一致（`addSerializer` / `addDeserializer` / `addConverter` / `addModule` / `addModules` / `config` / `objectInstantiator` / `build`）。

## 读取 API

### 二进制入口

| 方法 | 说明 |
|---|---|
| `parse(source: ByteArray, targetType)` | 单值解析；默认路径用内部 `CborTokenReader` 直读 Bean；`preserveTags=true` 或 `maxNodeDepth>0` 时回退 `CborReader` |
| `parse(file: File, targetType)` / `parse(path: Path, targetType)` | 二进制文件 → 实体 |
| `parse(input: InputStream, targetType)` | 二进制流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 Token 读取器 → 实体 |

### 不支持的文本入口

| 方法 | 行为 |
|---|---|
| `parse(source: String, targetType)` | 抛 `DocumentSerializationException("cbor mapper only supports binary input")` |
| `parse(reader: Reader, targetType)` | 抛 `DocumentSerializationException("... does not support character streams")` |

### 独立读取器

| 类/函数 | 说明 |
|---|---|
| `CborReader(source, maxDepth = 0, preserveTags = false, readFeatures = DEFAULT_READ_FEATURES)` | 单值读取器，`read(): Node` |
| `CborReader(source, ...)` 的 `read()` | 单值入口要求输入恰好一个顶层数据项 |
| `CborSequenceReader(source, maxDepth = 0, maxItems = 0, preserveTags = false, readFeatures = DEFAULT_READ_FEATURES)` | sequence 读取器，`readAll(): List<Node>` |
| `CborSequenceReader.parseAll(source, maxDepth, maxItems, preserveTags, readFeatures)` | 静态多值解析 |
| `ByteArray.toCborNodes(readFeatures = DEFAULT_READ_FEATURES)` | 扩展函数：sequence 字节 → `List<Node>` |

```kotlin
import com.mingliqiye.document.format.cbor.CborReader

val node = CborReader(mapper.formatBytes(user)).read()
```

读取类型映射：

| 类型 | Node |
|---|---|
| `null` | `NullNode` |
| `undefined` | `NullNode`；开启 `READ_UNDEFINED_AS_EMBEDDED_OBJECT` 时为 `{"$cborUndefined": true}` |
| `false` / `true` | `BooleanNode` |
| 整数（含 bignum） | `NumberNode`，保留十进制文本 `raw` |
| 半精度 / 单精度 / 双精度浮点 | `NumberNode`（参考实现 解码后的文本；NaN/Infinity 拒绝） |
| 十进制分数（tag 4） | `NumberNode`（精确 `BigDecimal`） |
| 文本字符串（含 tag 0 日期文本） | `StringNode` |
| 字节字符串 | `BinaryNode`（不会被当成文本） |
| CBOR simple value | `NumberNode`；开启 `READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT` 时为 `{"$cborSimpleValue": <整数>}` |
| 数组（含不定长） | `ArrayNode` |
| map（含不定长） | `ObjectNode` |

补充说明：

- 默认不保留 tag；tag 2/3（bignum）、tag 4（十进制分数）由 参考实现 语义解码；
- `readFeatures` 直接落到 参考实现 parser 特性：负数 bignum 规范解码（
  `DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING`）、`undefined` / simple value 的标记对象
  （`READ_UNDEFINED_AS_EMBEDDED_OBJECT` / `READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT`）、
  重复 key 检测（`STRICT_DUPLICATE_DETECTION`）；
- NaN / Infinity 无法由 `NumberNode` 表示，抛 `CborParseException`；
- CBOR map 的 key：文本 key 原样使用，整数 key 转为十进制字符串；其他类型 key 不在模块支持范围内，参考实现 解析失败时抛 `CborParseException`；
- 单值入口要求恰好一个顶层数据项：无值、多值都抛 `CborParseException`。

### Sequence 读取边界

- 空输入是合法的零项 sequence：`readAll()` 返回空列表；
- 只有一个数据项时返回长度为 1 的列表；
- 任一项格式非法或数据不完整时整体抛 `CborParseException`，不返回部分结果；
- 每个数据项独立解析，深度从 1 重新计数；
- `maxItems > 0` 时项数超过上限抛 `CborParseException`；
- 整段解析，暂未提供逐项/流式增量 API。

## 写出 API

| 方法 | 说明 |
|---|---|
| `formatBytes(value)` | 实体 → CBOR 字节；默认走内部 `CborTokenWriter` 直写，`preserveTags=true` 时走 `CborWriter` Node 写出 |
| `format(value, file / path)` | 写二进制文件 |
| `format(value, output: OutputStream)` | 二进制输出；非 pretty 模式直接流式写 Token |
| `formatCanonicalBytes(value)` | 实体 → canonical CBOR 字节（先构建 Node 树，再按 canonical key 顺序写出） |
| `formatAll(values: Iterable<Any?>): ByteArray` | 多个实体 → CBOR sequence 字节 |
| `parseAllToNodes(source, maxItems = 0)` / `parseAll(source, targetType, maxItems = 0)` | sequence 多值读取 |
| `CborWriter(canonical = false, preserveTags = false, bufferPool = null, writeFeatures = DEFAULT_WRITE_FEATURES).write(node)` | 公开的 Node → CBOR 单值写出器 |
| `Node.toCborBytes(preserveTags = false, bufferPool = null, writeFeatures = DEFAULT_WRITE_FEATURES)` | 扩展函数：Node → CBOR 字节 |
| `Node.toCanonicalCborBytes(preserveTags = false, bufferPool = null, writeFeatures = DEFAULT_WRITE_FEATURES)` | 扩展函数：Node → canonical CBOR 字节 |
| `CborSequenceWriter(canonical = false, preserveTags = false, bufferPool = null, writeFeatures = DEFAULT_WRITE_FEATURES).writeAll(nodes)` | 公开的 sequence 写出器 |
| `Collection<Node>.toCborSequenceBytes(canonical = false, preserveTags = false, bufferPool = null, writeFeatures = DEFAULT_WRITE_FEATURES)` | 扩展函数：Node 集合 → sequence 字节 |

不支持：

| 方法 | 行为 |
|---|---|
| `format(value)` / `formatPretty(value)` | 抛 `DocumentSerializationException("cbor mapper only supports binary output")` |
| `format(value, writer: Writer)` | 抛异常（二进制格式不支持字符流） |
| `formatPretty(value, file / path / output)` | 抛异常（二进制格式不支持 pretty） |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `formatBytes` | ✅ | `CborTokenWriter` 直接写 Token 流 |
| `format(value, OutputStream)` | ✅ | `createStreamingTokenWriter` 返回 `CborTokenWriter`（非 pretty、非 tag 保真时） |
| `format(value, File/Path)` | ❌ | 先得到 `ByteArray` 再写文件 |
| `formatCanonicalBytes` | ❌ | 先构建 Node 树再一次性写出 |

写出细节：

- 默认 Token 直写的对象/数组使用 CBOR **不定长**形式；`CborWriter` 使用**定长**形式。两者都合法且可互相读回；
- `CborWriter` 中对象/数组按实际元素数量写成定长 CBOR；
- 数字按 `NumberNode.raw`：
  - 不含小数点/指数标记 → CBOR 整数，`WRITE_MINIMAL_INTS` 开启（默认）时最短编码，关闭时按 int/long 范围写 4/8 字节；更大的整数写 bignum（tag 2/3）；
  - 其余 → CBOR 十进制分数（tag 4），保留 `BigDecimal` 精度；
- `StringNode` → 文本字符串；`BinaryNode` → 字节字符串；`NullNode` → `null`；
- `preserveTags=true` 时识别 tag 包装对象，先写 tag 再写 value，支持对象/数组/标量与嵌套 tag；
- `writeFeatures` 直接落到 参考实现 generator 特性：`WRITE_TYPE_HEADER`（自描述 tag）、
  `LENIENT_UTF_ENCODING`（替换非法代理对）、`STRINGREF`（共享字符串引用）、
  `ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING`（规范负数 bignum）、
  `STRICT_DUPLICATE_DETECTION`（重复字段名报错）；
- `WRITE_MINIMAL_DOUBLES` 只在 Token 直写路径（Bean 的 `Double` 字段）生效：开启时写最小二进制浮点，
  关闭时沿用模块历史的十进制分数写法（与 参考实现 默认写 8 字节 double 不同，见配置说明）。

## CBOR sequence（多值）

CBOR sequence（RFC 8742）是零个或多个自描述 CBOR 数据项首尾相接的字节流。单值 API（`CborReader` / `CborWriter` / `formatBytes` / `parse(ByteArray)`）语义不变，多值读写使用独立 API。

```kotlin
import com.mingliqiye.document.format.cbor.CborSequenceReader
import com.mingliqiye.document.format.cbor.CborSequenceWriter
import com.mingliqiye.document.format.cbor.toCborNodes
import com.mingliqiye.document.format.cbor.toCborSequenceBytes

val mapper = CborMapper()

// 写：多个实体按顺序写成 sequence（Token 直写，不构建 Node 树）
val bytes = mapper.formatAll(listOf(userA, userB))

// 读：List<Node>
val nodes = mapper.parseAllToNodes(bytes)
val nodes2 = CborSequenceReader.parseAll(bytes)
val nodes3 = bytes.toCborNodes()

// 读：List<Bean>（每个数据项反序列化为一个实体）
val users = mapper.parseAll(bytes, User::class.java)
val typed = mapper.parseAll<User>(bytes)   // reified 扩展

// 直接读写 Node 集合
val nodeBytes = listOf(nodeA, nodeB).toCborSequenceBytes()
val more = CborSequenceWriter(preserveTags = true).writeAll(listOf(nodeA, nodeB))
```

参数与边界：

- `CborSequenceReader(source, maxDepth, maxItems, preserveTags)`：`maxDepth` 限制单个数据项的 Node 树深度（0 = 不限制）；`maxItems` 限制数据项数量（0 = 不限制）；`preserveTags` 对每一项生效；
- `CborMapper.parseAllToNodes(source, maxItems)` / `parseAll(source, type, maxItems)` 的深度限制取自 Mapper 配置的 `maxNodeDepth`；
- `CborSequenceWriter(canonical, preserveTags)` / `toCborSequenceBytes(canonical, preserveTags)`；
- 空输入是合法的零项 sequence：`parseAll` 返回空列表，`formatAll(emptyList())` 返回空字节数组；
- 只有一个数据项时返回长度为 1 的列表；
- 任一项非法时整体抛 `CborParseException`，不返回部分结果。

## Tag 保真

默认关闭；开启后使用包装对象：

```json
{"$cborTag": 100, "value": "abc"}
```

- 只有对象**恰好**包含 `$cborTag` 与 `value` 两个字段，且 tag 是 `>= 0` 的整数值时，才视为包装对象；其他形态按普通对象处理；
- 写出时使用 `CBORGenerator.writeTag` 还原 tag，支持嵌套 tag；
- 保真模式下 `parse(ByteArray)` 禁用 Token 快路径（回退 Node 解析），`formatBytes` 改为 Node 写出（`CborWriter`）；
- Bean 映射会把包装对象当普通对象处理，因此保真模式下的 Bean 映射不感知 tag。

读取侧可见性（参考实现 2.20 行为）：

- tag 0/1/24 等普通 tag 在对应值上可见，会被包装保留；
- tag 2/3（bignum）、tag 4（十进制分数）已被 参考实现 语义解码，不会包装；写出时由数字策略重新编码等价 tag；
- 同一值上叠加多个 tag 时只能看到最外层第一个（参考实现 公有 API 限制），其余 tag 丢失；
- CBOR map key 上的 tag 不保留。

与写出特性的交互：

- `WRITE_TYPE_HEADER`（自描述 tag `d9d9f7`）与 `STRINGREF`（命名空间 tag 256、引用 tag 25）
  都依赖 tag 语义；与 `preserveTags=true` 同时使用时，读取端会把这些命名空间 tag 当作普通
  tag 包装，通常不应同时开启（保真模式建议保持这两个特性关闭）；
- `WRITE_MINIMAL_INTS` 不影响 tag 包装对象；`canonical` 模式只对包装对象内部的 map key 排序。

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper

val mapper = CborMapper(CborConfig.builder().preserveTags(true).build())

val bytes = mapper.formatBytes(mapOf("name" to "小于"))   // 普通 map，无 tag
val node = mapper.parse(bytes, com.mingliqiye.document.core.Node::class.java)

// 手工构造带 tag 的 Node 并写出
val tagged = com.mingliqiye.document.core.ObjectNode.of(
    CborConfig.TAG_FIELD to com.mingliqiye.document.core.NumberNode.of(100),
    CborConfig.VALUE_FIELD to com.mingliqiye.document.core.StringNode.of("abc"),
)
val taggedBytes = mapper.formatBytes(tagged)     // 等价于 tag(100) + "abc"

val restored = mapper.parse(taggedBytes, com.mingliqiye.document.core.Node::class.java)
// restored 是 {"$cborTag":100,"value":"abc"} 包装对象
```

配合 canonical：

```kotlin
val canonical = mapper.formatCanonicalBytes(tagged)
val canonicalNode = mapper.parse(canonical, com.mingliqiye.document.core.Node::class.java)
```

## Canonical CBOR

```kotlin
val bytes = mapper.formatCanonicalBytes(value)
val nodeBytes = node.toCanonicalCborBytes()
```

规则：

- 对象 key 按 UTF-8 编码长度升序，同长度按字节序排序；
- 数组保持原顺序；
- 整数使用最短编码形式（关闭 `WRITE_MINIMAL_INTS` 时按定长编码，canonical 字节会相应变化）；
- `preserveTags=true` 时 tag 包装对象也会被识别并写出。

注意：canonical 只保证 map key 顺序与整数最短编码，不覆盖所有 RFC 8949 确定性要求（例如浮点最短形式等），不是完整的确定性编码实现。

## 示例

### 1. Bean ↔ CBOR 字节

```kotlin
import com.mingliqiye.document.format.cbor.CborMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = CborMapper()
val user = User().apply {
    name = "小于"
    age = 18
}

val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
```

### 2. Node 读写

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse(mapper.formatBytes(user), Node::class.java)
println(node.asObject()["name"]?.asString())
```

### 3. 二进制字节串往返

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.ObjectNode

val node = ObjectNode.of("data" to BinaryNode.of(byteArrayOf(0, 1, 2, -1)))
val bytes = mapper.formatBytes(node)

val restored = mapper.parse(bytes, Node::class.java)
check(restored.asObject()["data"]?.isBinary == true)
check(restored.asObject()["data"]?.asBinary()?.contentEquals(byteArrayOf(0, 1, 2, -1)) == true)
```

### 4. canonical 输出

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

val node = ObjectNode.of(
    "b" to StringNode.of("2"),
    "aa" to StringNode.of("1"),
    "a" to StringNode.of("0"),
)

val canonical = mapper.formatCanonicalBytes(node)
// key 顺序：a（长度 1）、b（长度 1，字节序 a<b）、aa（长度 2）
val restored = mapper.parse(canonical, Node::class.java)
```

### 5. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.cbor"))
val fromFile = mapper.parse(File("build/user.cbor"), User::class.java)

mapper.format(user, Paths.get("build/user2.cbor"))
val fromPath = mapper.parse(Paths.get("build/user2.cbor"), User::class.java)
```

### 6. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(user, output)          // 二进制流式写
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 7. CBOR sequence 基本读写

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.cbor.toCborNodes

val a = User().apply { name = "a"; age = 1 }
val b = User().apply { name = "b"; age = 2 }

val seq: ByteArray = mapper.formatAll(listOf(a, b))
val nodes: List<Node> = mapper.parseAllToNodes(seq)
check(nodes.size == 2)

val users: List<User> = mapper.parseAll(seq, User::class.java)
check(users[1].name == "b")

val nodes2 = seq.toCborNodes()
check(nodes2.size == 2)
```

### 8. 空 sequence

```kotlin
val empty = mapper.formatAll(emptyList())
check(empty.isEmpty())
check(mapper.parseAllToNodes(empty).isEmpty())
check(mapper.parseAll(empty, User::class.java).isEmpty())
```

### 9. sequence 中任一项非法整体失败

```kotlin
import com.mingliqiye.document.format.cbor.exception.CborParseException

val valid = mapper.formatBytes(user)
val broken = valid.copyOf(valid.size - 1)     // 截断最后一项
val error = runCatching { mapper.parseAllToNodes(valid + broken) }.exceptionOrNull()
check(error is CborParseException)
```

### 10. maxItems 限制

```kotlin
import com.mingliqiye.document.format.cbor.exception.CborParseException

val seq = mapper.formatAll(listOf(a, b, a))
val error = runCatching { mapper.parseAllToNodes(seq, maxItems = 2) }.exceptionOrNull()
check(error is CborParseException)
```

### 11. Node 集合 → sequence 字节

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.cbor.toCborSequenceBytes

val nodes: List<Node> = listOf(mapper.toNode(a), mapper.toNode(b))
val bytes = nodes.toCborSequenceBytes()
check(mapper.parseAllToNodes(bytes).size == 2)
```

### 12. 深度限制

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.cbor.CborReader
import com.mingliqiye.document.format.cbor.exception.CborParseException

val nested = mapOf("a" to mapOf("b" to 1))
val bytes = mapper.formatBytes(nested)

val readerError = runCatching { CborReader(bytes, maxDepth = 1).read() }.exceptionOrNull()
check(readerError is CborParseException)

// Mapper 配置 maxNodeDepth > 0 时禁用 Token 快路径，走 Node 路径限制深度
val guarded = CborMapper(CborConfig.builder()
    .base(com.mingliqiye.document.serialization.DocumentMapperConfig.builder().maxNodeDepth(1).build())
    .build())
val mapperError = runCatching { guarded.parse(bytes, Node::class.java) }.exceptionOrNull()
println(mapperError?.message)
```

### 13. 文本 API 不支持

```kotlin
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

val error = runCatching { mapper.parse("""{"name":"x"}""", User::class.java) }.exceptionOrNull()
check(error is DocumentSerializationException)
```

### 14. Java 示例

```java
import com.mingliqiye.document.format.cbor.CborMapper;

public class CborDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        CborMapper mapper = new CborMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        byte[] bytes = mapper.formatBytes(user);
        User restored = mapper.parse(bytes, User.class);
        System.out.println(restored.name + ":" + restored.age);

        // 文件/流
        mapper.format(user, new java.io.File("build/user.cbor"));
        User fromFile = mapper.parse(new java.io.File("build/user.cbor"), User.class);
        System.out.println(fromFile.age);
    }
}
```

### 15. 不定长与定长写法

```kotlin
import com.mingliqiye.document.format.cbor.CborWriter
import com.mingliqiye.document.format.cbor.toCborBytes

// Token 直写：不定长 map/array
val tokenBytes = mapper.formatBytes(user)

// CborWriter：定长 map/array
val definiteBytes = CborWriter().write(mapper.toNode(user))
val definiteBytes2 = mapper.toNode(user).toCborBytes()

// 两者都能被读回
check(mapper.parse(tokenBytes, User::class.java).name == "小于")
check(mapper.parse(definiteBytes, User::class.java).name == "小于")
check(mapper.parse(definiteBytes2, Node::class.java) == mapper.parse(tokenBytes, Node::class.java))
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 文本 API（`parse(String)` / `format`） | `DocumentSerializationException`（只支持二进制） |
| `formatPretty` | `DocumentSerializationException`（二进制格式不支持 pretty） |
| NaN / Infinity | 读取抛 `CborParseException`；`NumberNode` 也无法表示；写出时 `WRITE_MINIMAL_DOUBLES` 路径同样拒绝 |
| 非字符串 map key | 文本 key 原样、整数 key 转十进制字符串；其他类型失败 |
| 无值 / 多个顶层值（单值入口） | `CborParseException` |
| 单值入口输入 sequence | `CborParseException`（请用 `CborSequenceReader`） |
| `preserveTags=false` | tag 不保留；tag 2/3/4 由 参考实现 语义解码，tag 0 日期读成字符串 |
| `preserveTags=true` | Token 快路径禁用；Bean 映射看不到 tag；多个嵌套 tag 只能保留最外层；与 `WRITE_TYPE_HEADER` / `STRINGREF` 不建议同时开启 |
| `READ_UNDEFINED_AS_EMBEDDED_OBJECT` / `READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT` | 读取为标记对象并禁用 Token 快路径；标记对象无法直接写回（参考实现 无对应写出 API），需要写回时自行转换为普通值 |
| `WRITE_MINIMAL_DOUBLES` | 仅 Token 直写路径（Bean 的 `Double`）生效；Node 数字仍走整数/十进制分数策略 |
| `stringref` 输出 | 需要支持 stringref 的读取器（参考实现 CBOR 读取器默认支持）；与 `preserveTags` 不建议同时开启 |
| `WRITE_TYPE_HEADER` 输出 | 读取端 default 模式忽略自描述 tag；`preserveTags=true` 时会读成 `$cborTag` 包装 |
| `STRICT_DUPLICATE_DETECTION` | 读取时重复 key 抛 `CborParseException`；写出特性只约束直接使用 `CborTokenWriter` 的调用方 |
| canonical 编码 | 只保证 map key 顺序与整数最短编码（受 `WRITE_MINIMAL_INTS` 影响），不是完整 RFC 8949 确定性要求 |
| sequence 解析 | 整段解析，任一项失败整体失败；无逐项/流式增量 API |
| `format(value, File/Path)` | 先得到完整 `ByteArray` 再写文件 |

## 与通用 `DocumentMapper` 的差异

- 继承 `BinaryDocumentMapper`：仅二进制 API，文本/字符流 API 抛 `DocumentSerializationException`；
- 构造：`CborConfig` 专属配置类与 `CborMapper.builder()`；
- `parse(ByteArray)` / `formatBytes`：默认 Token 直读/直写（不定长 CBOR），可选 Node 路径（定长）；
- 配置：`CborConfig.readFeatures` / `writeFeatures` 对齐 参考实现 CBOR 的 parser/generator 特性，
  并补充 `STRICT_DUPLICATE_DETECTION`；`CborConfigBuilder` 提供 `enable` / `disable` / `configure`
  与通用配置便捷方法；
- 额外能力：`formatCanonicalBytes`、CBOR sequence（`parseAllToNodes` / `parseAll` / `formatAll`、`CborSequenceReader` / `CborSequenceWriter`）、tag 保真；
- 公开的 Node 级工具：`CborReader`、`CborWriter`、`CborSequenceReader` / `CborSequenceWriter`、`Node.toCborBytes` / `Node.toCanonicalCborBytes` / `ByteArray.toCborNodes` / `Collection<Node>.toCborSequenceBytes`。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-cbor 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-cbor/README.md)
