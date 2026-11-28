# format-avro

Apache Avro 格式模块。

提供 Schema、wire 编解码、OCF 容器、Avro logical type、schema evolution，以及对齐
`Avro 底层库` 的格式配置（`AvroConfig` / `AvroReadFeature` / `AvroWriteFeature`）。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:avro:2.5.1")
```

## 主要 API

```text
AvroMapper
AvroMapperBuilder
AvroConfig
AvroConfigBuilder
AvroReadFeature
AvroWriteFeature
AvroSchema
AvroSchemaBuilder
AvroCodec
AvroType
AvroLogicalType
AvroContainerReader
AvroContainerWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper

val mapper = AvroMapper(avroSchema)
val bytes = mapper.formatBytes(value)
val restored = mapper.parse(bytes, Node::class.java)
```

## 配置：AvroConfig

`AvroConfig` 继承通用 `DocumentMapperConfig`，在通用配置上增加 Avro 专属选项，并对齐
`Avro 底层库` 的格式特性。

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `schema` | `Schema?` | `null` | writer Schema；`AvroMapper(config)` 使用 |
| `readerSchema` | `Schema?` | `null` | reader Schema（Schema 演进）；`null` 表示与 writer 相同 |
| `codec` | `AvroCodec` | `NULL` | OCF 容器默认 codec（`formatContainerBytes` / 新建容器的 `appendContainer`） |
| `readFeatures` | `Set<AvroReadFeature>` | `{AVRO_BUFFERING}` | Avro 读取特性 |
| `writeFeatures` | `Set<AvroWriteFeature>` | `{AVRO_BUFFERING, ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA}` | Avro 写出特性 |

读取特性（对齐 参考实现 2.x `AvroParser.Feature` / 3.x `AvroReadFeature`）：

| 特性 | 参考实现 | 默认 | 行为 |
|---|---|---|---|
| `AVRO_BUFFERING` | `AVRO_BUFFERING` | 开启 | 开启用 `binaryDecoder`（缓冲），关闭用 `directBinaryDecoder`；解析结果与错误行为一致 |

写出特性（对齐 参考实现 2.15.3 `AvroGenerator.Feature` / 3.x `AvroWriteFeature`）：

| 特性 | 参考实现 | 默认 | 行为 |
|---|---|---|---|
| `ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA` | 3.x 新增 | 本项目开启（历史兼容；参考实现 3.x 默认关闭） | 由 `AvroSchema` 生成 Schema 时给可空/非必填 union 字段补 `"default": null` |
| `AVRO_BUFFERING` | `AVRO_BUFFERING` | 开启 | 开启用 `binaryEncoder`，关闭用 `directBinaryEncoder`；输出字节一致 |
| `AVRO_FILE_OUTPUT` | `AVRO_FILE_OUTPUT` | 关闭 | `formatBytes` 及 `format(value, OutputStream/File)` 改写单记录 OCF 容器 |

```kotlin
import com.mingliqiye.document.format.avro.AvroCodec
import com.mingliqiye.document.format.avro.AvroConfig
import com.mingliqiye.document.format.avro.AvroMapper
import com.mingliqiye.document.format.avro.AvroWriteFeature

val mapper = AvroMapper(
    AvroConfig.builder()
        .schema(writerSchema)
        .readerSchema(readerSchema)        // 可选：Schema 演进
        .codec(AvroCodec.DEFLATE)          // 可选：OCF 默认 codec
        .disable(AvroWriteFeature.AVRO_BUFFERING)
        .enable(AvroWriteFeature.AVRO_FILE_OUTPUT)
        .build(),
)

val ocf = mapper.formatBytes(user)         // 开启 AVRO_FILE_OUTPUT 后输出 OCF 容器
val nodes = mapper.parseContainerToNodes(ocf)
```

说明：

- `AvroConfigBuilder` 同时转发全部通用 `DocumentMapperConfig` 便捷方法（`missingFieldPolicy`、`writeNulls`、`maxNodeDepth`、`failOnUnknownFields`、`namingStrategy` 等），始终使用它不会丢失通用配置；
- 也可以继续使用 `AvroMapper(schema)` / `AvroMapper(writerSchema, readerSchema)` 等旧构造器，或把普通 `DocumentMapperConfig` 传给 Mapper，此时 Avro 专属选项回退默认值；生效配置可通过 `mapper.avroConfig` 查询；
- `AVRO_FILE_OUTPUT` 与 参考实现 一致只影响写出：容器读取需使用 `parseContainerToNodes` / `parseContainer`，`parse(ByteArray)` 只接受裸 datum；
- `AVRO_BUFFERING` 只作用于裸 wire 编解码；OCF 由 Apache Avro `DataFileReader` / `DataFileWriter` 自行管理缓冲；
- `ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA` 只影响由 `AvroSchema` 生成的 Schema；直接传入 `org.apache.avro.Schema` 时不生效；
- `AvroMapper.builder()` 可先不带 Schema 创建构建器，再用 `schema(...)` / `readerSchema(...)` 或 `config(AvroConfig)` 提供。

## Avro logical type

读取与写出都按 Schema 中声明的 `logicalType` 处理，Node 映射如下：

| `logicalType` | 底层类型 | 读取（Avro → Node） | 写出（Node → Avro） |
|---|---|---|---|
| `date` | `int` | `"2024-01-02"`（ISO-8601 日期） | ISO 日期字符串或 epoch 天数 |
| `time-millis` | `int` | `"10:15:30.123"` | ISO 时间字符串或自午夜毫秒数 |
| `time-micros` | `long` | `"10:15:30.123456"` | ISO 时间字符串或自午夜微秒数 |
| `timestamp-millis` | `long` | `"2024-01-02T03:04:05.123Z"` | ISO UTC 时间字符串或 epoch 毫秒数 |
| `timestamp-micros` | `long` | `"2024-01-02T03:04:05.123456Z"` | ISO UTC 时间字符串或 epoch 微秒数 |
| `local-timestamp-millis` | `long` | `"2024-01-02T03:04:05.123"` | 无时区 ISO 时间字符串或 epoch 毫秒数 |
| `local-timestamp-micros` | `long` | `"2024-01-02T03:04:05.123456"` | 无时区 ISO 时间字符串或 epoch 微秒数 |
| `decimal` | `bytes` / `fixed` | 保留 scale 的 `NumberNode`（BigDecimal） | 数字或十进制字符串，按 precision/scale 校验 |
| `uuid` | `string` | UUID 字符串 | UUID 字符串（按 canonical 小写写出） |
| `duration` | `fixed(12)` | `{"months":n,"days":n,"millis":n}` | 同名对象或 12 字节二进制（`BinaryNode`/Base64） |

规则：

- 未知 `logicalType` 回退到底层 Avro 类型的原有行为；
- 已知 `logicalType` 的 Schema 结构非法（底层类型不匹配、decimal precision/scale 非法、fixed 容量不足、duration 非 12 字节）时，读取抛 `AvroParseException`、写出抛 `AvroException`；
- 时间精度超出逻辑类型（如 `timestamp-millis` 收到微秒值）报错，不做静默截断；decimal 无法无损调整 scale 或超出 precision 同样报错；
- union / null / 嵌套 record / array / map 内的逻辑类型按 Schema 逐层生效；
- `AvroLogicalType.fromSchema(schema)` 可查询 Schema 节点支持的逻辑类型，未知逻辑类型返回 `null`；`AvroLogicalType.fromTypeName(name)` 按名称查找；
- 容器（OCF）读写与 `maxNodeDepth` 限制对逻辑类型同样生效。

示例：

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.AvroLogicalType
import com.mingliqiye.document.format.avro.AvroMapper
import org.apache.avro.Schema
import java.math.BigDecimal

val schema = Schema.Parser().parse(
    """
    {"type":"record","name":"Event","fields":[
      {"name":"date","type":{"type":"int","logicalType":"date"}},
      {"name":"amount","type":{"type":"bytes","logicalType":"decimal","precision":10,"scale":2}},
      {"name":"id","type":{"type":"string","logicalType":"uuid"}},
      {"name":"elapsed","type":{"type":"fixed","name":"Elapsed","size":12,"logicalType":"duration"}}
    ]}
    """.trimIndent(),
)
val mapper = AvroMapper(schema)
val node = ObjectNode.of(
    "date" to StringNode.of("2024-01-02"),
    "amount" to NumberNode.of(BigDecimal("123.45")),
    "id" to StringNode.of("123e4567-e89b-12d3-a456-426614174000"),
    "elapsed" to ObjectNode.of(
        "months" to NumberNode.of(1),
        "days" to NumberNode.of(2),
        "millis" to NumberNode.of(3),
    ),
)

// 每个字段的 logical type 可由 Schema 查询
check(AvroLogicalType.fromSchema(schema.getField("amount").schema()) == AvroLogicalType.DECIMAL)

val restored: Node = mapper.parse(mapper.formatBytes(node), Node::class.java)
check(restored.asObject()["date"]?.asString() == "2024-01-02")
```

## Schema evolution

```kotlin
val mapper = AvroMapper(writerSchema, readerSchema)
val restored = mapper.parse(bytes, User::class.java)
```

读取按 reader Schema 转换，因此 reader Schema 上的 logical type 会生效；
writer Schema 只有底层类型时，reader 可把 `long` 当 `timestamp-millis` 读取。

## OCF

```kotlin
val ocf = mapper.formatContainerBytes(records, AvroCodec.DEFLATE)
val appended = mapper.appendContainerBytes(ocf, moreRecords)
val nodes = mapper.parseContainerToNodes(appended)
```

支持 codec：

```text
NULL
DEFLATE
SNAPPY
```

文件追加：

```kotlin
mapper.appendContainer(file, records)
```

## 特性

- Avro Schema <-> `org.apache.avro.Schema`
- record / array / map / enum / fixed / union / bytes
- 参考实现 Avro 配置对齐：`AvroConfig` / `AvroReadFeature` / `AvroWriteFeature`
  （`AVRO_BUFFERING`、`AVRO_FILE_OUTPUT`、`ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA`）
- Avro logical type：date / time / timestamp / local-timestamp / decimal / uuid / duration
- Object Container File
- OCF codec：null / deflate / snappy
- appendTo 文件追加
- writer/reader 双 Schema 演进
- 解析深度限制（裸 wire 与 OCF 一致）
- 文件/流读写

## 限制

- 裸二进制需要调用方提供 Schema
- 追加不能改变容器 codec/schema
- 不支持 bzip2 / xz / zstandard
- 非有限浮点拒绝
- `AVRO_FILE_OUTPUT` 写出的 OCF 需用 `parseContainer*` 读取；`parse(ByteArray)` 只接受裸 datum
- `AVRO_BUFFERING` 只影响裸 wire 编解码，不影响 OCF 缓冲
- `ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA` 默认开启（历史兼容），与 参考实现 3.x 默认值不同；只影响 `AvroSchema` 生成的 Schema
- 未实现 参考实现 `AvroFactory` 的 native decoder 模式、`AvroMapper.schemaFor`（从 POJO 自动生成 Schema）与 Avro 注解（`@AvroNamespace` / `@AvroFixedSize` / `@AvroDecimal`）
- `uuid` 读取按字符串返回，不额外校验格式（写出时校验并按 canonical 小写规范化）
- `AvroSchema` 通用 `SchemaType` 不携带 logical type：`AvroSchema.toAvroSchema()` 的 `DECIMAL` 是不带 precision/scale 的 `bytes`，`fromAvroSchema` 把已知 logical type 归一为底层 `SchemaType`（decimal 保留 `DECIMAL`）；需要完整 logical type 时直接使用 Apache Avro Schema
- `duration` 是 Avro 规范中的 logical type，但 Apache Avro 1.11 的 `LogicalTypes` 未注册它，本模块通过原始 `logicalType` 属性识别

## 构建

```bash
gradle :format-avro:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
