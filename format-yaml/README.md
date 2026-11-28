# format-yaml

YAML 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:yaml:2.5.1")
```

## 主要 API

```text
YamlMapper
YamlReader
YamlMultiDocumentReader
YamlConfig
YamlConfigBuilder
YamlReadFeature
YamlWriteFeature
YamlFlowStyle
```

## 示例

```kotlin
import com.mingliqiye.document.format.yaml.YamlMapper

val mapper = YamlMapper()
val yaml = """
    name: 小于
    age: 18
""".trimIndent()

val user = mapper.parse(yaml, User::class.java)
val text = mapper.format(user)
```

## 多文档

```kotlin
val nodes = YamlMapper().parseAllToNodes(text)
val result = YamlMapper().formatAll(nodes)
```

## 配置对齐 参考实现 YAML（2.15）

`YamlConfig` 的读写特性与默认值对齐 参考实现 YAML 2.15 的
`YAMLGenerator.Feature` / `YAMLParser.Feature`，`YamlConfigBuilder` 提供与 参考实现
同名的 `enable` / `disable` / `configure`。

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.format.yaml.YamlReadFeature
import com.mingliqiye.document.format.yaml.YamlWriteFeature

val mapper = YamlMapper(
    YamlConfig.builder()
        .pretty(true)
        .enable(YamlWriteFeature.MINIMIZE_QUOTES)
        .enable(YamlWriteFeature.LITERAL_BLOCK_STYLE)
        .enable(YamlWriteFeature.INDENT_ARRAYS)
        .disable(YamlWriteFeature.WRITE_DOC_START_MARKER)
        .disable(YamlReadFeature.EMPTY_STRING_AS_NULL)
        .build(),
)
```

### 读取特性（`YamlReadFeature`）

| 特性 | 默认 | 行为 |
|---|---|---|
| `EMPTY_STRING_AS_NULL` | `true` | 空 plain 标量（`key:`）读取为 `null`；关闭后读取为空字符串 `""`（`''` 始终是空字符串） |
| `PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS` | `false` | 开启后 `yes`/`no`/`on`/`off` 按字符串读取，只有 `true`/`false` 是布尔；显式 `!!bool` 无法匹配的值按字符串 |

读取特性同时作用于 Node 路径（`YamlReader` / `YamlMultiDocumentReader`）、原生 Token
路径（`YamlMapper.parse`）与 Node 回退路径。

### 写出特性（`YamlWriteFeature`）

| 特性 | 默认 | 行为 |
|---|---|---|
| `WRITE_DOC_START_MARKER` | `true` | 每个文档输出起始 `---` |
| `SPLIT_LINES` | `true` | 过长文本自动折行（SnakeYAML `splitLines`） |
| `MINIMIZE_QUOTES` | `false` | 关闭时字符串值一律双引号；开启后按 参考实现 quoting 规则输出 plain/双引号/字面量块 |
| `ALWAYS_QUOTE_NUMBERS_AS_STRINGS` | `false` | 与 `MINIMIZE_QUOTES` 配合：数字样字符串仍加引号 |
| `LITERAL_BLOCK_STYLE` | `false` | 含换行的字符串使用字面量块（开启 `MINIMIZE_QUOTES` 时自动对多行字符串生效） |
| `INDENT_ARRAYS` | `false` | 数组元素额外缩进（SnakeYAML `indicatorIndent=1`） |
| `INDENT_ARRAYS_WITH_INDICATOR` | `false` | 数组元素缩进且 `-` 指示符缩进 |
| `USE_PLATFORM_LINE_BREAKS` | `false` | 使用平台换行符（Windows `\r\n`）；关闭时统一 `\n` |
| `ALLOW_LONG_KEYS` | `false` | 简单 key 长度上限由 128 提升到 1024 |
| `CANONICAL_OUTPUT` | `false` | SnakeYAML canonical 输出（显式 tag + flow 布局） |
| `USE_NATIVE_OBJECT_ID` | `true` | **未支持**：本库 Node 模型不携带 参考实现 object id，对象引用使用通用 `$ref` |
| `USE_NATIVE_TYPE_ID` | `true` | **未支持**：本库多态类型使用通用 type 属性，Node 模型不携带 type id |

### SnakeYAML 选项

| 配置 | 默认 | 说明 |
|---|---|---|
| `pretty` | `false` | 布局开关：`false` 流式紧凑、`true` 块格式（本库历史行为） |
| `defaultFlowStyle` | `null` | `YamlFlowStyle.FLOW` / `BLOCK`；未设置时由 `pretty` 推导 |
| `indent` | `2` | 块式缩进（2..9） |
| `lineWidth` | `0` | 输出行宽；`0` 使用 SnakeYAML 默认值（80） |
| `allowUnicode` | `true` | `false` 时转义非 ASCII 字符 |
| `explicitEnd` | `false` | 文档末尾输出 `...` |
| `maxAliases` | `0` | 非标量 alias 上限；`0` 使用 SnakeYAML 默认值 |
| `allowCustomTags` | `false` | 是否允许未知自定义 tag |
| `allowDuplicateKeys` | `false` | 是否允许重复 mapping key |
| `codePointLimit` | `0` | 单次输入最大 code point；`0` 使用 SnakeYAML 默认值（3 MiB） |

### 公共 `DocumentMapperConfig` 便捷方法

`YamlConfigBuilder` 同时转发通用配置，避免为了组合 YAML 选项手工拼装
`DocumentMapperConfig`：

`field`、`missingFieldPolicy`、`failOnUnknownFields`、`writeNulls`、
`namingStrategy`、`fileCharset`、`atomicFileWrite`、`createParentDirectories`、
`maxInputSize`、`maxStringLength`、`maxNodeDepth`、`maxNodeCount`、
`remoteReferenceResolver`、`beanValidator`、`enable`/`disable`/`configure`
（`DeserializationFeature`）、`failOnNullForPrimitives`、`subtype`/`typeProperty`、
`resolveReferences`、`writeReferences`。

## 特性

- 单文档与多文档
- `!!binary` -> `BinaryNode`
- Token 直读 Bean
- SnakeYAML SafeConstructor
- 可选自定义 tag（`allowCustomTags`，默认关闭）
- 参考实现 YAML 2.15 读写特性对齐（`YamlReadFeature` / `YamlWriteFeature`）
- 解析深度限制
- alias 扩张限制
- 原生 YAML Token Reader
- 文件/流读写

## 与 参考实现 YAML 的行为差异

- **布局**：参考实现 YAML 固定 block 布局；本库默认 `pretty=false` 为流式紧凑布局，
  可通过 `pretty(true)` 或 `defaultFlowStyle(BLOCK)` 得到块格式。
- **type id / object id**：参考实现 的原生 type id（YAML tag）与 object id（anchor/alias）
  依赖 databind 类型/身份信息；本库 Node 模型不携带这些信息，
  `USE_NATIVE_OBJECT_ID` / `USE_NATIVE_TYPE_ID` 仅为配置兼容保留，不改变输出。
  本库等价能力为通用 type 属性与 `writeReferences` 的 `$ref`。
- **空文档**：参考实现 2.21+ 的 `YAMLParser.Feature.EMPTY_DOCUMENT_AS_EMPTY_OBJECT`
  不在 2.15 基线内，本库空文档固定为 `NullNode`。
- **自定义 tag**：只在读取侧生效；写出只根据 Node 类型输出标准 YAML，tag 信息不保留。
- **显式 `!!bool` 非法值**：按 参考实现 规则退回字符串；显式 `!!null` 空值仍按 null 处理
  （参考实现 该边缘场景返回空字符串）。

## 限制

- 非字符串 mapping key 支持有限
- 自定义 tag 只在读取侧生效：写出只根据 Node 类型输出标准 YAML，tag 信息不保留
- 自定义 tag 节点作为递归 alias 目标时不支持（抛 `YamlParseException`，不构建环）
- YAML 规范不支持跨文档 anchor/alias：`---` 分隔的多个文档中，后一文档不能引用
  前一文档的 anchor（会抛 `YamlParseException`），anchor 只在单个文档内有效
- timestamp 映射为字符串

## 构建

```bash
gradle :format-yaml:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
