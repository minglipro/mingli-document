---
title: Properties
description: format-properties 模块：PropertiesMapper、PropertiesConfig、参考实现 JavaPropsSchema 对齐（分隔符/下标标记/前缀/输出格式）、读写特性、点分 key 路径与扁平文本互转
---

# Properties

`format-properties` 提供 Java Properties 格式读写，核心类型是 `PropertiesMapper`。

Properties 是扁平 key/value 文本；模块用路径分隔符（默认 `.`）与数组下标（默认 `[index]`，
也支持 参考实现 默认的简单数字下标 `tags.1`）在扁平文本与 `Node` 树之间互转。

配置对齐 参考实现 `Properties 底层库` 的 `JavaPropsSchema`（参考实现 2.15）：
分隔符、下标标记、前缀、输出格式等 JSON 风格选项均有对应配置，默认值一致。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:properties:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `properties` |
| 版本 | `2.5.1` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | 无（基于 JDK `java.util.Properties`） |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.properties` |

## 特性

- **参考实现 `JavaPropsSchema` 对齐配置**：`pathSeparator`（支持多字符/空串）、`pathSeparatorEscapeChar`、
  `indexMarker`、`firstArrayOffset`、`keyValueSeparator`、`lineEnding`、`lineIndentation`、`header`、`prefix`；
- **数组两种写法都支持读取**：下标标记 `tags[0]` 与简单数字下标 `server.1`（参考实现 默认开启），
  写出默认使用简单下标（`firstArrayOffset` 起，默认 `1`），可切回 `[index]` 标记；
- **稠密数组**：下标只表示排序，不填充空洞（参考实现 语义）；
- **参考实现 结构合并语义**：同一路径同时有值与子节点时值成为数组首元素或空字符串键字段；
  下标子节点与命名字节点混用时下标降级为名字；
- **读写特性**：`PropertiesReadFeature` / `PropertiesWriteFeature` + `enable/disable/configure`；
- **输出格式**：键值分隔符、行结尾、行缩进、头部注释（header）；
- **前缀**：读取时从 key 剥离、写出时附加到根路径（`prefix + pathSeparator + ...`）；
- **百分号编码扩展**：字段名中的 `%` `.` `[` `]` 默认转义，保证可往返（本模块扩展，可关闭为纯 参考实现 语义）；
- **类型推断**：`true` / `false` / 数字 / 字符串；
- **原生 Token 读取**：`parse(String)` 通过内部 `PropertiesTokenReader` 直接从扁平 key/value 生成 Token 流，不构建 Node 树；
- **文件/流读写**：文本编码由 `fileCharset` 控制（默认 UTF-8）；
- **安全深度限制**：`maxDepth` 默认 512，避免恶意超深路径导致栈溢出。

## 参考实现 配置对齐

参考实现 2.15 的 `Properties 底层库` **没有** `PropertiesReadFeature` /
`PropertiesWriteFeature` 枚举（`JavaPropsFactory` 明确注释 “No format-specific configuration, yet”），
其格式配置集中在 `JavaPropsSchema`。本模块把 `JavaPropsSchema` 全部选项实现为 `PropertiesConfig`
字段，并提供同风格特性枚举便于统一 enable/disable/configure。

### `JavaPropsSchema` → `PropertiesConfig` 映射

| 参考实现 `JavaPropsSchema` | 默认值 | 本模块配置 | 读取/写出 |
|---|---|---|---|
| `firstArrayOffset()` | `1` | `firstArrayOffset` | 写出 |
| `pathSeparator()` | `"."` | `pathSeparator`（支持多字符、空串） | 读 + 写 |
| `pathSeparatorEscapeChar()` | `'\0'`（关闭） | `pathSeparatorEscapeChar` | 读取（仅单字符分隔符；写出不转义分隔符，与 参考实现 相同） |
| `indexMarker()` | `Markers("[", "]")` | `indexMarker: PropertiesIndexMarker?` | 读（标记解析）+ 写（启用标记写法时） |
| `parseSimpleIndexes()` | `true` | `PropertiesReadFeature.PARSE_SIMPLE_INDEXES` / `parseSimpleIndexes` | 读 |
| `writeIndexUsingMarkers()` | `false` | `PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS` / `writeIndexUsingMarkers` | 写 |
| `lineIndentation()` | `""` | `lineIndentation` | 写 |
| `keyValueSeparator()` | `"="` | `keyValueSeparator` | 写 |
| `lineEnding()` | `"\n"` | `lineEnding` | 写 |
| `header()` | `""` | `header` | 写 |
| `prefix()` | `null` | `prefix` | 读 + 写 |
| `withoutPathSeparator()` | – | `withoutPathSeparator()` | 读 + 写 |
| `withoutIndexMarker()` | – | `withoutIndexMarker()` | 读 + 写 |
| `withoutLineIndentation()` / `withoutHeader()` | – | 同名方法 | 写 |

### 读取特性：`PropertiesReadFeature`

| 特性 | 默认 | 行为 | 参考实现 对应 |
|---|---|---|---|
| `PARSE_SIMPLE_INDEXES` | 开 | 1~9 位纯数字路径段解析为数组下标（`server.1`）；关闭后作为对象字段名 | `parseSimpleIndexes` |
| `ALLOW_INDEX_MARKERS` | 开 | 识别 `indexMarker` 下标标记（`tags[0]`）；关闭等价 `withoutIndexMarker()` | `indexMarker` 是否生效 |
| `DECODE_PERCENT_ESCAPED_SEGMENTS` | 开 | 解码 `%25` / `%2E` / `%5B` / `%5D`（本模块扩展） | 无（参考实现 不支持） |

### 写出特性：`PropertiesWriteFeature`

| 特性 | 默认 | 行为 | 参考实现 对应 |
|---|---|---|---|
| `WRITE_INDEX_USING_MARKERS` | 关 | 数组下标写作 `tags[1]`；关闭时写作简单下标 `tags.1`（`firstArrayOffset` 起） | `writeIndexUsingMarkers` |
| `ESCAPE_PERCENT_ENCODED_SEGMENTS` | 开 | 字段名中的 `%` `.` `[` `]` 百分号编码（本模块扩展） | 无（参考实现 不支持） |
| `ESCAPE_NON_ASCII` | 关 | 非 ASCII 字符写成 `\uXXXX`，与 参考实现 基于 Latin-1 的字面输出一致 | 无开关（参考实现 固定行为） |

## 配置：`PropertiesConfig`

`PropertiesConfig` 继承通用 `DocumentMapperConfig`，可用 `PropertiesConfig.builder()` 构建：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `pathSeparator` | `String` | `"."` | 路径分隔符；空串表示不拆分嵌套；支持多字符 |
| `pathSeparatorEscapeChar` | `Char` | `'\u0000'` | 路径分隔符转义字符（读取）；`'\u0000'` 表示关闭，仅单字符分隔符生效 |
| `indexMarker` | `PropertiesIndexMarker?` | `[` `]` | 下标标记；`null` 表示关闭 |
| `firstArrayOffset` | `Int` | `1` | 数组写出起始编号 |
| `keyValueSeparator` | `String` | `"="` | 键值分隔字符串（写出） |
| `lineEnding` | `String` | `"\n"` | 行结尾（写出） |
| `lineIndentation` | `String` | `""` | 每行缩进前缀（写出） |
| `header` | `String` | `""` | 输出头部原始文本（写出，通常为注释行） |
| `prefix` | `String?` | `null` | 读写前缀；写出为 `prefix + pathSeparator + ...` |
| `maxDepth` | `Int` | `512` | 键路径最大嵌套深度（根对象为 `1`），超限抛 `PropertiesParseException` |
| `readFeatures` | `Set<PropertiesReadFeature>` | 见上表 | 读取特性集合 |
| `writeFeatures` | `Set<PropertiesWriteFeature>` | 见上表 | 写出特性集合 |

派生只读属性：`parseSimpleIndexes`、`allowIndexMarkers`、`decodePercentEscapedSegments`、
`writeIndexUsingMarkers`、`escapePercentEncodedSegments`、`escapeNonAscii`。

常量：`PropertiesConfig.DEFAULT_PATH_SEPARATOR = "."`、`DEFAULT_PATH_SEPARATOR_ESCAPE_CHAR = '\u0000'`、
`DEFAULT_FIRST_ARRAY_OFFSET = 1`、`DEFAULT_KEY_VALUE_SEPARATOR = "="`、`DEFAULT_LINE_ENDING = "\n"`、
`DEFAULT_LINE_INDENTATION = ""`、`DEFAULT_HEADER = ""`、`DEFAULT_MAX_DEPTH = 512`、
`DEFAULT_INDEX_MARKER`、`DEFAULT_READ_FEATURES`、`DEFAULT_WRITE_FEATURES`。

`PropertiesIndexMarker.create(start, end)` 创建自定义下标标记（起止均不能为空）。

## `PropertiesConfigBuilder` 方法

格式选项：

| 方法 | 说明 |
|---|---|
| `pathSeparator(String)` / `withoutPathSeparator()` | 设置/关闭路径分隔符 |
| `pathSeparatorEscapeChar(Char)` | 设置分隔符转义字符 |
| `indexMarker(PropertiesIndexMarker)` / `indexMarker(start, end)` / `withoutIndexMarker()` | 下标标记 |
| `firstArrayOffset(Int)` | 数组起始编号（写出） |
| `parseSimpleIndexes(Boolean)` | 等价 `configure(PARSE_SIMPLE_INDEXES, ...)` |
| `writeIndexUsingMarkers(Boolean)` | 等价 `configure(WRITE_INDEX_USING_MARKERS, ...)` |
| `keyValueSeparator(String)` / `lineEnding(String)` / `lineIndentation(String)` / `withoutLineIndentation()` | 输出格式 |
| `header(String)` / `withoutHeader()` | 头部注释 |
| `prefix(String?)` / `withoutPrefix()` | 读写前缀 |
| `maxDepth(Int)` | 解析最大深度（必须为正数） |
| `enable/disable/configure(PropertiesReadFeature)` | 读取特性 |
| `enable/disable/configure(PropertiesWriteFeature)` | 写出特性 |

通用 `DocumentMapperConfig` 便捷方法（全部转发）：

`field`、`missingFieldPolicy`、`failOnUnknownFields`、`writeNulls`、`namingStrategy`、`fileCharset`、
`atomicFileWrite`、`createParentDirectories`、`maxInputSize`、`maxStringLength`、`maxNodeDepth`、
`maxNodeCount`、`resolveReferences`、`writeReferences`、`remoteReferenceResolver`、`beanValidator`、
`enable/disable/configure(DeserializationFeature)`、`failOnNullForPrimitives`、`subtype`（4/5 参）、`typeProperty`。

## 配置示例

```kotlin
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.PropertiesMapper

// 1. 使用 "->" 作为路径分隔符、数组使用 [index] 标记、写出带头部注释与缩进
val config = PropertiesConfig.builder()
    .pathSeparator("->")
    .writeIndexUsingMarkers(true)
    .header("# generated by mingli-document\n")
    .lineIndentation("  ")
    .build()
val mapper = PropertiesMapper(config)

// 2. 纯 参考实现 默认语义：关闭本模块的百分号编码扩展
val backendLike = PropertiesConfig.builder()
    .disable(com.mingliqiye.document.format.properties.PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS)
    .disable(com.mingliqiye.document.format.properties.PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS)
    .build()

// 3. 旧式：通用配置 + 显式解析深度
val legacy = PropertiesMapper(
    com.mingliqiye.document.serialization.DocumentMapperConfig.builder()
        .missingFieldPolicy(com.mingliqiye.document.serialization.MissingFieldPolicy.FAIL)
        .build(),
    64,
)

// 4. Builder 入口
val fromBuilder = PropertiesMapper.builder()
    .config(config)
    .maxDepth(128)
    .build()
```

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `PropertiesMapper()` | 默认配置 |
| `PropertiesMapper(config: PropertiesConfig)` | Properties 配置 |
| `PropertiesMapper(config: DocumentMapperConfig)` | 通用配置 + 默认格式选项/解析深度 |
| `PropertiesMapper(config: DocumentMapperConfig, maxDepth: Int)` | 通用配置 + 显式解析深度（必须为正数） |

`PropertiesMapper.builder()` 返回 `PropertiesMapperBuilder`：

| 方法 | 说明 |
|---|---|
| `maxDepth(Int)` | 解析最大深度；显式设置时优先于 `PropertiesConfig.maxDepth` |
| `config(PropertiesConfig)` / `config(DocumentMapperConfig)` | 设置配置 |
| `addSerializer/addDeserializer/addConverter/addModule(s)/objectInstantiator/propertyMetadataResolver` | 通用 Builder 能力 |

## 读取 API

### Mapper 入口

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType)` | 原生 Token 路径：`PropertiesTokenReader` 直接生成 Token 流解码 Bean |
| `parse(source: ByteArray, targetType)` | 按 `fileCharset` 解码成文本后解析 |
| `parse(file / path, targetType)` | 文件 → 实体 |
| `parse(input: InputStream, targetType)` | 流 → 实体 |
| `parse(reader: Reader, targetType)` | 字符流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 Token 读取器 → 实体 |

### 独立读取器

```kotlin
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.PropertiesReader

val node = PropertiesReader("""
    name=小于
    age=18
""".trimIndent()).read()

// 带配置
val withConfig = PropertiesReader("server->host=localhost", PropertiesConfig.builder().pathSeparator("->").build()).read()

// 显式解析深度（0/负数关闭保护）
val deep = PropertiesReader("a.b=1", maxDepth = 64).read()

// 静态便捷方法 / 扩展函数
val node2 = PropertiesReader.parse("a.b=1")
val node3 = "a.b=1".toPropertiesNode()
val node4 = "a->b=1".toPropertiesNode(PropertiesConfig.builder().pathSeparator("->").build())
```

| 类/函数 | 说明 |
|---|---|
| `PropertiesReader(source, config)` | 读取器；`read(): Node` |
| `PropertiesReader(source, maxDepth)` | 默认配置 + 显式解析深度 |
| `PropertiesReader.parse(source)` | 静态解析（默认配置） |
| `String.toPropertiesNode()` / `String.toPropertiesNode(config)` | 字符串 → Node |

读取细节（对齐 参考实现 `JavaPropsParser`）：

- 使用 JDK `Properties.load`，因此 `#`/`!` 注释、`\uXXXX`、`\n`、行续接等 Properties 语法全部生效；
- key 先按字典序排序，再逐个插入路径树（保证确定性输出；参考实现 使用 Properties 迭代顺序，顺序未定义）；
- 空输入返回空 `ObjectNode`；
- 值的类型推断顺序：`true` → `BooleanNode(true)`、`false` → `BooleanNode(false)`、可解析为 `BigDecimal` → `NumberNode`（保留原文）、否则 `StringNode`；
- 数组稠密：下标只用于排序（`p[10]` 与 `p[2]` 相邻），不填充空洞；
- 同一路径既有值又有子节点时：下标子节点存在则值成为数组首元素（`str=first` + `str.2=second` → `["first","second"]`），
  否则值成为空字符串键 `""` 字段；
- 下标子节点与命名字节点混用时，下标降级为名字（`a[0]=x` + `a.b=y` → `{"a":{"0":"x","b":"y"}}`）；
- 配置了 `prefix` 且 key 不含 `prefix + pathSeparator` 时该 key 被忽略（参考实现 语义）；
- 路径深度超过 `maxDepth` 抛 `PropertiesParseException`。

### Token 路径

`PropertiesMapper.parse(String)` 使用内部 `PropertiesTokenReader`：

- 与 `PropertiesReader` 使用同一轻量路径树，直接生成 `START_OBJECT` / `FIELD_NAME` / 标量 / `END_OBJECT` 等 Token，
  不构建完整 Node 树；
- 解析语义与 `PropertiesReader` 完全一致（同一 `PropertiesConfig`）；Token 序列与
  `NodeTokenReader(PropertiesReader(source, config).read())` 一致；
- `maxNodeDepth` / `maxNodeCount` / `maxStringLength` 在生成 Token 时校验，超限抛 `DocumentSerializationException`；
- Token 路径无法校验的只有 Node 树循环检测（扁平原生结构不可能是环）；
- `maxInputSize` 仍由基类 `parse` 在创建 Token 读取器前校验。

需要自定义 Token 来源时，实现 `DocumentTokenReader` 后调用 `DocumentMapper.fromTokens(...)`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → Properties 文本 |
| `formatPretty(value)` | 与 `format` 输出相同（Properties 没有 pretty 概念） |
| `formatBytes(value)` | 实体 → 按 `fileCharset` 编码的字节 |
| `format(value, file / path)` | 写文件 |
| `format(value, output: OutputStream)` | 先编码为字节再写出 |
| `format(value, writer: Writer)` | 直接写字符流 |
| `Node.toPropertiesString()` / `Node.toPropertiesString(config)` | 扩展函数：Node → Properties 文本 |

写出规则（对齐 参考实现 `JavaPropsGenerator` / `JavaPropsSchema`）：

- 根节点必须是 `ObjectNode` 或 `ArrayNode`，否则抛 `PropertiesException`；
- 对象字段按 `pathSeparator` 连接路径；配置 `prefix` 时根路径为 `prefix + pathSeparator + ...`；
- 数组默认“简单下标”写法：元素 `i` 写成 `i + firstArrayOffset`（默认 `tags.1`、`tags.2`）；
  开启 `WRITE_INDEX_USING_MARKERS` 后写成 `tags[1]`、`tags[2]`（未配置 `indexMarker` 时回退简单写法）；
- `keyValueSeparator` 连接键值，`lineEnding` 结束每行，`lineIndentation` 作为每行前缀；
- `header` 原样输出在所有条目之前（不追加换行；仅在至少有一条输出时写出），通常写 `#` 注释行；
- 空对象/空数组不产生任何输出；
- 键/值按 JDK Properties 规则转义（`\\`、空格、`=`、`:`、`#`、`!`、控制字符、`\uXXXX`）；
  本模块额外转义值行首空格以避免 `Properties.load` 丢弃前导空白；
- 本模块扩展：字段名中的 `%` `.` `[` `]` 默认百分号编码（`%25` `%2E` `%5B` `%5D`），
  保证含分隔符的字段名可往返；关闭后按 参考实现 语义直接拼接（会歧义）；
- `NullNode` 写成空值（`path=`），读取回来是 `StringNode("")`（不保型）；
- `BinaryNode` 写成 Base64 字符串，读取回来是 `StringNode`（不保型）；
- 数字/布尔写成未转义文本；字符串值按需转义（`ESCAPE_NON_ASCII` 开启时非 ASCII 写成 `\uXXXX`）。

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.toPropertiesString

val config = PropertiesConfig.builder()
    .pathSeparator("->")
    .writeIndexUsingMarkers(true)
    .build()
val node = ObjectNode.of(
    "name" to StringNode.of("小于"),
    "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
)
println(node.toPropertiesString(config))
// name=小于
// tags[1]=a
// tags[2]=b
```

## 路径与转义规则

读取时（默认配置）：

```text
user.name=小于                -> {"user":{"name":"小于"}}
user.tags[0]=a                -> {"user":{"tags":["a"]}}       （下标标记）
p.1.x=1 / p.2.x=2             -> {"p":[{"x":1},{"x":2}]}        （简单下标，稠密数组）
str=first / str.2=second      -> {"str":["first","second"]}     （值 + 子节点）
a[0]=x / a.b=y                -> {"a":{"0":"x","b":"y"}}        （下标/名字混用降级）
[0].name=小于                 -> [{"name":"小于"}]              （根为数组）
a.b%2Ec=1                     -> {"a":{"b.c":1}}                （%2E 转义点号）
a%5B0%5D=1                    -> {"a[0]":1}                     （%5B/%5D 转义方括号）
a#.b=1（escapeChar='#'）      -> {"a.b":1}                      （路径分隔符转义字符）
```

写出时（默认配置）：

```text
{"tags":["a","b"]}            -> tags.1=a / tags.2=b           （简单下标、起始 1）
{"tags":["a","b"]}（开启标记） -> tags[1]=a / tags[2]=b
{"user.name":"小于"}          -> user%2Ename=小于               （百分号扩展）
```

| 转义 | 写法 | 说明 |
|---|---|---|
| 百分号编码（路径段，扩展） | `%` → `%25`、`.` → `%2E`、`[` → `%5B`、`]` → `%5D` | 默认开启；可用读写特性关闭 |
| 路径分隔符转义 | `pathSeparatorEscapeChar`（默认关闭，仅读取） | 仅单字符分隔符；如 `#` 时 `a#.b` → 字段 `a.b` |
| JDK key 转义 | `\\`、`\ `、`\=`、`\:`、`\#`、`\!`、`\t`/`\n`/`\r`、`\uXXXX` | 遵循 `java.util.Properties` 语法 |
| JDK value 转义 | `\\`、`\t`/`\n`/`\r`、控制字符 `\uXXXX`；本模块额外转义行首空格 | 遵循 `java.util.Properties` 语法 |

## 类型映射

| Node | Properties 读取 | Properties 写出 |
|---|---|---|
| `ObjectNode` | 分隔符分段的嵌套对象 | 递归展开为路径 |
| `ArrayNode` | 下标标记/简单下标；稠密数组 | 递归展开为 `[index]` 或 `.index` |
| `StringNode` | 无法推断为其它类型的文本 | 值文本（按需转义） |
| `NumberNode` | 可解析为 `BigDecimal` 的文本，保留原文 | `NumberNode.raw`（不转义） |
| `BooleanNode` | `true` / `false` | `true` / `false`（不转义） |
| `NullNode` | 不会从文本读出空值节点（空值读成 `StringNode("")`） | 空值（`path=`） |
| `BinaryNode` | 不会从文本读出（Base64 文本读成 `StringNode`） | Base64 字符串 |

## 深度限制

```kotlin
val mapper = PropertiesMapper.builder()
    .maxDepth(512)
    .build()

// 或在配置中
val mapper2 = PropertiesMapper(PropertiesConfig.builder().maxDepth(64).build())
```

- 默认 `512`，按 key 路径令牌数计算（根对象深度为 `1`，`a.b.c=1` 最深层级为 `4`）；
- 超限时 Node / Token 路径都抛出
  `com.mingliqiye.document.format.properties.exception.PropertiesParseException`；
- 通用 `DocumentMapperConfig.maxNodeDepth` 保持 Node 限制语义：超限抛
  `com.mingliqiye.document.serialization.exception.DocumentSerializationException`；
- `PropertiesReader` / `PropertiesTokenReader` 的显式 `maxDepth` 传 `0` 或负数表示关闭保护。

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 空文本 | 返回空 `ObjectNode`（参考实现 parser 无 Token；本模块保留历史空对象行为） |
| 根节点不是对象/数组（写出） | `PropertiesException("Properties root must be an object or array")` |
| 路径同时是标量与子节点 | 参考实现 合并语义：值成为数组首元素或空字符串键字段 |
| 下标与命名路径混用 | 下标降级为名字（参考实现 语义） |
| 数组空洞 | 不存在：下标只排序，数组稠密 |
| 值中的 `true`/`false`/数字文本 | 读取时推断为对应节点类型（无法强制保持字符串） |
| 需要保持字符串 `"true"` 的语义 | 用 Schema 或转换器处理（Properties 本身无类型标记） |
| `NullNode` / `BinaryNode` 往返 | 不保型：读回空字符串 / Base64 字符串 |
| 非 ASCII 文本 | 文件/流默认 UTF-8（`fileCharset`）；需要 参考实现 字面输出时开启 `ESCAPE_NON_ASCII` |
| 字段名含路径分隔符 | 默认百分号编码可往返；关闭扩展后按 参考实现 语义会歧义 |
| 空 key / 只有空 key 的输入 | 根节点为标量字符串（参考实现 语义） |

## 与 参考实现 的差异 / 未支持项

1. **没有 `PropertiesReadFeature` / `PropertiesWriteFeature` 上游枚举**：参考实现 2.15 的 Properties 模块
   只有 `JavaPropsSchema`；本模块特性枚举是 `JavaPropsSchema` 布尔选项 + 本模块扩展的等价封装；
2. **字节编码**：参考实现 字节输入固定 ISO-8859-1；本模块统一按 `fileCharset`（默认 UTF-8）编解码，
   通过 `PropertiesConfigBuilder.fileCharset(Charset)` 调整；
3. **输出非 ASCII**：参考实现 基于 Latin-1 固定把 `>0xFF` 字符写成 `\uXXXX`；本模块默认原样输出
   （由 `fileCharset` 决定字节编码），开启 `ESCAPE_NON_ASCII` 后字面输出与 参考实现 一致；
4. **根值**：参考实现 允许任意根值（标量/数组/对象）；本模块写出要求对象或数组根；
5. **空输入**：参考实现 parser 对空内容不产生 Token（databind 报空文档）；本模块返回空对象；
6. **读取顺序**：参考实现 按 `Properties` 迭代顺序（未定义）；本模块固定按 key 字典序排序（确定性）；
7. **路径分隔符转义字符**：与 参考实现 相同，仅在“仅分隔符”拆分路径生效；当 key 含下标标记起始字符时
   参考实现 使用完整拆分器并忽略转义字符，本模块镜像该行为；
8. **前缀**：与 参考实现 相同，`prefix` 仅在下标标记生效时用于读取；关闭下标标记时忽略前缀；
9. **未支持**：参考实现 的流式 `JsonParser` / `JsonGenerator` API、`writeValueAsProperties` /
   `writeValueAsMap` 便捷方法、预解析 `Properties`/`Map` 输入的 `createParser(Map)`、
   按操作设置 `FormatSchema`（`ObjectReader.with(schema)`）——本模块的 `DocumentMapper` API 通过
   `PropertiesConfig` 在构造 Mapper 时配置；
10. **`maxDepth`**：本模块安全限制（默认 512）为额外能力；参考实现 2.15 通过 `StreamReadConstraints`
    （默认 `maxNestingDepth=1000`）限制深度。

## 示例

### 1. 字符串 ↔ Bean

```kotlin
import com.mingliqiye.document.format.properties.PropertiesMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = PropertiesMapper()
val user = mapper.parse("""
    name=小于
    age=18
""".trimIndent(), User::class.java)

println(mapper.format(user))
// age=18
// name=小于
```

### 2. Node 树读取

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse("""
    user.name=小于
    user.tags[0]=a
    user.tags[1]=b
""".trimIndent(), Node::class.java)

val userNode = node.asObject()["user"]?.asObject()
println(userNode?.get("name")?.asString())        // 小于
println(userNode?.get("tags")?.asArray()?.size)   // 2
```

### 3. 简单数字下标（参考实现 默认）

```kotlin
val text = """
    server.1.host=127.0.0.1
    server.1.port=8080
    server.2.host=127.0.0.2
"""

val node = mapper.parse(text, Node::class.java)
val servers = node.asObject()["server"]?.asArray()
println(servers?.size)                                        // 2
println(servers?.get(0)?.asObject()?.get("port")?.asNumber()?.toInt()) // 8080
```

### 4. 自定义路径分隔符与前缀

```kotlin
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.PropertiesMapper

val mapper = PropertiesMapper(
    PropertiesConfig.builder()
        .pathSeparator("->")
        .prefix("app")
        .build(),
)
val text = mapper.format(mapOf("server" to mapOf("host" to "localhost")))
println(text)
// app->server->host=localhost

val restored = mapper.parse(text, Map::class.java)
check(restored["server"] != null)
```

### 5. 百分号转义

```kotlin
val node = mapper.parse("""
    a.b%2Ec=1
    a%5B0%5D=2
""".trimIndent(), Node::class.java)

println(node.asObject()["a"]?.asObject()?.get("b.c")?.asNumber()?.toInt())  // 1
println(node.asObject()["a[0]"]?.asNumber()?.toInt())                        // 2
```

### 6. 根为数组

```kotlin
val node = mapper.parse("""
    1.name=小于
    2.name=阿明
""".trimIndent(), Node::class.java)

check(node.isArray)
println(node.asArray().size)   // 2
```

### 7. 写出格式（头部、缩进、自定义键值分隔符）

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.toPropertiesString

val config = PropertiesConfig.builder()
    .header("# 生成配置\n")
    .lineIndentation("  ")
    .keyValueSeparator(": ")
    .build()
val text = ObjectNode.of("user" to ObjectNode.of("name" to StringNode.of("小于"))).toPropertiesString(config)
check(text == "# 生成配置\n  user.name: 小于\n")
```

### 8. null 与二进制不保型

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode

val node = ObjectNode.of(
    "empty" to NullNode.INSTANCE,
    "data" to BinaryNode.of(byteArrayOf(1, 2, 3)),
)
val text = mapper.format(node)      // empty= / data=AQID（Base64）
val restored = mapper.parse(text, Node::class.java)
check(restored.asObject()["empty"]?.isString == true)   // 读回空字符串
check(restored.asObject()["data"]?.isString == true)    // 读回 Base64 字符串
```

### 9. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.properties"))
val fromFile = mapper.parse(File("build/user.properties"), User::class.java)

mapper.format(user, Paths.get("build/user2.properties"))
val fromPath = mapper.parse(Paths.get("build/user2.properties"), User::class.java)
```

### 10. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(user, output)
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 11. 字节数组读写

```kotlin
val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
check(restored.age == 18)
```

### 12. Java 示例

```java
import com.mingliqiye.document.format.properties.PropertiesMapper;

public class PropertiesDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        PropertiesMapper mapper = new PropertiesMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        String text = mapper.format(user);
        User restored = mapper.parse(text, User.class);
        System.out.println(restored.name + ":" + restored.age);
    }
}
```

### 13. 自定义字符集

```kotlin
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.PropertiesMapper
import java.nio.charset.Charset

val gbkMapper = PropertiesMapper(
    PropertiesConfig.builder()
        .fileCharset(Charset.forName("GBK"))
        .build(),
)

val bytes = gbkMapper.formatBytes(user)               // GBK 编码
val restored = gbkMapper.parse(bytes, User::class.java)
```

## 与通用 `DocumentMapper` 的差异

- 格式选项集中在 `PropertiesConfig`（继承 `DocumentMapperConfig`）；`PropertiesReader` /
  `PropertiesWriter`（内部）/ `PropertiesTokenReader`（内部）都可接收同一配置；
- `parse(String)` 走原生 `PropertiesTokenReader`（不构建 Node 树），Token 生成时同时执行 Node 限制校验；
- `formatPretty` 与 `format` 输出一致（Properties 没有格式化概念）；
- Properties 专属公开类型：`PropertiesConfig`、`PropertiesConfigBuilder`、`PropertiesIndexMarker`、
  `PropertiesReadFeature`、`PropertiesWriteFeature`、`PropertiesReader`、`String.toPropertiesNode(...)`、
  `Node.toPropertiesString(...)`；
- 文本格式语义：`parse(ByteArray)` / `formatBytes` 按 `fileCharset` 编解码。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [Token 与流式读写](../guide/token-stream.md)
- [API 参考](../reference/api.md)
- [format-properties 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-properties/README.md)
