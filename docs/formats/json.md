---
title: JSON / JSONC
description: format-json 模块：JsonMapper、JsonConfig、参考实现 2.15.3 读/写特性、重复 key 策略、深度限制与 JSON Schema 校验
---

# JSON / JSONC

`format-json` 提供 JSON 与 JSONC（带注释 JSON）的读写能力，核心类型是 `JsonMapper`。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:json:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `json` |
| 版本 | `2.5.1`（由根 `gradle.properties` 的 `projectVersion` 决定） |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `第三方底层库坐标.core:JSON Token 底层库:2.15.3` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.json` |

## 特性

- **JSONC 注释**：默认允许 `//` 行注释与 `/* ... */` 块注释，可关闭恢复严格 JSON；
- **严格 UTF-8 字节 API**：`parse(ByteArray)` 用 `REPORT` 模式解码，非法 UTF-8（截断、孤立续字节、过长编码）抛 `JsonParseException`，不会静默替换为 `U+FFFD`；
- **重复 key 策略**：`LAST_WINS` / `FIRST_WINS` / `FAIL`，树、Token、Bean 三条路径保持一致；
- **解析深度限制**：对象与数组统一计数，默认 512；
- **Token 直读**：`parse(String)` / `parse(ByteArray)` 走内部 `JsonTokenReader`，直接把 Token 解码为 Bean，不构建完整 Node 树；
- **紧凑字节写出**：`formatBytes` 通过 `JsonUtf8StreamWriter` 直接输出 UTF-8 字节，不经过中间 `String`；
- **Double 最短表示**：紧凑/格式化写出使用 JDK `Double.toString` 输出最短可往返的十进制文本；
- **JSON Schema**：内置常用 draft-07 子集校验器；
- **文件/流读写**：`File`、`Path`、`InputStream`、`Reader`、`OutputStream`、`Writer` 全覆盖；
- **流式写出到 `OutputStream`**：`format(value, outputStream)` 直接边遍历边写；
- **参考实现 2.15.3 配置对齐**：`JsonReadFeature` / `JsonWriteFeature` 全量实现，名称、默认值与 legacy 映射见下文「参考实现 2.15.3 配置对齐」。

## 配置：`JsonConfig`

`JsonConfig` 继承 `DocumentMapperConfig`，在通用配置上增加 JSON 专属选项。

### 专属字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `allowComments` | `Boolean` | `true` | 是否允许 JSONC 注释 |
| `pretty` | `Boolean` | `false` | `format()` 是否默认输出格式化 JSON |
| `duplicateKeyPolicy` | `DuplicateKeyPolicy` | `LAST_WINS` | 同一对象内重复 key 的处理策略 |
| `maxDepth` | `Int` | `512`（`JsonConfig.DEFAULT_MAX_DEPTH`） | 对象/数组最大嵌套深度，必须为正数 |
| `readFeatures` | `Set<JsonReadFeature>` | 空集合（`JsonConfig.DEFAULT_READ_FEATURES`） | 显式开启的 JSON 读取特性 |
| `writeFeatures` | `Set<JsonWriteFeature>` | `QUOTE_FIELD_NAMES`、`WRITE_NAN_AS_STRINGS`、`WRITE_HEX_UPPER_CASE`（`JsonConfig.DEFAULT_WRITE_FEATURES`） | 显式开启的 JSON 写出特性 |

`readFeatures` / `writeFeatures` 是「显式开启」集合，默认值与 参考实现 2.15.3 一致：
读取特性全部关闭，写出特性中 `QUOTE_FIELD_NAMES`、`WRITE_NAN_AS_STRINGS`、
`WRITE_HEX_UPPER_CASE` 默认开启。唯一默认值差异是 `ALLOW_JAVA_COMMENTS`：本项目为兼容
JSONC 历史行为默认开启（参考实现 默认关闭），可用 `allowComments(false)` 或
`disable(JsonReadFeature.ALLOW_JAVA_COMMENTS)` 恢复严格 JSON。`allowComments` 与
`ALLOW_JAVA_COMMENTS`、`duplicateKeyPolicy(FAIL)` 与 `STRICT_DUPLICATE_DETECTION`
分别是同一个开关（最后一次配置生效）。

`DuplicateKeyPolicy` 取值：

| 值 | 行为 |
|---|---|
| `LAST_WINS` | 后出现的值覆盖先出现的值（默认，历史兼容） |
| `FIRST_WINS` | 保留先出现的值，忽略后续重复 key 的值 |
| `FAIL` | 遇到重复 key 立即抛 `JsonParseException` |

### Builder 方法

`JsonConfig.builder()` 返回 `JsonConfigBuilder`：

| 方法 | 说明 |
|---|---|
| `field(Class, String, DocumentBaseConverter)` | 为指定字段绑定转换器 |
| `missingFieldPolicy(MissingFieldPolicy)` | 缺字段策略 |
| `failOnUnknownFields(Boolean)` | 未知字段是否报错 |
| `writeNulls(Boolean)` | null 字段是否写出（对应 `JsonInclude.ALWAYS/NON_NULL` 全局开关） |
| `allowComments(Boolean)` | 是否允许注释（等价 `ALLOW_JAVA_COMMENTS`） |
| `pretty(Boolean)` | 默认 pretty 输出（对应 `INDENT_OUTPUT`） |
| `duplicateKeyPolicy(DuplicateKeyPolicy)` | 重复 key 策略（`FAIL` 等价 `STRICT_DUPLICATE_DETECTION`） |
| `maxDepth(Int)` | 最大嵌套深度（必须 > 0） |
| `enable(JsonReadFeature)` / `disable(...)` / `configure(feature, enabled)` | JSON 读取特性开关 |
| `enable(JsonWriteFeature)` / `disable(...)` / `configure(feature, enabled)` | JSON 写出特性开关 |
| `enable(DeserializationFeature)` / `disable(...)` / `configure(feature, enabled)` | 通用反序列化特性开关 |
| `failOnNullForPrimitives(Boolean)` | 便捷设置 `FAIL_ON_NULL_FOR_PRIMITIVES` |
| `subtype(base, typeName, subClass)` / `subtype(base, typeName, subClass, typeProperty)` | 注册多态子类型 |
| `typeProperty(base, typeProperty)` | 覆盖多态类型属性名 |
| `resolveReferences(Boolean)` / `writeReferences(Boolean)` | 本地 `$ref` 解析/写出 |
| `remoteReferenceResolver(RemoteReferenceResolver?)` | 远程 `$ref` 解析器 |
| `namingStrategy(PropertyNamingStrategy)` | 字段命名策略 |
| `fileCharset(Charset)` | 文本文件/流读写字符集 |
| `atomicFileWrite(Boolean)` / `createParentDirectories(Boolean)` | 文件写出选项 |
| `maxInputSize(Long)` / `maxStringLength(Int)` / `maxNodeDepth(Int)` / `maxNodeCount(Long)` | 输入与 Node 树限制（0 表示不限制） |
| `beanValidator(DocumentBeanValidator?)` | 根对象校验器 |
| `build()` | 构建 `JsonConfig` |

> `JsonConfigBuilder` 转发全部通用 `DocumentMapperConfig` 选项，始终使用它也不会丢失
> `namingStrategy`、`fileCharset`、`maxInputSize` 等配置；也可以直接把
> `DocumentMapperConfig` 传给 `JsonMapper`，此时 JSON 专属选项回退默认值。

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.JsonReadFeature
import com.mingliqiye.document.format.json.JsonWriteFeature

val mapper = JsonMapper(
    JsonConfig.builder()
        .allowComments(false)                 // 严格 JSON
        .pretty(true)                         // format() 默认 pretty
        .duplicateKeyPolicy(DuplicateKeyPolicy.FAIL)
        .maxDepth(256)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
        .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE)
        .build(),
)
```

独立读取器 `JsonReader` 同样支持读取特性：

```kotlin
import com.mingliqiye.document.format.json.JsonReadFeature
import com.mingliqiye.document.format.json.JsonReader

val node = JsonReader(
    source = "{'a':1,}",
    readFeatures = setOf(JsonReadFeature.ALLOW_SINGLE_QUOTES, JsonReadFeature.ALLOW_TRAILING_COMMA),
).read()
```

## 参考实现 2.15.3 配置对齐

格式级配置以 参考实现 2.15.3（`JSON Token 底层库` 2.15.3）为准：读取特性对应
`第三方底层库坐标.core.json.JsonReadFeature` 与
`第三方底层库坐标.core.StreamReadFeature` 中与 JSON 语法相关的项；写出特性对应
`JsonWriteFeature`、`JsonGenerator.Feature` 与 `StreamWriteFeature`。每个枚举常量通过
`legacyName` 属性暴露对应的 参考实现 旧版特性名称，便于从 参考实现 配置迁移。

### 读取特性（`JsonReadFeature`）

| 本项目特性 | 参考实现 旧名 | 默认 | 行为 |
|---|---|---|---|
| `ALLOW_JAVA_COMMENTS` | `JsonParser.Feature.ALLOW_COMMENTS` | **开启**（本项目 JSONC 兼容） | `//` 行注释与块注释 |
| `ALLOW_YAML_COMMENTS` | `ALLOW_YAML_COMMENTS` | 关闭 | `#` 行注释 |
| `ALLOW_SINGLE_QUOTES` | `ALLOW_SINGLE_QUOTES` | 关闭 | 字符串/字段名可用单引号 |
| `ALLOW_UNQUOTED_FIELD_NAMES` | `ALLOW_UNQUOTED_FIELD_NAMES` | 关闭 | 字段名可不加引号 |
| `ALLOW_UNESCAPED_CONTROL_CHARS` | `ALLOW_UNQUOTED_CONTROL_CHARS` | 关闭 | 字符串允许未转义控制字符 |
| `ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER` | `ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER` | 关闭 | 未知转义按字面字符处理 |
| `ALLOW_LEADING_ZEROS_FOR_NUMBERS` | `ALLOW_NUMERIC_LEADING_ZEROS` | 关闭 | 允许 `0123` |
| `ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` | `ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` | 关闭 | 允许 `+1` |
| `ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS` | `ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS` | 关闭 | 允许 `.5` |
| `ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS` | `ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS` | 关闭 | 允许 `1.` |
| `ALLOW_NON_NUMERIC_NUMBERS` | `ALLOW_NON_NUMERIC_NUMBERS` | 关闭 | 允许 `NaN` / `Infinity` / `-Infinity`（含 `+INF` / `-INF` / `+Infinity` 写法） |
| `ALLOW_MISSING_VALUES` | `ALLOW_MISSING_VALUES` | 关闭 | 允许 `[1,,2]`、`[,1]`、`[1,]`，缺失槽读作 `null` |
| `ALLOW_TRAILING_COMMA` | `ALLOW_TRAILING_COMMA` | 关闭 | 允许 `[1,]`、`{"a":1,}` |
| `STRICT_DUPLICATE_DETECTION` | `StreamReadFeature.STRICT_DUPLICATE_DETECTION` | 关闭 | 重复 key 抛 `JsonParseException`，等价 `duplicateKeyPolicy(FAIL)` |

补充语义（与 参考实现 探针结果一致）：

- `ALLOW_MISSING_VALUES`：`[1,,2]` → `[1,null,2]`，`[,1]` → `[null,1]`，`[1,]` → `[1,null]`，
  `[,]` → `[null,null]`，`[,,]` → `[null,null,null]`；同时开启 `ALLOW_TRAILING_COMMA`
  时尾逗号优先，`[1,]` → `[1]`；
- `ALLOW_NON_NUMERIC_NUMBERS`：Node 树中保留规范化 `raw`（`NaN` / `Infinity` /
  `-Infinity`），`NumberNode.value`（`BigDecimal`）无法表示非有限值，需要数值时使用
  `asNumber().toDouble()`；写出时由 `WRITE_NAN_AS_STRINGS` 决定是否加引号。

### 写出特性（`JsonWriteFeature`）

| 本项目特性 | 参考实现 旧名 | 默认 | 行为 |
|---|---|---|---|
| `QUOTE_FIELD_NAMES` | `JsonGenerator.Feature.QUOTE_FIELD_NAMES` | 开启 | 关闭后字段名原样输出（非标准 JSON，与 参考实现 一致） |
| `WRITE_NAN_AS_STRINGS` | `QUOTE_NON_NUMERIC_NUMBERS` | 开启 | `NaN` / `Infinity` / `-Infinity` 写成 JSON 字符串；关闭后写裸文本 |
| `WRITE_NUMBERS_AS_STRINGS` | `WRITE_NUMBERS_AS_STRINGS` | 关闭 | 所有数字写成 JSON 字符串 |
| `WRITE_BIGDECIMAL_AS_PLAIN` | `WRITE_BIGDECIMAL_AS_PLAIN`（`StreamWriteFeature`） | 关闭 | Node 中带指数写法的 `raw`（如 `1E+5`）输出为普通十进制（`100000`） |
| `ESCAPE_NON_ASCII` | `ESCAPE_NON_ASCII` | 关闭 | 7 位 ASCII 之外字符写成 `\uXXXX`，代理对写成两个转义 |
| `WRITE_HEX_UPPER_CASE` | `WRITE_HEX_UPPER_CASE` | 开启 | `\uXXXX` 中十六进制字母大写 |

### `JsonParser.Feature` / `StreamReadFeature` 全量覆盖

| 参考实现 特性 | 状态 | 说明 |
|---|---|---|
| `AUTO_CLOSE_SOURCE` | 未作为特性暴露 | 解析 API 不关闭调用方输入流（IO 所有权归调用方） |
| `ALLOW_COMMENTS` | 已实现 | → `ALLOW_JAVA_COMMENTS`（默认开启，见上文差异） |
| `ALLOW_YAML_COMMENTS` | 已实现 | → `ALLOW_YAML_COMMENTS` |
| `ALLOW_UNQUOTED_FIELD_NAMES` | 已实现 | → 同名特性 |
| `ALLOW_SINGLE_QUOTES` | 已实现 | → 同名特性 |
| `ALLOW_UNQUOTED_CONTROL_CHARS` | 已实现 | → `ALLOW_UNESCAPED_CONTROL_CHARS` |
| `ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER` | 已实现 | → 同名特性 |
| `ALLOW_NUMERIC_LEADING_ZEROS` | 已实现 | → `ALLOW_LEADING_ZEROS_FOR_NUMBERS` |
| `ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` | 已实现 | → 同名特性 |
| `ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS` | 已实现 | → 同名特性 |
| `ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS` | 已实现 | → 同名特性 |
| `ALLOW_NON_NUMERIC_NUMBERS` | 已实现 | → 同名特性（Node 树以 `raw` 保留） |
| `ALLOW_MISSING_VALUES` | 已实现 | → 同名特性 |
| `ALLOW_TRAILING_COMMA` | 已实现 | → 同名特性 |
| `STRICT_DUPLICATE_DETECTION` | 已实现 | → 同名特性，等价 `duplicateKeyPolicy(FAIL)` |
| `IGNORE_UNDEFINED` | 不适用 | databind 层概念，JSON 文本没有 undefined token |
| `INCLUDE_SOURCE_IN_LOCATION` | 不适用 | `JsonParseException` 已包含行列位置，不保留源文本片段 |
| `USE_FAST_DOUBLE_PARSER` / `USE_FAST_BIG_NUMBER_PARSER` | 不适用 | 纯性能提示；本实现使用 JDK `Double.parseDouble` 与 `BigDecimal`，语义无差异 |

### `JsonGenerator.Feature` / `StreamWriteFeature` 全量覆盖

| 参考实现 特性 | 状态 | 说明 |
|---|---|---|
| `AUTO_CLOSE_TARGET` | 未作为特性暴露 | `format(value, OutputStream/Writer)` 只 flush，不关闭调用方目标（本模块既有约定） |
| `AUTO_CLOSE_JSON_CONTENT` | 不适用 | 写出器始终输出成对闭合的对象/数组，不存在遗留未闭合容器 |
| `FLUSH_PASSED_TO_STREAM` | 恒等价开启 | `flush()` 直接传递给底层输出流 |
| `QUOTE_FIELD_NAMES` | 已实现 | → 同名特性（默认开启） |
| `QUOTE_NON_NUMERIC_NUMBERS` | 已实现 | → `WRITE_NAN_AS_STRINGS`（默认开启） |
| `ESCAPE_NON_ASCII` | 已实现 | → 同名特性（默认关闭） |
| `WRITE_NUMBERS_AS_STRINGS` | 已实现 | → 同名特性（默认关闭） |
| `WRITE_BIGDECIMAL_AS_PLAIN` | 已实现 | → 同名特性（默认关闭） |
| `STRICT_DUPLICATE_DETECTION` | 不适用 | 本模块公开 API 不产生重复字段名（Node/Bean 字段唯一）；仅底层内部 Token 写出器可驱动，不对外暴露 |
| `IGNORE_UNKNOWN` | 不适用 | databind 层特性 |
| `USE_FAST_DOUBLE_WRITER` | 不适用 | 纯性能提示；本实现已使用 JDK `Double.toString` 最短往返表示 |
| `WRITE_HEX_UPPER_CASE` | 已实现 | → 同名特性（默认开启） |

### ObjectMapper 常用配置映射

| 参考实现 配置 | 本项目对应 | 状态 |
|---|---|---|
| `JsonInclude.Include.ALWAYS` | 默认 `writeNulls(true)` | 已实现 |
| `JsonInclude.Include.NON_NULL` | `writeNulls(false)` 或 `@DocumentInclude(NON_NULL)` | 已实现 |
| `JsonInclude.Include.NON_EMPTY` | `@DocumentInclude(NON_EMPTY)` | 已实现 |
| `JsonInclude.Include.USE_DEFAULTS` | `@DocumentInclude(USE_DEFAULTS)` | 已实现 |
| `JsonInclude.Include.NON_ABSENT` | 无专门策略 | 不支持：Node 无 `Optional`/引用缺席语义，可近似使用 `NON_NULL` |
| `JsonInclude.Include.NON_DEFAULT` / `CUSTOM` | 无 | 不支持：需 serialization 层扩展 |
| `SerializationFeature.INDENT_OUTPUT` | `JsonConfig.pretty(true)` | 已实现（参考实现 通过 PrettyPrinter 配置） |
| `SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN` | `JsonWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN` | 已实现（Bean 的 BigDecimal 在 serialization 层恒为普通表示） |
| `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` 等日期/枚举/排序项 | 由 serialization、转换器与注解层负责 | 不在 format-json 范围 |
| `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES` | `serialization.DeserializationFeature` 同名项 | 已实现（`JsonConfigBuilder.enable` 转发） |
| `DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY` | `duplicateKeyPolicy(FAIL)` / `STRICT_DUPLICATE_DETECTION` | 已实现 |
| `DeserializationFeature.FAIL_ON_TRAILING_TOKENS` | 无开关（所有解析入口恒拒绝 trailing content） | 等价恒开启 |
| `DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS` | 无开关（serialization 的 enum 只接受 JSON 字符串） | 等价恒开启，无法关闭 |
| `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` | `failOnUnknownFields(true)` | 已实现 |
| `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` 等数字目标策略 | Node 数字始终保留 `raw` + `BigDecimal`；Bean 数字目标由字段类型决定 | 部分等价：不支持 `Object` 泛型目标按 Long/BigInteger 的策略切换 |
| `ACCEPT_SINGLE_VALUE_AS_ARRAY` / `UNWRAP_SINGLE_VALUE_ARRAYS` / `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT` / `READ_UNKNOWN_ENUM_VALUES_*` | 无 | serialization 层项，当前未提供 |

## Mapper 构造与 Builder

### 构造器

| 构造器 | 说明 |
|---|---|
| `JsonMapper()` | 默认配置：允许注释、紧凑输出、`LAST_WINS`、深度 512 |
| `JsonMapper(config: DocumentMapperConfig)` | 通用配置；传 `JsonConfig` 时 JSON 专属选项生效 |

### Builder

`JsonMapper.builder()` 返回 `JsonMapperBuilder`（`DocumentMapperBuilder<JsonMapper>` 子类）：

| 方法 | 说明 |
|---|---|
| `addSerializer(DocumentBaseSerializer)` | 注册序列化器 |
| `addDeserializer(DocumentBaseDeserializer)` | 注册反序列化器 |
| `addConverter(DocumentBaseConverter)` | 注册双向转换器 |
| `addModule(DocumentModule)` / `addModules(vararg)` | 注册模块（如 `kotlinModule()`） |
| `config(DocumentMapperConfig)` | 设置配置（可传 `JsonConfig`） |
| `objectInstantiator(ObjectInstantiator?)` | 自定义对象实例化策略 |
| `build()` | 构建 `JsonMapper` |

```kotlin
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

val mapper = JsonMapper.builder()
    .config(JsonConfig.builder().pretty(true).build())
    .addModule(kotlinModule())   // 需要 serialization-kotlin 模块
    .build()
```

## 读取 API

### Mapper 入口

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType: Class<T>)` | 直接 Token 解码，不构建 Node 树 |
| `parse(source: String, targetType: DocumentTypeReference<T>)` | 泛型目标 |
| `parse(source: ByteArray, targetType)` | 严格 UTF-8 解码后 Token 解码；非法 UTF-8 抛 `JsonParseException` |
| `parse(file: File, targetType)` / `parse(path: Path, targetType)` | 文件按 `fileCharset`（默认 UTF-8）解码成文本后解析 |
| `parse(input: InputStream, targetType)` | 读尽输入流后按 `fileCharset` 解码 |
| `parse(reader: Reader, targetType)` | 直接读取字符流 |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 `DocumentTokenReader` → 实体 |

`Node.class` 及其子类型同样可以作为 `targetType`：`mapper.parse(json, Node::class.java)` 返回树。

### 独立读取器

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonReader

// 默认：允许注释、LAST_WINS、深度 512
val node = JsonReader("""{"name":"小于", // 行注释
  "age":18}""").read()

// 指定策略
val strict = JsonReader(
    source = """{"name":"小于"}""",
    allowComments = false,
    duplicateKeyPolicy = DuplicateKeyPolicy.FAIL,
    maxDepth = 128,
).read()

// 静态便捷方法
val node2 = JsonReader.parse("""{"a":1}""")
val node3 = JsonReader.parse("""{"a":1} // 注释""", allowComments = true)

// 扩展函数
val node4 = """{"a":1}""".toJsonNode()
```

| 类/函数 | 说明 |
|---|---|
| `JsonReader(source, allowComments = true, duplicateKeyPolicy = LAST_WINS, maxDepth = 512)` | 单文档读取器，`read(): Node` |
| `JsonReader.parse(source)` / `JsonReader.parse(source, allowComments)` | 静态解析为 Node |
| `String.toJsonNode()` | 字符串 → Node 的扩展函数 |

### Token 路径

`JsonMapper` 内部为 `parse(String)` / `parse(ByteArray)` 创建原生 `JsonTokenReader`（`internal`，不对外暴露）：

- 直接产生 `DocumentTokenType` Token 流，Bean 映射在 Token 上完成，不构建完整 Node 树；
- 解析深度由 JSON 专属的 `JsonConfig.maxDepth` 控制（默认 512），与通用 `maxNodeDepth` 相互独立；
- 读取特性（`JsonReadFeature`）在 Token 路径同样生效；`ALLOW_MISSING_VALUES` 会产出
  `NULL` token，`ALLOW_NON_NUMERIC_NUMBERS` 产出文本为 `NaN` / `Infinity` / `-Infinity`
  的 `NUMBER` token；
- 通用 Node 树限制（`maxNodeDepth` / `maxNodeCount` / `maxStringLength`）主要作用于 Node 路径（`fromNode` / `toNode`）以及各格式的 Node 回退路径；JSON 的文本 Token 快路径不执行这些 Node 树限制；
- 需要自定义 Token 来源时，实现 `DocumentTokenReader` 后调用 `DocumentMapper.fromTokens(...)`；也可以先用 `JsonReader` 构建 Node，再通过 `serialization` 模块的 `NodeTokenReader(node)` 接入。

### 输入限制与错误

- `maxInputSize` 同时限制字符串长度（字符数）与字节输入长度，超限抛 `DocumentSerializationException`；
- 空输入、多个顶层值、非法 token 抛 `JsonParseException`，异常信息包含行列位置；
- `allowComments = false` 时出现注释抛 `JsonParseException`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → 紧凑 JSON；配置 `pretty(true)` 时输出格式化 JSON |
| `formatPretty(value)` | 实体 → 两空格缩进格式化 JSON |
| `formatBytes(value)` | 实体 → UTF-8 字节；紧凑模式直接流式编码，`pretty(true)` 时先格式化再编码 |
| `format(value, file: File)` / `format(value, path: Path)` | 写文件（`format` 语义） |
| `formatPretty(value, file / path)` | 写 pretty 文件 |
| `format(value, output: OutputStream)` | 直接流式写到输出流（内部 `JsonUtf8StreamWriter`） |
| `formatPretty(value, output: OutputStream)` | 流式 pretty 输出 |
| `format(value, writer: Writer)` / `formatPretty(value, writer)` | 写字符流 |
| `Node.toJsonString(pretty: Boolean = false, writeFeatures: Set<JsonWriteFeature> = DEFAULT)` | 扩展函数：Node → JSON 文本，可指定写出特性 |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `formatBytes` | ✅ | 紧凑模式 `JsonUtf8StreamWriter`，不经过中间 `String` |
| `format(value, OutputStream)` | ✅ | 直接 Token/值写出，按需 flush |
| `format(value, File/Path)` | ❌ | 先编码为字节再写文件（文件写入本身支持原子替换/自动建目录） |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

```kotlin
import com.mingliqiye.document.format.json.toJsonString

val text = mapper.formatPretty(user)          // 两空格缩进
val bytes = mapper.formatBytes(user)          // UTF-8
val node = mapper.parse(text, com.mingliqiye.document.core.Node::class.java)
val nodeText = node.toJsonString(pretty = true)
```

> `Node.toJsonString` 由 `JsonWriter` 实现，数字按 `NumberNode.raw` 原样写出；Mapper 的 `format` 路径使用 `JsonStreamWriter`，`Double` 使用 JDK `Double.toString` 的最短可往返表示。两者输出都是合法 JSON。

## 类型映射

| Node | JSON 读取 | JSON 写出 |
|---|---|---|
| `ObjectNode` | JSON 对象，保持字段插入顺序；重复 key 按策略处理 | JSON 对象；key 按插入顺序；`null` 字段由 Bean 层 `writeNulls` 控制 |
| `ArrayNode` | JSON 数组 | JSON 数组，保持顺序 |
| `StringNode` | JSON 字符串，支持 `\uXXXX` 等转义 | 字符串；控制字符转义，非 ASCII 原样 UTF-8 |
| `NumberNode` | 整数/小数/指数原文写入 `raw`，`value` 为精确 `BigDecimal` | 按 `raw` 写出；`Double`/`Float` 写出最短可往返文本 |
| `BooleanNode` | `true` / `false` | `true` / `false` |
| `NullNode` | `null` | `null` |
| `BinaryNode` | 不会从 JSON 文本读出（JSON 无二进制类型） | 写成 Base64 字符串；再读取得到 `StringNode`，**不保型** |

数字约束：`NumberNode` 只保存有限十进制值，NaN / Infinity 无法表示；写出时 `writeDouble` 也要求有限值。

## 示例

### 1. 字符串 ↔ Bean

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = JsonMapper()
val user = mapper.parse("""{"name":"小于","age":18}""", User::class.java)
val text = mapper.format(user)
println(text)   // 紧凑 JSON（字段顺序由 Bean 属性元数据决定）
```

### 2. JSON → Node 树

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse("""{"name":"小于","tags":["a","b"]}""", Node::class.java)
val obj = node.asObject()
println(obj["name"]?.asString())          // 小于
println(obj["tags"]?.asArray()?.get(0)?.asString()) // a
println(obj["name"]?.asStringOrNull())    // 类型不匹配时返回 null，不抛异常
```

### 3. 泛型 List / Map

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference

val json = """[{"name":"a","age":1},{"name":"b","age":2}]"""
val users = mapper.parse(json, DocumentTypeReference.listType(User::class.java))

val mapJson = """{"a":{"name":"a","age":1}}"""
val userMap = mapper.parse(
    mapJson,
    DocumentTypeReference.mapType(String::class.java, User::class.java),
)
```

### 4. JSONC 注释

```kotlin
val jsonc = """
{
  // 用户名
  "name": "小于",
  /* 年龄 */
  "age": 18
}
"""

val fromJsonc = mapper.parse(jsonc, User::class.java)          // 默认允许注释
val strict = JsonMapper(com.mingliqiye.document.format.json.JsonConfig.builder()
    .allowComments(false).build())
val err = runCatching { strict.parse(jsonc, User::class.java) } // JsonParseException
```

### 5. 重复 key 策略

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonReader
import com.mingliqiye.document.format.json.exception.JsonParseException

val src = """{"name":"first","name":"second"}"""

val last = JsonReader(src, duplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS).read()
println(last.asObject()["name"]?.asString())   // second

val first = JsonReader(src, duplicateKeyPolicy = DuplicateKeyPolicy.FIRST_WINS).read()
println(first.asObject()["name"]?.asString())  // first

val throwing = JsonReader(src, duplicateKeyPolicy = DuplicateKeyPolicy.FAIL)
val error = runCatching { throwing.read() }.exceptionOrNull()
check(error is JsonParseException)
```

### 6. pretty 输出

```kotlin
// 一次性 pretty
println(mapper.formatPretty(user))

// 让 format() 默认 pretty
val prettyMapper = JsonMapper(JsonConfig.builder().pretty(true).build())
println(prettyMapper.format(user))
println(prettyMapper.formatBytes(user).decodeToString())   // pretty 字节
```

### 7. 字节数组读写（严格 UTF-8）

```kotlin
import com.mingliqiye.document.format.json.exception.JsonParseException

val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)

val bad = byteArrayOf(0xC3.toByte())   // 截断的 UTF-8
val error = runCatching { mapper.parse(bad, User::class.java) }.exceptionOrNull()
check(error is JsonParseException)
```

### 8. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.json"))
val fromFile = mapper.parse(File("build/user.json"), User::class.java)

mapper.formatPretty(user, Paths.get("build/user-pretty.json"))
val fromPath = mapper.parse(Paths.get("build/user-pretty.json"), User::class.java)
```

### 9. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter

val output = ByteArrayOutputStream()
mapper.format(user, output)                    // 流式直接写
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)

val writer = StringWriter()
mapper.formatPretty(user, writer)
val fromReader = mapper.parse(StringReader(writer.toString()), User::class.java)
```

### 10. Node 与 Bean 互相转换

```kotlin
val node = mapper.toNode(user)                 // Bean -> Node
val another = mapper.fromNode(node, User::class.java)   // Node -> Bean
val converted = mapper.convert(node, User::class.java)  // Node/Bean -> Bean
```

### 11. 深度限制

```kotlin
import com.mingliqiye.document.format.json.JsonParseException
import com.mingliqiye.document.format.json.JsonReader

val deep = """{"a":{"b":{"c":1}}}"""
val reader = JsonReader(deep, maxDepth = 2)
val error = runCatching { reader.read() }.exceptionOrNull()
check(error is JsonParseException)   // 深度超过 maxDepth
```

### 12. Java 示例

```java
import com.mingliqiye.document.format.json.JsonMapper;

public class JsonDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        JsonMapper mapper = new JsonMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        String json = mapper.format(user);
        User restored = mapper.parse(json, User.class);
        System.out.println(restored.name + ":" + restored.age);

        // 带配置的 Builder
        JsonMapper pretty = JsonMapper.builder()
                .config(com.mingliqiye.document.format.json.JsonConfig.builder()
                        .pretty(true)
                        .maxDepth(256)
                        .build())
                .build();
        System.out.println(pretty.format(user));
    }
}
```

### 13. Kotlin data class

需要 `serialization-kotlin` 模块：

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.kotlin.kotlinModule

data class User(val name: String, val age: Int)

val moduleMapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .build()

val user = moduleMapper.parse("""{"name":"小于","age":18}""", User::class.java)
println(moduleMapper.format(user))
```

### 14. JSON Schema 校验

```kotlin
import com.mingliqiye.document.format.json.schema.JsonSchema
import com.mingliqiye.document.format.json.schema.JsonSchemaValidationException
import com.mingliqiye.document.core.Node

val schema = JsonSchema.compile(
    """
    {
      "type": "object",
      "required": ["name"],
      "properties": {
        "name": {"type": "string", "minLength": 1},
        "age": {"type": "integer", "minimum": 0}
      },
      "additionalProperties": false
    }
    """.trimIndent(),
)

val node = mapper.parse("""{"name":"小于","age":18}""", Node::class.java)

val errors = schema.validate(node)     // List<JsonSchemaValidationError>
schema.isValid(node)                   // true / false
schema.validateOrThrow(node)           // 失败抛 JsonSchemaValidationException
```

`JsonSchema` 还提供 `of(schemaNode: Node)` 从 Node 构造。校验错误对象 `JsonSchemaValidationError` 包含：

- `instancePath`：实例的 JSON Pointer 路径（如 `/address/city`）；
- `message`：错误描述；
- `toString()`：`"$instancePath: $message"`。

`JsonSchemaValidationException.errors` 保存全部错误，异常 message 为逐行拼接。

支持的 JSON Schema 关键字（源码实现范围）：

```text
type / enum / const
properties / required / additionalProperties / patternProperties
minProperties / maxProperties
items / minItems / maxItems / uniqueItems
minimum / maximum / exclusiveMinimum / exclusiveMaximum / multipleOf
minLength / maxLength / pattern
allOf / anyOf / oneOf / not
if / then / else
contains / minContains / maxContains
dependentRequired
propertyNames
format：date-time / date / time / email / hostname / ipv4 / ipv6 / uri / uuid
本地 $ref（#/...）
```

不支持远程 `$ref`（编译或使用会抛 `JsonSchemaException`）、`dependentSchemas`、`unevaluatedProperties` 等高级关键字；非法关键字值（如未知 `type`）在校验时抛 `JsonSchemaException`。

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 空字符串 / 多个顶层值 | `JsonParseException` |
| 非法 UTF-8 字节 | `parse(ByteArray)` 抛 `JsonParseException("Invalid UTF-8 input")` |
| `allowComments = false` 且输入含注释 | `JsonParseException` |
| 重复 key | 按 `duplicateKeyPolicy`；`FAIL` 抛 `JsonParseException` |
| 嵌套深度 > `maxDepth` | `JsonParseException`，默认上限 512 |
| `maxDepth` 配置 ≤ 0 | `IllegalArgumentException`（Builder 校验） |
| 输入超过 `maxInputSize` | `DocumentSerializationException` |
| 文件/流输入含非法 UTF-8 | 文件/流路径按 `fileCharset` 解码（默认 UTF-8 的宽松解码），不保证与 `parse(ByteArray)` 相同的严格校验 |
| 数字 NaN / Infinity | 无法构造 `NumberNode`；写出非有限 Double 抛 `IllegalArgumentException` |
| `BinaryNode` 写入 | 写成 Base64 字符串，读取回来是 `StringNode`（JSON 无二进制类型） |
| 非字符串 Map key | Bean 层 Map 的 key 作为 JSON 字段名 `toString()` |
| 开启 `ALLOW_NON_NUMERIC_NUMBERS` 后读取 `NaN` / `Infinity` | Node 树保留 `raw`；`NumberNode.value` 访问抛 `NumberFormatException`，需要数值时用 `asNumber().toDouble()` |
| 使用 `toNode` 序列化非有限 `Double` / `Float` | `IllegalArgumentException`（`NumberNode` 只能表示有限 `BigDecimal`）；流式写出 `format` / `formatBytes` 在 `WRITE_NAN_AS_STRINGS` 开启时写成 `"NaN"` 等字符串 |
| 关闭 `WRITE_HEX_UPPER_CASE` | `\uXXXX` 中的十六进制字母变为小写（默认大写，与 参考实现 一致） |
| 关闭 `QUOTE_FIELD_NAMES` | 字段名按原样输出，可能得到非标准 JSON（参考实现 行为） |
| `format(value, OutputStream/Writer)` | 只 flush 不关闭调用方目标（`AUTO_CLOSE_TARGET` 未暴露） |

## 与通用 `DocumentMapper` 的差异

- 构造函数：额外提供 `JsonConfig` 配置类与 `JsonMapper.builder()`；
- `parse(String)` / `parse(ByteArray)`：覆盖为 Token 直读，不构建完整 Node 树；
- `formatBytes`：紧凑模式下用 `JsonUtf8StreamWriter` 直接输出 UTF-8 字节，不先构造 `String`；
- `format` / `formatPretty`：尊重 `JsonConfig.pretty`；`formatPretty` 始终 pretty；
- `createStreamingTokenWriter`：`OutputStream` 写出走原生流式 Token 写出器；
- 配置 API：新增 `JsonReadFeature` / `JsonWriteFeature`、`JsonConfig.readFeatures/writeFeatures`、
  `JsonConfigBuilder.enable/disable/configure`，并转发全部通用 `DocumentMapperConfig` 选项；
  `JsonParser.Feature` / `JsonGenerator.Feature` / `StreamReadFeature` / `StreamWriteFeature`
  的覆盖情况见上文「参考实现 2.15.3 配置对齐」；
- JSON 专属：`JsonReader`、`DuplicateKeyPolicy`、`JsonSchema`、`String.toJsonNode()` / `Node.toJsonString()`；
- 严格 UTF-8 字节语义：仅 `parse(ByteArray)` 使用严格解码，文件/流路径按 `fileCharset` 解码。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [Token 与流式读写](../guide/token-stream.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-json 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-json/README.md)
