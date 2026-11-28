# format-json

JSON / JSONC 格式模块，行为对齐 参考实现 2.15.3 的 JSON 读写配置。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:json:2.5.1")
```

## 主要 API

```text
JsonMapper / JsonMapperBuilder
JsonReader
JsonConfig / JsonConfigBuilder
JsonReadFeature / JsonWriteFeature
DuplicateKeyPolicy
Node.toJsonString(pretty, writeFeatures)
JsonMapper.parseLines / formatLines
JsonMapper.parseArray / formatArray
JsonSchema
```

## 快速开始

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = JsonMapper()
val user = mapper.parse("""{"name":"小于","age":18}""", User::class.java)
val text = mapper.format(user)              // 紧凑 JSON（字段顺序由 Bean 属性元数据决定）
val bytes = mapper.formatBytes(user)        // UTF-8 字节
val restored = mapper.parse(bytes, User::class.java)
```

## 配置

`JsonConfig.builder()` 返回 `JsonConfigBuilder`，可配置 JSON 专属选项、读写特性与全部
通用 `DocumentMapperConfig` 选项。

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.JsonReadFeature
import com.mingliqiye.document.format.json.JsonWriteFeature

val mapper = JsonMapper(
    JsonConfig.builder()
        .allowComments(true)                    // JSONC 注释（默认开启）
        .pretty(false)                          // format() 是否默认格式化
        .duplicateKeyPolicy(DuplicateKeyPolicy.LAST_WINS)   // 重复 key 策略
        .maxDepth(512)                          // 对象/数组最大嵌套深度
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
        .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE)
        .build(),
)
```

### JSON 专属字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `allowComments` | `Boolean` | `true` | 是否允许 JSONC 注释（联动 `ALLOW_JAVA_COMMENTS`） |
| `pretty` | `Boolean` | `false` | `format()` 是否默认输出格式化 JSON |
| `duplicateKeyPolicy` | `DuplicateKeyPolicy` | `LAST_WINS` | 重复 key 策略（`FAIL` 联动 `STRICT_DUPLICATE_DETECTION`） |
| `maxDepth` | `Int` | `512` | 对象/数组最大嵌套深度（必须为正数） |
| `readFeatures` | `Set<JsonReadFeature>` | 空集合 | 显式开启的读取特性 |
| `writeFeatures` | `Set<JsonWriteFeature>` | `QUOTE_FIELD_NAMES`、`WRITE_NAN_AS_STRINGS`、`WRITE_HEX_UPPER_CASE` | 显式开启的写出特性 |

默认值与 参考实现 2.15.3 一致，唯一例外是 `ALLOW_JAVA_COMMENTS`：本项目为兼容 JSONC
历史行为默认开启（参考实现 默认关闭），可用 `allowComments(false)` 或
`disable(JsonReadFeature.ALLOW_JAVA_COMMENTS)` 恢复严格 JSON。

### 读取特性（对应 参考实现 `JsonReadFeature` / `StreamReadFeature`）

| 特性 | 参考实现 旧名 | 默认 | 行为 |
|---|---|---|---|
| `ALLOW_JAVA_COMMENTS` | `ALLOW_COMMENTS` | **开** | `//` 行注释与块注释 |
| `ALLOW_YAML_COMMENTS` | `ALLOW_YAML_COMMENTS` | 关 | `#` 行注释 |
| `ALLOW_SINGLE_QUOTES` | `ALLOW_SINGLE_QUOTES` | 关 | 字符串/字段名可用单引号 |
| `ALLOW_UNQUOTED_FIELD_NAMES` | `ALLOW_UNQUOTED_FIELD_NAMES` | 关 | 字段名可不加引号 |
| `ALLOW_UNESCAPED_CONTROL_CHARS` | `ALLOW_UNQUOTED_CONTROL_CHARS` | 关 | 字符串允许未转义控制字符 |
| `ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER` | 同名 | 关 | 未知转义按字面字符处理 |
| `ALLOW_LEADING_ZEROS_FOR_NUMBERS` | `ALLOW_NUMERIC_LEADING_ZEROS` | 关 | 允许 `0123` |
| `ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` | 同名 | 关 | 允许 `+1` |
| `ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS` | 同名 | 关 | 允许 `.5` |
| `ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS` | 同名 | 关 | 允许 `1.` |
| `ALLOW_NON_NUMERIC_NUMBERS` | 同名 | 关 | 允许 `NaN` / `Infinity` / `-Infinity` |
| `ALLOW_MISSING_VALUES` | 同名 | 关 | 允许 `[1,,2]` 等缺失值（读作 `null`） |
| `ALLOW_TRAILING_COMMA` | 同名 | 关 | 允许 `[1,]`、`{"a":1,}` |
| `STRICT_DUPLICATE_DETECTION` | 同名（`StreamReadFeature`） | 关 | 重复 key 抛 `JsonParseException`（等价 `duplicateKeyPolicy(FAIL)`） |

### 写出特性（对应 参考实现 `JsonWriteFeature` / `JsonGenerator.Feature` / `StreamWriteFeature`）

| 特性 | 参考实现 旧名 | 默认 | 行为 |
|---|---|---|---|
| `QUOTE_FIELD_NAMES` | 同名 | 开 | 关闭后字段名原样输出（非标准 JSON） |
| `WRITE_NAN_AS_STRINGS` | `QUOTE_NON_NUMERIC_NUMBERS` | 开 | `NaN` / `Infinity` 写成字符串；关闭后写裸 `NaN` |
| `WRITE_NUMBERS_AS_STRINGS` | 同名 | 关 | 所有数字写成 JSON 字符串 |
| `WRITE_BIGDECIMAL_AS_PLAIN` | 同名（`StreamWriteFeature`） | 关 | Node 中 `1E+5` 等指数写法输出为 `100000` |
| `ESCAPE_NON_ASCII` | 同名 | 关 | 7 位 ASCII 之外字符写成 `\uXXXX` |
| `WRITE_HEX_UPPER_CASE` | 同名 | 开 | `\uXXXX` 十六进制字母大写 |

### Builder 方法

| 分类 | 方法 |
|---|---|
| 字段/策略 | `field(...)`、`missingFieldPolicy(...)`、`failOnUnknownFields(...)`、`writeNulls(...)` |
| JSON 专属 | `allowComments(...)`、`pretty(...)`、`duplicateKeyPolicy(...)`、`maxDepth(...)` |
| 读取特性 | `enable(JsonReadFeature)`、`disable(...)`、`configure(feature, enabled)` |
| 写出特性 | `enable(JsonWriteFeature)`、`disable(...)`、`configure(feature, enabled)` |
| 通用反序列化特性 | `enable/disable/configure(DeserializationFeature)`、`failOnNullForPrimitives(...)` |
| 多态/引用 | `subtype(...)`、`typeProperty(...)`、`resolveReferences(...)`、`writeReferences(...)`、`remoteReferenceResolver(...)` |
| 通用读写 | `namingStrategy(...)`、`fileCharset(...)`、`atomicFileWrite(...)`、`createParentDirectories(...)`、`maxInputSize(...)`、`maxStringLength(...)`、`maxNodeDepth(...)`、`maxNodeCount(...)`、`beanValidator(...)` |
| 构建 | `build()` |

独立读取器 `JsonReader` 也支持读取特性：

```kotlin
import com.mingliqiye.document.format.json.JsonReadFeature
import com.mingliqiye.document.format.json.JsonReader

val node = JsonReader(
    source = "{'a':1,}",
    readFeatures = setOf(JsonReadFeature.ALLOW_SINGLE_QUOTES, JsonReadFeature.ALLOW_TRAILING_COMMA),
).read()
```

## 流式 API（大文件）

`parseLines` / `formatLines`（JSON Lines / NDJSON）与 `parseArray` / `formatArray`（顶层数组）
逐条处理，任意时刻内存中只有当前行/元素。读取特性同样生效。

```kotlin
import java.io.FileInputStream
import java.io.FileOutputStream

FileInputStream("users.ndjson").use { input ->
    mapper.parseLines(input, User::class.java) { user ->
        // 逐条处理
    }
}
FileOutputStream("users.ndjson").use { output ->
    mapper.formatLines(users, output)
}

// 顶层数组
FileInputStream("users.json").use { input ->
    mapper.parseArray(input, User::class.java) { user -> }
}
```

语义：空行跳过、首行 BOM 忽略；流只读取/flush，不关闭调用方流；`maxInputSize`
按累计已读字节数/字符数校验；`formatLines` / `formatArray` 始终紧凑输出。

## JSON Schema

```kotlin
import com.mingliqiye.document.format.json.schema.JsonSchema

val schema = JsonSchema.compile(
    """{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""",
)
val node = mapper.parse("""{"name":"小于"}""", com.mingliqiye.document.core.Node::class.java)
schema.validateOrThrow(node)
```

支持 draft-07 常用子集（type/enum/const、properties/required、items、数字/字符串约束、
allOf/anyOf/oneOf/not、if/then/else、contains、propertyNames、format、本地 `$ref`）；
不支持远程 `$ref`、`unevaluatedProperties` 等高级关键字。

## 与 参考实现 的差异与不支持项

完整映射表见 [docs/formats/json.md](../../docs/formats/json.md#参考实现-2153-配置对齐)。

- `ALLOW_JAVA_COMMENTS` 默认开启（本项目 JSONC 历史行为，参考实现 默认关闭）；
- `JsonGenerator.Feature.AUTO_CLOSE_TARGET` 未暴露：`format(value, OutputStream/Writer)`
  只 flush 不关闭调用方目标；
- `JsonParser.Feature.AUTO_CLOSE_SOURCE` 未暴露：解析 API 不关闭调用方输入流；
- generator 侧 `STRICT_DUPLICATE_DETECTION`、`IGNORE_UNKNOWN`、`IGNORE_UNDEFINED`、
  `INCLUDE_SOURCE_IN_LOCATION`、`USE_FAST_*` 属 databind/性能提示项，本模块不适用；
- `ALLOW_NON_NUMERIC_NUMBERS` 在 Node 树中以 `raw` 保留 `NaN` / `Infinity` /
  `-Infinity`，`NumberNode.value`（`BigDecimal`）无法表示非有限值，需要数值时用
  `asNumber().toDouble()`；
- `WRITE_NAN_AS_STRINGS` 的 Bean 流式写出路径可用；`toNode` 树路径受
  `NumberNode` 只能表示有限 `BigDecimal` 限制；
- `WRITE_BIGDECIMAL_AS_PLAIN` 对 Bean 的 `BigDecimal` 恒为普通表示（serialization
  层即使用 `toPlainString()`），对 Node 中带指数写法的 `raw` 按特性转换。

## 构建

```bash
gradle :format-json:test
```

文档：根 [README](../README.md) · [docs/formats/json.md](../../docs/formats/json.md)
