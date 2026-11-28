# format-properties

Java Properties 格式模块。

配置对齐 参考实现 `Properties 底层库`（2.15）的 `JavaPropsSchema`：
分隔符、下标标记、数组起始编号、前缀、键值分隔符、行结尾/缩进、头部注释均有对应配置，
默认值一致；另提供 `PropertiesReadFeature` / `PropertiesWriteFeature` 风格开关与
`enable` / `disable` / `configure` API。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:properties:2.5.1")
```

## 主要 API

```text
PropertiesMapper
PropertiesConfig / PropertiesConfigBuilder
PropertiesIndexMarker
PropertiesReadFeature / PropertiesWriteFeature
PropertiesReader
```

## 示例

```kotlin
import com.mingliqiye.document.format.properties.PropertiesMapper

val mapper = PropertiesMapper()
val text = """
    name=小于
    age=18
""".trimIndent()

val user = mapper.parse(text, User::class.java)
val output = mapper.format(user)   // age=18\nname=小于\n
```

## 路径规则

```text
user.name=小于
user.tags[0]=a     # 下标标记
server.1.host=x    # 简单数字下标（参考实现 默认）
```

表示：

```json
{
  "user": {
    "name": "小于",
    "tags": ["a"]
  },
  "server": [{ "host": "x" }]
}
```

写出默认使用简单下标（起始 `1`）：

```kotlin
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.properties.toPropertiesString

val text = ObjectNode.of(
    "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
).toPropertiesString()
// tags.1=a
// tags.2=b
```

## 参考实现 对齐配置

`PropertiesConfig`（继承 `DocumentMapperConfig`）字段与 参考实现 `JavaPropsSchema` 对应：

| 参考实现 `JavaPropsSchema` | 默认值 | 本模块配置 |
|---|---|---|
| `firstArrayOffset()` | `1` | `firstArrayOffset` |
| `pathSeparator()` | `"."` | `pathSeparator`（支持多字符、空串） |
| `pathSeparatorEscapeChar()` | `'\0'` | `pathSeparatorEscapeChar` |
| `indexMarker()` | `[` `]` | `indexMarker` / `withoutIndexMarker()` |
| `parseSimpleIndexes()` | `true` | `PropertiesReadFeature.PARSE_SIMPLE_INDEXES` |
| `writeIndexUsingMarkers()` | `false` | `PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS` |
| `lineIndentation()` | `""` | `lineIndentation` |
| `keyValueSeparator()` | `"="` | `keyValueSeparator` |
| `lineEnding()` | `"\n"` | `lineEnding` |
| `header()` | `""` | `header` |
| `prefix()` | `null` | `prefix` |

说明：参考实现 2.15 的 Properties 模块并没有 `PropertiesReadFeature` / `PropertiesWriteFeature`
枚举（格式配置集中在 `JavaPropsSchema`）；本模块的两个特性枚举是 `JavaPropsSchema` 布尔
选项 + 本模块兼容扩展的等价封装，默认值与 参考实现 一致。

### 读取特性 `PropertiesReadFeature`

| 特性 | 默认 | 说明 |
|---|---|---|
| `PARSE_SIMPLE_INDEXES` | 开 | 纯数字路径段（1~9 位）解析为数组下标 |
| `ALLOW_INDEX_MARKERS` | 开 | 识别 `indexMarker`（默认 `[index]`） |
| `DECODE_PERCENT_ESCAPED_SEGMENTS` | 开 | 解码 `%25`/`%2E`/`%5B`/`%5D`（本模块扩展，参考实现 无） |

### 写出特性 `PropertiesWriteFeature`

| 特性 | 默认 | 说明 |
|---|---|---|
| `WRITE_INDEX_USING_MARKERS` | 关 | 数组写作 `tags[1]`；默认写作简单下标 `tags.1` |
| `ESCAPE_PERCENT_ENCODED_SEGMENTS` | 开 | 字段名中的 `%` `.` `[` `]` 百分号编码（本模块扩展） |
| `ESCAPE_NON_ASCII` | 关 | 非 ASCII 写成 `\uXXXX`（与 参考实现 Latin-1 字面输出一致） |

## 配置示例

```kotlin
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.PropertiesMapper
import com.mingliqiye.document.format.properties.PropertiesWriteFeature

val config = PropertiesConfig.builder()
    .pathSeparator("->")                 // 自定义路径分隔符
    .prefix("app")                      // 读写前缀
    .writeIndexUsingMarkers(true)        // 数组写成 tags[1]
    .header("# generated\n")             // 头部注释
    .lineIndentation("  ")              // 每行缩进
    .enable(PropertiesWriteFeature.ESCAPE_NON_ASCII)
    .maxDepth(128)                       // 解析深度安全限制
    .build()
val mapper = PropertiesMapper(config)
```

Builder 入口：

```kotlin
val mapper = PropertiesMapper.builder()
    .config(config)
    .maxDepth(64)                        // 显式设置时优先于 config.maxDepth
    .build()
```

通用 `DocumentMapperConfig` 便捷方法也可直接在 `PropertiesConfigBuilder` 上调用：
`missingFieldPolicy`、`failOnUnknownFields`、`writeNulls`、`namingStrategy`、`fileCharset`、
`atomicFileWrite`、`createParentDirectories`、`maxInputSize`、`maxStringLength`、`maxNodeDepth`、
`maxNodeCount`、`resolveReferences`、`writeReferences`、`remoteReferenceResolver`、`beanValidator`、
`enable/disable/configure(DeserializationFeature)`、`failOnNullForPrimitives`、`subtype`、`typeProperty`。

## 深度限制（maxDepth）

key 路径令牌数决定对象/数组嵌套深度（根对象深度为 `1`，`a.b.c=1` 最深层级为 `4`）。
默认安全上限为 512，避免恶意超深路径导致 StackOverflow：

```kotlin
import com.mingliqiye.document.format.properties.PropertiesConfig
import com.mingliqiye.document.format.properties.PropertiesMapper

// 通过 PropertiesConfig
val mapper = PropertiesMapper(PropertiesConfig.builder().maxDepth(64).build())

// 通过 MapperBuilder（默认 PropertiesMapper.DEFAULT_MAX_DEPTH = 512）
val mapper2 = PropertiesMapper.builder().maxDepth(64).build()

// 通过旧式构造器
val mapper3 = PropertiesMapper(
    com.mingliqiye.document.serialization.DocumentMapperConfig.empty(),
    maxDepth = 64,
)

// 独立读取器
val node = com.mingliqiye.document.format.properties.PropertiesReader(source, maxDepth = 64).read()
```

- 超限时 Node / Token 路径都抛出
  `com.mingliqiye.document.format.properties.exception.PropertiesParseException`；
- 通用 `DocumentMapperConfig.maxNodeDepth` 保持 Node 限制语义：超限抛
  `com.mingliqiye.document.serialization.exception.DocumentSerializationException`；
- `PropertiesReader` / `PropertiesTokenReader` 的 `maxDepth` 显式传 `0` 或负数表示关闭保护。

## 特性

- `.` 对象路径（分隔符可配置，支持多字符/空串）
- `[index]` 下标标记 + 简单数字下标 `tags.1`（参考实现 默认两种都可读）
- 数组写出默认简单下标、起始编号可配置，可切换 `[index]` 标记
- 前缀、键值分隔符、行结尾、行缩进、头部注释
- 稠密数组、值与子节点合并、下标/名字混用降级（参考实现 语义）
- 百分号编码扩展，保证含 `.`/`[]` 的字段名可往返
- 扁平文本与 Node 树互转
- 文件/流读取与保存（`fileCharset`）
- Token 直读 Bean
- 原生 Properties Token Reader
- 解析深度限制（默认 512，可配置）

## 与 参考实现 的主要差异

- 字节编码：参考实现 固定 ISO-8859-1；本模块按 `fileCharset`（默认 UTF-8）；
- 非 ASCII 输出：参考实现 固定 `\uXXXX`；本模块默认原样输出，可开启 `ESCAPE_NON_ASCII`；
- 写出根：参考实现 允许任意根；本模块要求对象/数组；
- 空输入：本模块返回空对象；参考实现 parser 不产生 Token；
- 读取顺序：本模块按 key 字典序排序（确定性）；参考实现 使用 Properties 迭代顺序（未定义）；
- 未支持：参考实现 流式 `JsonParser`/`JsonGenerator`、`writeValueAsProperties/Map`、
  预解析 `Map` 输入、按操作设置 `FormatSchema`（本模块通过构造 Mapper 时的 `PropertiesConfig` 配置）。

详细配置矩阵、路径/转义规则与示例见 [docs/formats/properties.md](../docs/formats/properties.md)。

## 构建

```bash
gradle :format-properties:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
