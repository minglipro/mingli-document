---
title: Fuzz 与安全
---

# Fuzz 与安全

> 解析不可信输入是这类库最危险的用法。`mingli-document` 提供三层保障：
> **解析器内置防护**（XXE、SafeConstructor、深度限制）、**可配置的资源边界**
> （输入大小/节点数/字符串长度/alias）、以及**持续 fuzz 工具链**（随机、Jazzer、JQF）。
> 本页说明能力边界与推荐配置。

## 目录

- [安全基线](#安全基线)
- [已有防护](#已有防护)
- [资源限制](#资源限制)
- [不可信输入推荐配置](#不可信输入推荐配置)
- [YAML 安全](#yaml-安全)
- [XML 安全](#xml-安全)
- [JSON 安全](#json-安全)
- [二进制格式安全](#二进制格式安全)
- [引用解析的安全](#引用解析的安全)
- [fuzz 模块](#fuzz-模块)
- [持续随机 fuzz](#持续随机-fuzz)
- [Jazzer 覆盖率引导 fuzz](#jazzer-覆盖率引导-fuzz)
- [JQF 覆盖率引导 fuzz](#jqf-覆盖率引导-fuzz)
- [语料目录](#语料目录)
- [安全清单](#安全清单)
- [非目标](#非目标)

## 安全基线

仓库根目录的 `SECURITY.md` 记录了安全边界与自动化基线测试：

```text
XML: 禁用 DOCTYPE、外部通用/参数实体、XInclude、实体展开
YAML: SafeConstructor、maxAliases、单文档
JSON/YAML/XML/Properties/TOML/CSV/Ion: maxInputSize / maxNodeDepth / maxNodeCount / maxStringLength
CBOR/Smile/Protobuf/Avro/Ion: 解析深度限制
$ref: 本地/远程解析器可插拔，远程默认禁用
```

基线测试：

```text
fuzz/SecurityBaselineTest
```

覆盖：XXE 外部实体拒绝、YAML alias 膨胀拒绝、Node 深度/字符串长度限制、输入大小限制、
CBOR 深度限制。

## 已有防护

| 风险 | 防护 |
|---|---|
| XML 外部实体（XXE） | 解析器禁用 DOCTYPE、外部通用/参数实体、XInclude、实体展开 |
| YAML 炸弹（billion laughs / alias 膨胀） | SnakeYAML `SafeConstructor`、可选 `maxAliases`、单文档模式 |
| 超深嵌套导致栈溢出 | 各格式解析深度限制；Node 树 `maxNodeDepth` |
| 超大输入占满内存 | `maxInputSize`、`maxNodeCount`、`maxStringLength` |
| 无限循环 | fuzz 基线：不崩溃、不无限循环、受控抛异常 |
| 循环引用 | 序列化时环检测（抛异常）；可选 `$ref` 表达 |
| 远程引用 SSRF | 远程 `$ref` 默认禁用，需要显式提供解析器 |
| 非有限浮点 | `NumberNode` 拒绝 NaN/Infinity；CBOR/Smile/Avro/Protobuf/Smile 拒绝 |
| 未知字段/扩展 | 未知字段策略、Protobuf `ProtobufUnknownFieldPolicy` |

## 资源限制

`DocumentMapperConfig` 提供四个通用限制：

| 配置 | 默认 | 作用 |
|---|---|---|
| `maxInputSize` | 0（不限制） | 单次输入最大字节数（String 按字符数近似） |
| `maxNodeDepth` | 0 | Node 树最大深度（根为 1） |
| `maxNodeCount` | 0 | Node 树最大节点数 |
| `maxStringLength` | 0 | Node 树单个字符串（含对象字段名）最大长度 |

格式自身的限制：

| 格式 | 限制入口 |
|---|---|
| JSON | `JsonConfig.maxDepth`（默认 512，必须 > 0） |
| YAML | `maxNodeDepth`、`maxAliases`、SnakeYAML 解析限制 |
| XML | `maxNodeDepth` + 安全解析器（不可放开 DOCTYPE） |
| CSV | Schema / 嵌套模式下的结构校验；Token Reader |
| TOML | `maxNodeDepth`（回退 Node 路径时生效） |
| Properties | `PropertiesTokenReader` 显式接收三个 Node 限制 |
| CBOR | `CborReader(source, maxDepth)`、`CborMapper` 配置 `maxNodeDepth` |
| Smile | `SmileReader(source, maxDepth)`、`SmileSequenceReader(maxDepth, maxItems)` |
| Ion | `IonReader(text, maxDepth)`、`IonBinaryReader(bytes, maxDepth)` |
| Avro | `AvroMapper(config.maxNodeDepth)` 的 wire 深度限制 |
| Protobuf | `ProtobufMapper(descriptor, policy, maxDepth)` |

::: warning Token 快路径与限制
通用 Node 限制在 **Node 路径**上校验。纯 Token 快路径（例如 `JsonMapper.parse(String)`）
不构建完整 Node 树，因此多数格式不会单独检查这些限制；需要硬约束时请：

- 使用格式自身的深度限制（如 `JsonConfig.maxDepth`）；
- 或者先 `parse(text, Node::class.java)` 再 `fromNode(...)`，让 Node 路径校验；
- 或者选择在配置限制时会自动回退 Node 的格式（YAML/XML/TOML/CBOR）。

详见 [Token 流式 API 的限制](./token-stream.md#token-路径的限制)。
:::

## 不可信输入推荐配置

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy

// 用于不可信 JSON 的额外安全配置
val safeJson = JsonMapper(
    JsonConfig.builder()
        .allowComments(false)                        // 生产输入一般不需要注释
        .duplicateKeyPolicy(DuplicateKeyPolicy.FAIL) // 重复 key 直接拒绝
        .maxDepth(64)                                // 词法嵌套上限
        .missingFieldPolicy(MissingFieldPolicy.FAIL)
        .failOnUnknownFields(true)
        .build(),
)

// 通用限制：注意传入 DocumentMapperConfig 时 JSON 专属选项取默认
val bounded = JsonMapper(
    DocumentMapperConfig.builder()
        .maxInputSize(1024 * 1024)          // 1 MiB
        .maxNodeDepth(64)
        .maxNodeCount(100_000)
        .maxStringLength(1_000_000)
        .build(),
)
```

YAML 输入：

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper

val safeYaml = YamlMapper(
    YamlConfig.builder()
        .maxAliases(50)          // 非标量 alias 上限
        .allowCustomTags(false)  // 未知 tag 直接拒绝
        .build(),
)
```

XML 输入：

```kotlin
import com.mingliqiye.document.format.xml.XmlMixedContentPolicy
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper

val safeXml = XmlMapper(
    XmlConfig.builder()
        .mixedContentPolicy(XmlMixedContentPolicy.REJECT) // 拒绝混合内容
        .build(),
)
```

> XML 的 XXE 防护是解析器级别的固定行为，没有开关可以放开；这是刻意设计。

## YAML 安全

默认使用 SnakeYAML `SafeConstructor`：

- 未知自定义 tag（`!foo`、`!env`、`!!foo`、`!<tag:...>`）抛 `YamlParseException`；
- 不会实例化任意 Java 类型；
- 单文档模式；多文档用 `parseAllToNodes` 显式开启；
- `maxAliases` 限制单文档非标量 alias 数量，默认 `0` 表示使用 SnakeYAML 默认值；
- 跨文档 anchor/alias 无效（会抛异常），anchor 只在单个文档内有效；
- 自定义 tag 节点作为递归 alias 目标时拒绝，不构建环。

需要处理带自定义 tag 的合法 YAML 时：

```kotlin
val mapper = YamlMapper(
    YamlConfig.builder()
        .allowCustomTags(true)   // 标量->StringNode，序列->ArrayNode，映射->ObjectNode
        .maxAliases(100)
        .build(),
)
```

开启后仍然不会实例化 Java 类型，但未知 tag 的标量不再做类型推断（保持原始文本）。

## XML 安全

固定防护（不可关闭）：

```text
禁用 DOCTYPE
禁用外部通用实体 / 参数实体
禁用 XInclude
禁用实体展开
```

因此 XXE 攻击面在解析阶段就被切断。另外：

- 解析深度受限（`maxNodeDepth`，并在 fuzz 基线中验证）；
- 混合内容可用 `XmlMixedContentPolicy.REJECT` 拒绝，避免歧义结构；
- 属性/命名空间映射是可选项，默认关闭，减少攻击面；
- 根元素名、属性前缀等只影响写出，不影响读取安全。

## JSON 安全

- `JsonConfig.maxDepth`（默认 512，必须为正数）限制对象/数组嵌套；
- `DuplicateKeyPolicy.FAIL` 可拒绝重复 key（避免“解析器分歧”类攻击）；
- `JsonConfig.allowComments` 生产环境建议关闭；
- JSON 字节 API 严格 UTF-8，非法编码抛 `JsonParseException`；
- 数字用 `BigDecimal` 精确表示，拒绝 NaN/Infinity；
- 轻量 JSON Schema 校验可用于业务层结构校验（`JsonSchema.compile(...)`），
  但它不是安全边界。

## 二进制格式安全

| 格式 | 保护点 |
|---|---|
| CBOR | 深度限制、拒绝 NaN/Infinity、tag 保真需要显式开启 |
| Smile | 深度限制、消息数量 `maxItems`、非有限浮点拒绝 |
| Ion | 深度限制、类型保真需要显式开启 |
| Avro | Schema 必填、深度限制、非有限浮点拒绝 |
| Protobuf | Descriptor 必填、深度限制、未知字段策略默认 `FAIL` |

`ProtobufUnknownFieldPolicy` 三个取值：

```text
FAIL       默认：遇到未知字段抛 ProtobufParseException
IGNORE     丢弃未知字段
PRESERVE   保留为 "$unknownFields"，可原样写回
```

对不可信输入建议保持默认 `FAIL`，或显式 `IGNORE`；`PRESERVE` 会保留原始 wire 数据，
需要额外的内存与大小评估。

## 引用解析的安全

- 本地 `$ref`（`#/...`）：只有调用方主动开启 `resolveReferences(true)` 才解析；
- 远程 `$ref`：默认 `remoteReferenceResolver = null`，**直接报错**；
- 提供远程解析器时，URI 校验、IO、缓存、超时、SSRF 防护全部由调用方负责；
- 纯别名环与缺失引用会抛 `DocumentSerializationException`，不会死循环。

```kotlin
// 不安全：不要给不可信输入配置能访问内网的解析器
// 安全默认：不配置 remoteReferenceResolver
val mapper = JsonMapper(
    DocumentMapperConfig.builder()
        .resolveReferences(true)
        .build(),
)
```

## fuzz 模块

`fuzz` 模块只用于测试，不发布、不在 BOM 中。覆盖：

```text
JSON YAML XML Properties TOML CSV Ion CBOR Smile Avro Protobuf
```

测试内容：

- malformed 文本/字节语料；
- 随机畸形输入；
- 超时保护；
- 不崩溃、不无限循环、不 StackOverflow；
- 解析器受控抛异常（普通异常视为被拒绝）。

运行：

```bash
# 有限随机 + 语料测试（常规构建会执行）
gradle :fuzz:test

# 持续随机 fuzz
gradle :fuzz:fuzzContinuous -PfuzzSeconds=60

# Jazzer 覆盖率引导（需要指定目标）
gradle :fuzz:fuzzJazzer -PfuzzSeconds=30 -PjazzerTarget=com.mingliqiye.document.fuzz.JsonJazzerTarget

# JQF 覆盖率引导
gradle :fuzz:fuzzJqf -PfuzzSeconds=30
```

## 持续随机 fuzz

`fuzzContinuous` 运行 `com.mingliqiye.document.fuzz.FuzzHarnessKt`：

- 参数：秒数（默认 10，通过 `-PfuzzSeconds` 传入）；
- 每轮随机生成 0~127 字节的二进制与 0~127 字符的文本；
- 用 11 个 Mapper 分别对文本/字节做 `parse(..., Node::class.java)`；
- 普通异常视为「解析器按预期拒绝」；
- 遇到 `StackOverflowError` 以退出码 **2** 终止；遇到 `OutOfMemoryError` 以退出码
  **3** 终止；
- 正常结束打印 `fuzz completed: iterations=N seconds=S`。

```bash
gradle :fuzz:fuzzContinuous -PfuzzSeconds=120
```

适合在改动解析器后本地跑一段时间，作为快速冒烟。

## Jazzer 覆盖率引导 fuzz

`fuzzJazzer` 使用 Jazzer（`com.code-intelligence:jazzer:0.22.1`）运行单个字节目标：

```bash
gradle :fuzz:fuzzJazzer \
  -PfuzzSeconds=30 \
  -PjazzerTarget=com.mingliqiye.document.fuzz.JsonJazzerTarget
```

可用目标（`JazzerTargets.TARGETS`）：

```text
JsonJazzerTarget           YamlJazzerTarget        XmlJazzerTarget
PropertiesJazzerTarget     TomlJazzerTarget        CsvJazzerTarget
IonJazzerTarget            CborJazzerTarget        SmileJazzerTarget
AvroJazzerTarget           ProtobufJazzerTarget
CborTagJazzerTarget        IonFidelityJazzerTarget
```

后两个目标专门覆盖「保真模式」：CBOR tag 保真与 Ion 类型保真。

目标实现约定：`fuzzerTestOneInput(data: ByteArray)`，内部捕获普通异常，
把畸形输入当作被拒绝。

## JQF 覆盖率引导 fuzz

`fuzzJqf` 使用 JQF（`edu.berkeley.cs.jqf:jqf-fuzz` + `jqf-junit5:3.0`）与
`jqf-instrument` agent：

```bash
gradle :fuzz:fuzzJqf -PfuzzSeconds=30
```

特性：

- 基于 JUnit 5 适配器，无需改测试框架；
- 使用 Java 17 工具链运行（`javaLauncher` 固定 17）；
- `-javaagent` 注入 JQF instrument；
- 覆盖 json / yaml / xml / properties / toml / csv / ion / cbor / smile；
- 常规 `test` 任务会排除 `*JqfTest*`，不影响普通构建；
- 结果输出到 `build/jqf-results`。

## 语料目录

```text
fuzz/src/test/resources/corpus/<format>/
```

新增边界样本时按格式放入对应目录。语料是回归测试的核心资产：
每次修复崩溃都应该补一个最小复现样本。

## 安全清单

上线前逐项确认：

- [ ] 不可信输入设置了 `maxInputSize`；
- [ ] 设置了 `maxNodeDepth` / `maxNodeCount` / `maxStringLength` 中至少深度与输入大小；
- [ ] JSON 使用 `JsonConfig.maxDepth` 并考虑 `DuplicateKeyPolicy.FAIL`；
- [ ] YAML 设置 `maxAliases`，保持 `allowCustomTags(false)`；
- [ ] XML 不尝试放开 DOCTYPE/外部实体（库层面也不提供开关）；
- [ ] Protobuf 未知字段策略符合预期；
- [ ] 不给不可信输入启用远程 `$ref`；
- [ ] 自定义转换器/解析器不引入 IO、反射加载类等额外攻击面；
- [ ] 升级依赖后跑一遍 `gradle :fuzz:test`，必要时跑 Jazzer/JQF；
- [ ] 业务层校验（JSON Schema / Bean 校验）与安全边界分开设计。

## 非目标

`SECURITY.md` 明确以下内容**不在**当前安全基线内：

- 恶意 schema 导致的业务逻辑绕过；
- 不受信任的 Jackson / 第三方库自身漏洞；
- 侧信道与 DoS 级别的 CPU/内存竞争；
- 远程 `$ref` 解析器的 SSRF 防护（由使用方实现）。

也就是说：本库负责「解析器不被打穿」，业务语义安全与基础设施防护仍需你自己兜底。

下一步：[迁移指南](./migration.md) 或 [常见问题](./faq.md)。
