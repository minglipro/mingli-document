# format-csv

CSV 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:csv:2.4.0")
```

## 主要 API

```text
CsvMapper
CsvReader
CsvWriter
CsvConfig
CsvSchema
CsvSchemaBuilder
CsvNestedMode
CsvSchemaReader
CsvSchemaWriter
CsvMapper.parseStream
```

## 示例

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper

val mapper = CsvMapper()
val csv = """
    name,age
    小于,18
""".trimIndent()

val users = mapper.parse(
    csv,
    com.mingliqiye.document.core.DocumentTypeReference.listType(User::class.java),
)
val text = mapper.format(users)
```

## 流式读取（大文件）

`CsvMapper.parseStream` 逐行解析 CSV 并逐条回调 Bean，不把整个文件读成 String；
表头、类型推断、`CsvConfig.nestedMode` 与 `CsvSchema` 规则和整段 `parse` 完全一致。

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.csv.CsvMapper
import java.io.FileInputStream

val mapper = CsvMapper() // 或 CsvMapper(schema) / CsvMapper(CsvConfig.builder()...build())

FileInputStream("users.csv").use { input ->
    mapper.parseStream(input, User::class.java) { user ->
        // 逐行处理：首次回调时尚未读完整个文件
    }
}
```

也支持 `Reader`、`DocumentTypeReference` 与 `maxRows` 上限：

```kotlin
// Reader 输入
mapper.parseStream(reader, User::class.java) { user -> }

// 泛型目标类型
mapper.parseStream(reader, DocumentTypeReference.of(User::class.java)) { user -> }

// 最多回调 1000 行（-1 表示不限制，默认）；达到上限后停止读取
mapper.parseStream(reader, User::class.java, maxRows = 1000) { user -> }
```

语义与限制：

- 逐行读取：首次回调后不会读完整个输入；带引号的多行单元格按“一条记录”解析。
- 空输入、只有表头时不产生回调。
- CSV 语法/编码错误与 Schema 校验失败抛
  `com.mingliqiye.document.format.csv.exception.CsvParseException`；Bean 反序列化失败抛
  `com.mingliqiye.document.serialization.exception.DocumentSerializationException`；
  `consumer` 抛出的异常原样传播。
- 不关闭调用方传入的流（只读取），调用方负责关闭。
- `maxInputSize` 按“累计已从输入读取的字节数（`InputStream`）/ 字符数（`Reader`）”
  校验（可能包含缓冲区预读），超限抛 `DocumentSerializationException`。
- 只支持顺序单遍读取；Schema 校验按行进行，不跨行共享上下文；流式 API 不改变写出行为。
- `maxRows` 达到上限后剩余输入不再读取，流中未消费的部分由调用方自行处理（不提供游标 API）。

## CSV Schema

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvSchema
import com.mingliqiye.document.schema.SchemaType

val schema = CsvSchema.builder("user")
    .field("name", SchemaType.STRING)
    .field("age", SchemaType.INT)
    .build()

val mapper = CsvMapper(schema)
```

## 嵌套单元格

默认不支持嵌套（保持旧版本行为）。通过 `CsvConfig.nestedMode` 或 Schema 中的嵌套子
Schema 按需启用。

### JSON 文本单元格（`CsvNestedMode.JSON`）

`{...}` / `[...]` 单元格解析为嵌套对象/数组，嵌套节点写出为 JSON 文本：

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvNestedMode

val mapper = CsvMapper(
    CsvConfig.builder()
        .nestedMode(CsvNestedMode.JSON)
        .build(),
)

val csv = """
    name,address,tags
    小于,"{""city"":""杭州""}","[""a"",""b""]"
""".trimIndent()
```

只有以 `{` / `[` 开头的单元格会被解析；非法 JSON 抛出
`CsvParseException`。数组类型字段在 Schema 模式下同样以 JSON 文本单元格读写。

### 点分列名（`CsvNestedMode.COLUMNS`）

表头中的点分列名读写为嵌套对象，写出时把嵌套对象展开为点分列名：

```kotlin
val mapper = CsvMapper(
    CsvConfig.builder()
        .nestedMode(CsvNestedMode.COLUMNS)
        .build(),
)

// name,address.city,address.geo.lat
// 小于,杭州,30.1
```

数组没有列形式，`COLUMNS` 模式下写出嵌套数组会抛出 `CsvException`。

## 多级 Schema

`CsvSchemaBuilder.objectField` 声明带子 Schema 的对象字段，子字段展开为点分列名并支持
任意层级；`CsvSchemaBuilder.arrayField` 声明带元素 Schema 的数组字段，数组以 JSON 文本
单元格读写、每个元素按元素 Schema 校验：

```kotlin
import com.mingliqiye.document.format.csv.CsvSchema

val geo = CsvSchema.builder("geo")
    .double("lat")
    .double("lon")
    .build()

val address = CsvSchema.builder("address")
    .string("city")
    .int("zip", required = false)
    .objectField("geo", geo, required = false)
    .build()

val member = CsvSchema.builder("member")
    .string("name")
    .int("age")
    .build()

val person = CsvSchema.builder("person")
    .string("name")
    .objectField("address", address)
    .arrayField("members", member) // JSON 数组单元格
    .build()

// person.columnNames ==
// [name, address.city, address.zip, address.geo.lat, address.geo.lon, members]

val mapper = CsvMapper(person)
```

写出时按子 Schema 递归展开：

```text
name,address.city,address.zip,address.geo.lat,address.geo.lon,members
小于,杭州,310000,30.1,120.2,"[{""name"":""小于"",""age"":18}]"
```

读取规则：

- 有表头时按列名映射（列顺序任意），无表头时按展开后的列顺序对应；
- 必填的嵌套叶子列缺失时报错；可选父对象整体缺席时其内部必填列不报错；
- 嵌套对象的所有单元格都为空时按对象缺席处理，便于可选对象写出后 round-trip；
- 嵌套对象上的额外字段忽略；`failOnUnknownColumns` 控制顶层未声明列与数组元素 Schema
  内部的未知字段。

## 深度限制（maxDepth）

默认解析上限为 512（根数组深度为 `1`、行对象为 `2`、单元格标量为 `3`），防止超深嵌套
Schema / JSON 文本单元格耗尽调用栈：

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvNestedMode

val mapper = CsvMapper(
    CsvConfig.builder()
        .nestedMode(CsvNestedMode.JSON)
        .maxDepth(64) // 默认 CsvConfig.DEFAULT_MAX_DEPTH = 512，必须为正数
        .build(),
)

// 独立读取器入口同样可配置
val node = com.mingliqiye.document.format.csv.CsvReader(csv, maxDepth = 64).read()
```

- 点分列名（`COLUMNS`）每多一层加 `1`；嵌套 Schema 的对象/数组元素每层加 `1`；
  JSON 文本单元格内部嵌套从单元格根深度 `3` 起算；
- Tree / Token / Bean 三条解析路径与 `parseStream` 流式解析都会在超限时抛出
  `com.mingliqiye.document.format.csv.exception.CsvParseException`；
- JSON 文本单元格的解析与写出都是带深度校验的递归下降，超深输入/节点不会
  `StackOverflowError`；写出侧同样受 `maxDepth` 限制（超限抛 `CsvException`）；
- 通用 `DocumentMapperConfig.maxNodeDepth` 保持 Node 限制语义（回退 Node 路径后由
  NodeLimits 校验，超限抛 `DocumentSerializationException`）；
- `CsvReader` / `CsvSchemaReader` / `CsvWriter` / `CsvSchemaWriter` 的 `maxDepth` 显式传
  `0` 或负数表示关闭保护。

## 特性

- 平坦表格
- 表头 / 无表头
- 类型推断
- CSV Schema
- 嵌套对象 / 数组（JSON 文本单元格、点分列名）
- 多级 Schema（嵌套子 Schema、数组元素 Schema）
- 文件/流读写
- 大文件流式逐行解析（`CsvMapper.parseStream`，支持 `maxRows` 与 Schema/嵌套复用）
- Token 直读 Bean
- 原生 CSV Token Reader
- 解析深度限制（默认 512，可配置，覆盖嵌套 Schema 与 JSON 单元格）

## 限制

- 嵌套 JSON 单元格只支持以 `{` / `[` 开头的对象/数组（不支持裸 JSON 标量）；
- `COLUMNS` 模式不支持数组（数组请用 `JSON` 模式或元素 Schema）；
- 多级 Schema 的嵌套对象固定按点分列名展开，子 Schema 的 `header` /
  `failOnUnknownColumns` 不参与列展开，仅 `failOnUnknownColumns` 参与数组元素 Schema 校验；
- 子 Schema 内的列名不能与其它展开列冲突（如既有 `address.city` 字段又展开 `address`）；
- 根节点固定为数组（写出时单对象按单行处理）。

## 构建

```bash
gradle :format-csv:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
