# format-ion

Amazon Ion 格式模块，支持文本 Ion 与二进制 Ion。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:ion:2.5.1")
```

## 主要 API

```text
IonMapper
IonConfig / IonConfigBuilder
IonReadFeature / IonWriteFeature
IonReader / IonWriter
IonBinaryReader / IonBinaryWriter
IonTokenReader / IonBinaryTokenWriter / IonTextTokenWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.ion.IonMapper

val mapper = IonMapper()

// 文本
val text = mapper.format(user)
val a = mapper.parse(text, User::class.java)

// 二进制
val bytes = mapper.formatBytes(user)
val b = mapper.parse(bytes, User::class.java)
```

## 类型映射

```text
BLOB / CLOB -> BinaryNode
TIMESTAMP   -> StringNode
SYMBOL      -> StringNode
LIST / SEXP -> ArrayNode
STRUCT      -> ObjectNode
```

## 参考实现 读/写特性对齐

官方枚举核查：Ion 底层库 2.15 / 2.23 / 3.3 的格式级 Parser/Generator
Feature 只有 `USE_NATIVE_TYPE_ID`（2.12+）、`READ_NULL_AS_IONVALUE`（2.19+/3.x）。
不存在 `LENIENT_*` / `ALLOW_*` / `WRITE_*` 等常量（那些属于其他格式）。默认值对齐
参考实现（2.15 没有 READ_NULL_AS_IONVALUE 开关，但行为等价于开启）。

读取特性 `IonReadFeature`：

| 特性 | 参考实现 对应 | 默认 | 行为 |
|---|---|---|---|
| `USE_NATIVE_TYPE_ID` | `IonParser.Feature` / `IonReadFeature` | 开 | 保真模式（`preserveTypes=true`）下把 Ion annotation 读成 `{"$ionAnnotations":[...],"value":<node>}` 包装对象；否则注解丢弃 |
| `READ_NULL_AS_IONVALUE` | `IonReadFeature`（2.19+/3.x） | 开 | 关闭时 `null.list` / `null.sexp` → 空 `ArrayNode`，`null.struct` → 空 `ObjectNode`，其余 null 仍为 `NullNode` |
| `STRICT_DUPLICATE_DETECTION` | `JsonParser.Feature` / `StreamReadFeature`（参考实现 Ion parser 未接入 DupDetector，本项目实现） | 关 | struct 重复字段名抛 `IonParseException`；关闭时后者覆盖前者 |

写出特性 `IonWriteFeature`：

| 特性 | 参考实现 对应 | 默认 | 行为 |
|---|---|---|---|
| `USE_NATIVE_TYPE_ID` | `IonGenerator.Feature` / `IonWriteFeature` | 开 | 识别 `{"$ionAnnotations":[...],"value":<node>}` 并写为原生 Ion annotation；关闭时按普通 struct 写出 |
| `STRICT_DUPLICATE_DETECTION` | `StreamWriteFeature`（参考实现 3.x IonGenerator 已接入） | 关 | Token 写出器遇到重复字段名抛 `IonException`；Bean/Map/Node 写出不会产生重复字段名 |

```kotlin
import com.mingliqiye.document.format.ion.IonConfig
import com.mingliqiye.document.format.ion.IonReadFeature
import com.mingliqiye.document.format.ion.IonWriteFeature

val mapper = IonMapper(
    IonConfig.builder()
        .preserveTypes(true)
        .disable(IonReadFeature.USE_NATIVE_TYPE_ID)      // 丢弃 annotation
        .enable(IonReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(IonWriteFeature.STRICT_DUPLICATE_DETECTION)
        .build(),
)
```

`IonConfigBuilder` 同时转发全部通用 `DocumentMapperConfig` 选项：
`field` / `missingFieldPolicy` / `failOnUnknownFields` / `writeNulls` / `namingStrategy` /
`fileCharset` / `atomicFileWrite` / `createParentDirectories` / `maxInputSize` /
`maxStringLength` / `maxNodeDepth` / `maxNodeCount` / `resolveReferences` /
`writeReferences` / `remoteReferenceResolver` / `beanValidator` /
`subtype` / `typeProperty` / `DeserializationFeature` 的 `enable` / `disable` /
`configure` / `failOnNullForPrimitives`。

## 类型保真

默认关闭；开启后 Node 路径保留：

```kotlin
val mapper = IonMapper(
    IonConfig.builder()
        .preserveTypes(true)
        .build(),
)
```

包装对象：

```json
{"$ionType":"timestamp","value":"2026-11-28T00:00:00Z"}
{"$ionType":"symbol","value":"name"}
{"$ionType":"clob","value":<BinaryNode>}
{"$ionType":"blob","value":<BinaryNode>}
{"$ionAnnotations":["ann"],"value":<node>}
```

写出时自动还原为 Ion timestamp/symbol/clob/blob 与 annotation。开启后不走 Token 快路径；
Bean 直读建议保持关闭。

## 解析深度

```kotlin
IonReader(text, maxDepth = 64).read()
IonBinaryReader(bytes, maxDepth = 64).read()
```

`IonMapper(DocumentMapperConfig.builder().maxNodeDepth(64).build())` 的 Node 路径同样生效。

## 未支持 / 不适用项

- 参考实现 通用 `JsonParser.Feature` / `JsonGenerator.Feature` / `StreamReadFeature` /
  `StreamWriteFeature` 中与内存文本/字节 API 无关的项（如 `AUTO_CLOSE_SOURCE`、
  `FLUSH_PASSED_TO_STREAM`、`INCLUDE_SOURCE_IN_LOCATION`、`USE_FAST_*_PARSER`）
  由通用层处理或不适用，未映射为 Ion 特性。
- 参考实现 Ion 的文本/二进制 writer 选择由 `format(...)`（文本）与 `formatBytes(...)`
  （二进制）区分，未提供 `IonFactory.createBinaryWriters` 等价开关。
- 未暴露自定义 `IonSystem` / `IonFactory` 配置（固定使用 ion-java 标准 `IonSystem`）。
- annotation 仅在 `preserveTypes=true` 的 Node 路径保留；Token 直读/直写路径不携带 annotation。
- `READ_NULL_AS_IONVALUE=false` 的容器 null 映射是本项目对 Node 模型的近似
  （参考实现 返回 Java null / IonValue 的差异无法在 Node 树中完全表达）。

## 限制

- timestamp/symbol 写回为字符串（未开启 `preserveTypes` 时）
- Ion annotation 默认忽略
- CLOB 类型信息不保留（未开启 `preserveTypes` 时）

## 构建

```bash
gradle :format-ion:ktlintFormat :format-ion:test :format-ion:build
```

文档：[根 README](../README.md) · [Ion 格式文档](../docs/formats/ion.md) · [贡献指南](../CONTRIBUTING.md)
