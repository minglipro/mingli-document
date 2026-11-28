---
title: CSV
description: format-csv 模块：CsvMapper、CsvConfig、CSV Schema、参考实现 读写特性、嵌套单元格与多级 Schema 读写
---

# CSV

`format-csv` 提供 CSV 读写，核心类型是 `CsvMapper`，可选 `CsvSchema` 做列类型约束。

CSV 只能表达平坦表格：根节点固定为 `ArrayNode`，每个元素是一行 `ObjectNode`；写出时根对象按单行处理。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:csv:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `csv` |
| 版本 | `2.5.1` |
| 传递依赖 | `core`、`exception`、`serialization`、`schema` |
| 第三方依赖 | `org.apache.commons:commons-csv:1.10.0` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.csv` |

## 特性

- **平坦表格**：根数组 + 行对象；
- **表头 / 无表头**：`CsvConfig.header` 控制；无表头时列名为 `column1`、`column2`……；
- **类型推断**：`true` / `false` / 数字 / 字符串（与 Properties、CSV Schema 的推断规则一致）；
- **CSV Schema**：声明列顺序、列名与列类型，读取按类型解析、写出按 Schema 输出；
- **嵌套对象 / 数组**：`CsvNestedMode.JSON`（JSON 文本单元格）与 `CsvNestedMode.COLUMNS`（点分列名）；
- **多级 Schema**：`objectField` 声明嵌套子 Schema（点分列名展开，任意层级），`arrayField` 声明元素 Schema（JSON 数组单元格 + 元素校验）；
- **原生 Token 读取**：默认配置下 `parse(String)` 通过内部 `CsvTokenReader` 直接从 CSV 记录生成 Token；
- **文件/流读写**：文本 UTF-8（可改 `fileCharset`）；
- **参考实现 配置对齐**：`CsvReadFeature`（对齐 `CsvParser.Feature` / `CsvReadFeature`）与
  `CsvWriteFeature`（对齐 `CsvGenerator.Feature` / `CsvWriteFeature`）覆盖 参考实现 2.15～3.3
  官方枚举，`CsvConfigBuilder` 提供 `enable` / `disable` / `configure`；
- **参考实现 `CsvSchema` 对齐**：`quoteChar`、`escapeChar`、`lineSeparator`、`columnSeparator`、
  `arrayElementSeparator`、`nullValue`、`skipFirstDataRow`、`allowComments`、`header`、
  `sortedBy` 列重排均已支持。

## 配置：`CsvConfig`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `delimiter` | `Char` | `,` | 字段分隔符 |
| `header` | `Boolean` | `true` | 是否使用表头；配置了 `schema` 时由 `CsvSchema.header` 决定 |
| `schema` | `CsvSchema?` | `null` | 非 null 时按 Schema 的列顺序与类型读写 |
| `nestedMode` | `CsvNestedMode?` | `null` | 嵌套值读写方式；`null` 表示不支持嵌套（默认，保持旧行为） |
| `maxDepth` | `Int` | `512` | 解析/写出最大嵌套深度，必须为正数 |
| `readFeatures` | `Set<CsvReadFeature>` | `CsvConfig.DEFAULT_READ_FEATURES` | 参考实现 风格读取特性集合 |
| `writeFeatures` | `Set<CsvWriteFeature>` | `CsvConfig.DEFAULT_WRITE_FEATURES` | 参考实现 风格写出特性集合 |

`CsvNestedMode`：

| 值 | 语义 |
|---|---|
| `JSON` | `{...}` / `[...]` 单元格解析为嵌套对象/数组；嵌套节点写出为 JSON 文本单元格 |
| `COLUMNS` | 表头中的点分列名（`address.city`）读写为嵌套对象；写出时展开为点分列名；数组没有列形式，写出嵌套数组抛 `CsvException` |

`CsvConfigBuilder` 方法：

| 方法 | 说明 |
|---|---|
| `field(Class, String, DocumentBaseConverter)` | 字段转换器 |
| `missingFieldPolicy` / `failOnUnknownFields` / `writeNulls` | 通用 Bean 映射选项 |
| `delimiter(Char)` | 分隔符 |
| `header(Boolean)` | 是否使用表头（被 Schema 覆盖） |
| `schema(CsvSchema?)` | 设置 CSV Schema |
| `nestedMode(CsvNestedMode?)` | 启用嵌套单元格 |
| `maxDepth(Int)` | 解析/写出最大嵌套深度（必须为正数） |
| `enable/disable/configure(CsvReadFeature)` | 参考实现 风格读取特性 |
| `enable/disable/configure(CsvWriteFeature)` | 参考实现 风格写出特性 |
| `namingStrategy` / `fileCharset` / `atomicFileWrite` / `createParentDirectories` | 通用 Mapper 便捷选项 |
| `maxInputSize` / `maxStringLength` / `maxNodeDepth` / `maxNodeCount` | Node/输入限制便捷选项 |
| `remoteReferenceResolver` / `beanValidator` | 远程 `$ref` 解析器 / 根对象校验器 |
| `enable/disable/configure(DeserializationFeature)` / `failOnNullForPrimitives` | 通用反序列化特性 |
| `subtype(...)` / `typeProperty(...)` | 多态子类型 |
| `resolveReferences` / `writeReferences` | 本地 `$ref` |
| `build()` | 构建 `CsvConfig` |

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvNestedMode

val mapper = CsvMapper(
    CsvConfig.builder()
        .delimiter(';')
        .header(false)
        .nestedMode(CsvNestedMode.JSON)
        .build(),
)
```

### 参考实现 读写特性

`CsvReadFeature` 与 参考实现 `CsvParser.Feature`（2.x）/ `CsvReadFeature`（3.x）同名同默认值；
`CsvWriteFeature` 与 `CsvGenerator.Feature`（2.x）/ `CsvWriteFeature`（3.x）同名同默认值。
本库覆盖 参考实现 2.15～3.3 的并集（条目 KDoc 标注引入版本）；默认值见“默认”列。

读取特性（`CsvReadFeature`）：

| 特性 | 默认 | 行为 |
|---|---|---|
| `TRIM_SPACES` | 关闭 | 裁剪无引号单元格两侧空白；引号内空白保留 |
| `WRAP_AS_ARRAY` | 关闭 | 本库 Node 根固定数组，等价恒开启；条目仅用于配置对照 |
| `IGNORE_TRAILING_UNMAPPABLE` | 关闭 | 开启后强制忽略 Schema 未映射的多余列（关闭时由 `failOnUnknownColumns` 决定） |
| `SKIP_EMPTY_LINES` | 关闭 | 跳过完全空白行；关闭时空行解析为单空单元格记录 |
| `ALLOW_TRAILING_COMMA` | **开启** | 允许行尾多出一个空列（尾逗号） |
| `ALLOW_COMMENTS` | 关闭 | 允许 `#` 注释行 |
| `FAIL_ON_MISSING_COLUMNS` | 关闭 | 数据行少于 Schema 列数时抛 `CsvParseException` |
| `FAIL_ON_MISSING_HEADER_COLUMNS` | **开启** | 表头缺少 Schema 必填列时抛异常 |
| `INSERT_NULLS_FOR_MISSING_COLUMNS` | 关闭 | 行缺失列插入 null（`FAIL_ON_MISSING_COLUMNS` 优先） |
| `EMPTY_STRING_AS_NULL` | 关闭 | 空单元格读取为 null |
| `TRIM_HEADER_SPACES` | **开启** | 裁剪表头列名两侧空白（参考实现 2.16+） |
| `EMPTY_UNQUOTED_STRING_AS_NULL` | 关闭 | 无引号空单元格读取为 null（本库无法区分引号，等价 `EMPTY_STRING_AS_NULL`） |
| `ONLY_UNQUOTED_NULL_VALUES_AS_NULL` | 关闭 | 只把无引号 nullValue 读取为 null（同上，按“内容等于 nullValue 即 null”处理） |
| `EMPTY_UNQUOTED_STRING_AS_MISSING` | 关闭 | 无引号空单元格按缺失列处理（参考实现 3.2+） |
| `SKIP_EMPTY_ROWS` | 关闭 | 跳过仅由分隔符组成的行（如 `,,`，参考实现 3.2+） |
| `FAIL_ON_DUPLICATE_HEADER_COLUMNS` | **开启** | 表头重复列名抛异常；关闭后按最后一列取值（参考实现 3.2+） |
| `CASE_INSENSITIVE_HEADERS` | 关闭 | 表头与 Schema 列名忽略大小写匹配（参考实现 3.2+） |

写出特性（`CsvWriteFeature`）：

| 特性 | 默认 | 行为 |
|---|---|---|
| `STRICT_CHECK_FOR_QUOTING` | 关闭 | 关闭时使用宽松引号检查（对齐 参考实现 默认）；开启后只在确实需要时加引号 |
| `OMIT_MISSING_TAIL_COLUMNS` | 关闭 | 省略行尾缺失列 |
| `ALWAYS_QUOTE_STRINGS` | 关闭 | 字符串值（含表头）强制加引号 |
| `ALWAYS_QUOTE_EMPTY_STRINGS` | 关闭 | 空字符串强制加引号 |
| `ESCAPE_QUOTE_CHAR_WITH_ESCAPE_CHAR` | 关闭 | 引号用转义字符转义，而不是双写 |
| `ESCAPE_CONTROL_CHARS_WITH_ESCAPE_CHAR` | 关闭 | `\b`/`\t`/`\n`/`\f`/`\r` 用转义字符输出（Commons CSV 可读回） |
| `ALWAYS_QUOTE_NUMBERS` | 关闭 | 数字值强制加引号（参考实现 2.16+） |
| `WRITE_LINEFEED_AFTER_LAST_ROW` | **开启** | 最后一行后写出换行；关闭后输出不以行分隔符结尾（参考实现 2.17+） |
| `QUOTE_STRINGS_WITH_LEADING_TRAILING_WHITESPACE` | 关闭 | 首/尾空白（≤0x20）字符串强制加引号（参考实现 3.2+） |

> **默认引号行为**：对齐 参考实现 默认（`STRICT_CHECK_FOR_QUOTING` 关闭）时采用宽松检查，
> 值中出现小于 `minSafeChar` 的字符（默认逗号分隔时包括空格、制表符、`!`、`#` 等）、
> 引号或转义字符就会加引号；数字与布尔值默认不加引号（`ALWAYS_QUOTE_NUMBERS` 除外）。
> 需要“最小必要引号”时开启 `STRICT_CHECK_FOR_QUOTING`。

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvReadFeature
import com.mingliqiye.document.format.csv.CsvWriteFeature

val mapper = CsvMapper(
    CsvConfig.builder()
        .enable(CsvReadFeature.SKIP_EMPTY_LINES)
        .enable(CsvReadFeature.TRIM_SPACES)
        .disable(CsvReadFeature.ALLOW_TRAILING_COMMA)
        .enable(CsvWriteFeature.ALWAYS_QUOTE_STRINGS)
        .configure(CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW, false)
        .build(),
)
```

## CSV Schema

`CsvSchema` 实现通用 `DocumentSchema`，字段使用 `com.mingliqiye.document.schema.SchemaType`：

| `SchemaType` | CSV 单元格语义 |
|---|---|
| `STRING` | 原样字符串；空单元格读为空串 |
| `INT` | `toIntOrNull`，失败抛 `CsvParseException` |
| `LONG` | `toLongOrNull`，失败抛异常 |
| `DOUBLE` | 必须有限值，失败抛异常 |
| `DECIMAL` | `BigDecimal`，失败抛异常 |
| `BOOLEAN` | `true` / `false`（忽略大小写） |
| `BYTES` | Base64 解码为 `BinaryNode` |
| `OBJECT` | 需要嵌套子 Schema（`objectField`），否则需要 JSON 模式 |
| `ARRAY` | 需要元素 Schema（`arrayField`）或 JSON 模式 |
| `MAP` / `NULL` | 不支持作为 CSV 标量列 |

`CsvSchema` 关键属性与方法：

| 成员 | 说明 |
|---|---|
| `name` | Schema 名称 |
| `fields` | 顶层字段列表，顺序即列顺序 |
| `header` | 是否读写表头行（优先于 `CsvConfig.header`） |
| `failOnUnknownColumns` | 遇到未声明列是否报错 |
| `nestedObjects: Map<String, CsvSchema>` | `OBJECT` 字段的子 Schema |
| `nestedArrays: Map<String, CsvSchema>` | `ARRAY` 字段的元素 Schema |
| `columnNames: List<String>` | 展开后的列名（嵌套对象展开为点分列名） |
| `columnSeparator: Char?` | 列分隔符；null 表示沿用 `CsvConfig.delimiter` |
| `quoteChar: Char?` | 引号字符；null 表示关闭引号，默认 `"` |
| `escapeChar: Char?` | 转义字符；null 表示不使用，默认 null |
| `lineSeparator: String` | 写出用行分隔符，默认 `\n` |
| `arrayElementSeparator: String?` | 数组元素分隔符；null 表示数组使用 JSON 文本（本库扩展） |
| `nullValue: String?` / `nullValueText` | null 写出文本；null 表示空串 |
| `skipFirstDataRow: Boolean` | 是否跳过首条数据行（对齐 参考实现），默认 false |
| `allowComments: Boolean` | 是否允许 `#` 注释行，默认 false |
| `sortedBy(vararg String)` | 返回按指定列顺序重排的新 Schema |
| `effectiveColumnSeparator(fallback)` | 计算实际列分隔符 |
| `field(name)` / `nestedObject(name)` / `nestedArray(name)` | 查询 |

`CsvSchemaBuilder` 方法：

| 方法 | 说明 |
|---|---|
| `field(name, type, required = true, nullable = false, description = null)` | 添加字段 |
| `addField(SchemaField)` | 添加已构建字段 |
| `string / int / long / double / decimal / boolean / bytes(name, required = true, nullable = false)` | 便捷方法 |
| `objectField(name, nested: CsvSchema, required = true, nullable = false, description = null)` | 带子 Schema 的对象字段，展开为点分列名 |
| `arrayField(name, element: CsvSchema? = null, required = true, nullable = false, description = null)` | 数组字段；`element` 非空时按元素 Schema 校验/排序 |
| `header(Boolean)` | 是否读写表头 |
| `failOnUnknownColumns(Boolean)` | 未声明列是否报错 |
| `addColumn(name, required = true, nullable = false)` / `addColumns(vararg names)` | 字符串列便捷方法（对齐 参考实现 `addColumn` / `addColumns`） |
| `columnSeparator(Char)` / `quoteChar(Char?)` / `escapeChar(Char?)` | 列分隔符 / 引号 / 转义字符（传 null 关闭引号或转义） |
| `lineSeparator(String)` / `arrayElementSeparator(String?)` / `nullValue(String?)` | 行分隔符 / 数组元素分隔符 / null 文本 |
| `skipFirstDataRow(Boolean)` / `allowComments(Boolean)` | 跳过首条数据行 / 允许 `#` 注释行 |
| `orderColumns(vararg names)` | 构建时重排顶层列顺序 |
| `build()` | 构建 `CsvSchema`（校验列名非空、顶层不重复、展开后列名不冲突、嵌套字段类型匹配） |

#### 与 参考实现 `CsvSchema.Builder` 对照

| 参考实现 `CsvSchema.Builder` | 本库 `CsvSchemaBuilder` |
|---|---|
| `setColumnSeparator(char)` | `columnSeparator(Char)` |
| `setQuoteChar(char)` / `withoutQuoteChar()` | `quoteChar(Char?)`（null 关闭） |
| `setEscapeChar(char)` / `withoutEscapeChar()` | `escapeChar(Char?)`（null 关闭） |
| `setLineSeparator(String)` | `lineSeparator(String)` |
| `setArrayElementSeparator(String)` / `withoutArrayElementSeparator()` | `arrayElementSeparator(String?)`（默认 null 表示 JSON 数组，参考实现 默认 `;`） |
| `setNullValue(String)` | `nullValue(String?)`（默认 null 等价空串） |
| `setUseHeader` / `withHeader` / `withoutHeader` | `header(Boolean)` |
| `setSkipFirstDataRow(boolean)` | `skipFirstDataRow(Boolean)` |
| `setAllowComments` / `withComments` / `withoutComments` | `allowComments(Boolean)` |
| `setColumnReordering` / `setStrictHeaders` | 不支持：本库按列名映射，表头列顺序任意；未提供严格列序校验 |
| `addColumn` / `addColumns` | `addColumn` / `addColumns` |
| `addNumberColumn` / `addBooleanColumn` / `addArrayColumn` | `int/long/double/decimal` / `boolean` / `arrayField` |
| `sortedBy(String...)` | `CsvSchema.sortedBy(String...)` / `CsvSchemaBuilder.orderColumns(...)` |

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvSchema
import com.mingliqiye.document.schema.SchemaType

val schema = CsvSchema.builder("user")
    .field("name", SchemaType.STRING)
    .field("age", SchemaType.INT)
    .field("tags", SchemaType.ARRAY)      // 数组列：JSON 文本单元格（需要 nestedMode JSON）
    .build()

val mapper = CsvMapper(
    CsvConfig.builder()
        .schema(schema)
        .nestedMode(CsvNestedMode.JSON)
        .build(),
)
```

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `CsvMapper()` | 默认：逗号分隔、带表头、无 Schema、不支持嵌套 |
| `CsvMapper(config: DocumentMapperConfig)` | 通用配置 |
| `CsvMapper(config: CsvConfig)` | CSV 配置 |
| `CsvMapper(schema: CsvSchema)` | 按 Schema 读写，分隔符为默认逗号；`CsvSchema.header` 优先 |

`CsvMapper.builder()` 返回 `CsvMapperBuilder`：

| 方法 | 说明 |
|---|---|
| `schema(CsvSchema?)` | 设置 Schema，覆盖 `config(CsvConfig)` 里的 `schema` |
| 其余方法 | 继承 `DocumentMapperBuilder`：`addSerializer` / `addDeserializer` / `addConverter` / `addModule` / `addModules` / `config` / `objectInstantiator` / `build` |

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper

val mapper = CsvMapper.builder()
    .config(CsvConfig.builder().delimiter(';').build())
    .schema(schema)
    .build()
```

## 读取 API

### Mapper 入口

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType)` | 默认配置走内部 `CsvTokenReader`；有 Schema 或启用嵌套模式时走 Node 路径 |
| `parse(source: ByteArray, targetType)` | 按 `fileCharset` 解码后解析 |
| `parse(file / path, targetType)` | 文件 → 实体 |
| `parse(input: InputStream, targetType)` | 流 → 实体 |
| `parse(reader: Reader, targetType)` | 字符流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |

读取根节点固定为 `ArrayNode`（每行一个 `ObjectNode`）；空文本返回空数组。读表格为 Bean 列表要用泛型目标类型：

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.csv.CsvMapper

val mapper = CsvMapper()
val csv = """
    name,age
    小于,18
    阿明,20
""".trimIndent()

val users = mapper.parse(csv, DocumentTypeReference.listType(User::class.java))
```

### 独立读取器

| 类/函数 | 说明 |
|---|---|
| `CsvReader(source, delimiter = ',', header = true, nestedMode = null)` | 无 Schema 读取器，`read(): Node` |
| `CsvReader.parse(source, delimiter, header, nestedMode)` | 静态解析 |
| `String.toCsvNode(delimiter = ',', header = true, nestedMode = null)` | 字符串 → Node 扩展函数 |
| `CsvSchemaReader(source, schema, delimiter = ',', nestedMode = null)` | 按 Schema 读取器，`read(): Node` |

```kotlin
import com.mingliqiye.document.format.csv.CsvReader
import com.mingliqiye.document.format.csv.CsvSchemaReader

val node = CsvReader("name,age\n小于,18\n").read()

val schemaNode = CsvSchemaReader(
    source = "name,age\n小于,18\n",
    schema = schema,
).read()

val extended = "a,b\n1,2\n".toCsvNode()
```

读取规则（无 Schema）：

- 有表头时按列名映射；无表头时列名为 `column1`、`column2`……；
- 表头重复抛 `CsvParseException`；
- 单元格按 `true` / `false` / 数字 / 字符串推断；
- 行比表头短时缺失单元格按空串处理；行比表头长时多余单元格忽略（无表头时按该行实际宽度生成列名）。

读取规则（有 Schema）：

- 有表头时按列名映射（列顺序任意）；无表头时按展开后的列顺序对应；
- 单元格按 `SchemaField` 类型解析，失败抛 `CsvParseException`；
- 空单元格：`nullable` → `null`，`STRING` → 空串，必填字段报错；
- `required` 的列缺失（表头没有、无表头行过短）时报错；
- 未声明列由 `failOnUnknownColumns` 控制（默认忽略，true 时报错）；
- 嵌套对象字段按子 Schema 与点分列名读取；嵌套对象所有单元格为空时按对象缺席处理；
- 可选嵌套对象整体缺席时，其内部必填列不报错；
- 带元素 Schema 的数组以 JSON 文本单元格读取，每个元素按元素 Schema 校验。

### 嵌套单元格

`CsvNestedMode.JSON`：

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

val node = mapper.parse(csv, com.mingliqiye.document.core.Node::class.java)
```

- 只有以 `{` / `[` 开头的单元格会被解析为嵌套节点；非法 JSON 抛 `CsvParseException`；
- 不支持裸 JSON 标量单元格（如 `123`、`true`、`"text"` 不会被当作 JSON 解析）。

`CsvNestedMode.COLUMNS`：

```kotlin
val mapper = CsvMapper(
    CsvConfig.builder()
        .nestedMode(CsvNestedMode.COLUMNS)
        .build(),
)

val csv = """
    name,address.city,address.geo.lat
    小于,杭州,30.1
""".trimIndent()
```

- 表头中的点分列名组装为嵌套对象；
- 列前缀冲突（既有 `address` 又有 `address.city`）抛 `CsvParseException`；
- 数组没有列形式，`COLUMNS` 模式下写出嵌套数组抛 `CsvException`。

### Token 路径

`CsvMapper.parse(String)` 在默认配置（无 Schema、无嵌套模式）下使用内部 `CsvTokenReader`：

- 直接产生 `START_ARRAY` → 每行 `START_OBJECT` / `FIELD_NAME` / 值 / `END_OBJECT` → `END_ARRAY` 的 Token 流；
- 字段类型推断与 `CsvReader` 一致；
- 同时应用 `CsvReadFeature` 的空单元格/null、跳过空行、重复表头、大小写不敏感等读取特性；
- 有 Schema 或 `nestedMode` 时回退 `NodeTokenReader`（先构建 Node 树并执行 Node 限制）；
- 默认 Token 路径不执行通用 Node 树限制（`maxNodeDepth` / `maxNodeCount` / `maxStringLength`）；需要这些限制时配置 Schema 或嵌套模式走 Node 路径，或用 `CsvReader` 手动构建 Node 后自行校验。

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → CSV 文本 |
| `formatPretty(value)` | 与 `format` 输出相同（CSV 无 pretty 概念） |
| `formatBytes(value)` | 实体 → 按 `fileCharset` 编码的字节 |
| `format(value, file / path)` | 写文件 |
| `format(value, output: OutputStream)` | 先编码为字节再写出（无流式 Token 写出器） |
| `format(value, writer: Writer)` | 直接写字符流 |
| `Node.toCsvString(delimiter = ',', header = true, nestedMode = null, maxDepth = 512, writeFeatures = CsvConfig.DEFAULT_WRITE_FEATURES)` | 扩展函数：Node → CSV 文本 |
| `Node.toCsvString(schema, delimiter = ',', nestedMode = null, maxDepth = 512, writeFeatures = ...)` | 按 Schema 写出 |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `format(value, OutputStream)` | ❌ | 先构造完整 `String` 再编码 |
| `format(value, File/Path)` | ❌ | 同上 |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

写出规则（无 Schema）：

- 根节点可以是对象（单行）或数组（每行必须是对象）；其他类型抛 `CsvException`；
- 表头取所有行字段名的并集，保持字段首次出现的顺序；
- 单元格：`null` → 空串（Schema 配置 `nullValue` 时用该文本），字符串 / 数字 `raw` / 布尔 / Base64 二进制；对象/数组只有在 `JSON` 模式下写成 JSON 文本，否则抛 `CsvException`；
- 引号规则由 `CsvWriteFeature` 控制：默认对齐 参考实现 的宽松检查（可能需要引号之外的值也加引号），
  开启 `STRICT_CHECK_FOR_QUOTING` 后使用最小必要引号；数字/布尔值默认不加引号；
- 记录分隔符默认 `\n`，Schema 可用 `lineSeparator` 覆盖；`WRITE_LINEFEED_AFTER_LAST_ROW` 控制末尾换行。

写出规则（有 Schema）：

- 列顺序与表头由 Schema 决定；行中未声明顶层字段默认忽略，是否报错由 `failOnUnknownColumns` 控制；
- 缺少 `required` 列抛 `CsvException`；可选嵌套对象缺席时其列写成空单元格；
- 嵌套对象按子 Schema 展开，嵌套对象上的额外字段忽略；
- 带元素 Schema 的数组按元素 Schema 规范化后写成 JSON 数组；配置 `arrayElementSeparator`
  后改为分隔文本数组（元素 Schema 必须是单标量字段）。

```kotlin
import com.mingliqiye.document.format.csv.toCsvString
import com.mingliqiye.document.core.Node

val node = mapper.parse("name,age\n小于,18\n", Node::class.java)
println(node.toCsvString())
// name,age
// 小于,18
```

## 类型映射

| Node | CSV 读取（无 Schema） | CSV 写出（无 Schema） |
|---|---|---|
| `ObjectNode` | 一行数据 | 一行；根对象按单行 |
| `ArrayNode` | 根为行数组 | 根为多行 |
| `StringNode` | 无法推断为其它类型时 | 原样单元格文本 |
| `NumberNode` | 可解析为 `BigDecimal` 的单元格 | `raw` 文本 |
| `BooleanNode` | `true` / `false` | `true` / `false` |
| `NullNode` | 不会从单元格读出（空单元格读为空串） | 空单元格 |
| `BinaryNode` | 不会从单元格读出（Base64 文本读为字符串） | Base64 字符串 |

Schema 模式下读取可以产生 `BinaryNode`（`BYTES` 列）和 `NullNode`（`nullable` 列为空）；嵌套 JSON 单元格可以产生对象/数组节点。

## 示例

### 1. 字符串 → Bean 列表

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.csv.CsvMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = CsvMapper()
val csv = """
    name,age
    小于,18
    阿明,20
""".trimIndent()

val users = mapper.parse(csv, DocumentTypeReference.listType(User::class.java))
val text = mapper.format(users)
```

### 2. 无表头

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper

val mapper = CsvMapper(CsvConfig.builder().header(false).build())
val node = mapper.parse("1,2\n3,4\n", com.mingliqiye.document.core.Node::class.java)
val row = node.asArray()[0].asObject()
println(row["column1"]?.asNumber()?.toInt())   // 1
```

### 3. 自定义分隔符

```kotlin
val mapper = CsvMapper(CsvConfig.builder().delimiter(';').build())
val users = mapper.parse("name;age\n小于;18\n", DocumentTypeReference.listType(User::class.java))
```

### 4. 类型推断

```kotlin
val node = CsvReader("a,b,c,d\ntrue,42,3.14,text\n").read()
val row = node.asArray()[0].asObject()
println(row["a"]?.isBoolean)   // true
println(row["b"]?.isNumber)    // true
println(row["c"]?.isNumber)    // true
println(row["d"]?.isString)    // true
```

### 5. 表头重复报错

```kotlin
import com.mingliqiye.document.format.csv.exception.CsvParseException

val error = runCatching {
    CsvReader("name,name\n1,2\n").read()
}.exceptionOrNull()
check(error is CsvParseException)
```

### 6. CSV Schema 读取

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvSchema
import com.mingliqiye.document.schema.SchemaType

val schema = CsvSchema.builder("user")
    .field("name", SchemaType.STRING)
    .field("age", SchemaType.INT)
    .build()

val mapper = CsvMapper(schema)
val users = mapper.parse("name,age\n小于,18\n", DocumentTypeReference.listType(User::class.java))
check(users.size == 1)
```

### 7. Schema 空单元格 / nullable

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.CsvSchema

val schema = CsvSchema.builder("item")
    .string("name")
    .int("count", required = false, nullable = true)
    .build()

val node = CsvMapper(schema).parse("name,count\na,\n", Node::class.java)
val row = node.asArray()[0].asObject()
check(row["count"]?.isNull == true)     // nullable + 空单元格 -> NullNode
```

### 8. 多级 Schema（嵌套对象 + 数组）

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper
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
    .arrayField("members", member)
    .build()

println(person.columnNames)
// [name, address.city, address.zip, address.geo.lat, address.geo.lon, members]

val mapper = CsvMapper(person)
val rows = mapper.parse(
    """
    name,address.city,address.zip,address.geo.lat,address.geo.lon,members
    小于,杭州,310000,30.1,120.2,"[{""name"":""小于"",""age"":18}]"
    """.trimIndent(),
    com.mingliqiye.document.core.Node::class.java,
)
val first = rows.asArray()[0].asObject()
println(first["address"]?.asObject()?.get("city")?.asString())   // 杭州
println(first["members"]?.asArray()?.size)                       // 1
```

### 9. JSON 文本单元格

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvNestedMode

val mapper = CsvMapper(
    CsvConfig.builder().nestedMode(CsvNestedMode.JSON).build(),
)

val node = mapper.parse(
    """name,address
小于,"{""city"":""杭州""}"
""".trimIndent(),
    com.mingliqiye.document.core.Node::class.java,
)
println(node.asArray()[0].asObject()["address"]?.asObject()?.get("city")?.asString()) // 杭州
```

### 10. 点分列名

```kotlin
val mapper = CsvMapper(
    CsvConfig.builder().nestedMode(CsvNestedMode.COLUMNS).build(),
)

val node = mapper.parse(
    """
    name,address.city
    小于,杭州
    """.trimIndent(),
    com.mingliqiye.document.core.Node::class.java,
)
println(node.asArray()[0].asObject()["address"]?.asObject()?.get("city")?.asString()) // 杭州
```

### 11. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(users, File("build/users.csv"))
val fromFile = mapper.parse(File("build/users.csv"), DocumentTypeReference.listType(User::class.java))

mapper.formatPretty(users, Paths.get("build/users2.csv"))
val fromPath = mapper.parse(Paths.get("build/users2.csv"), DocumentTypeReference.listType(User::class.java))
```

### 12. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(users, output)
val restored = mapper.parse(
    ByteArrayInputStream(output.toByteArray()),
    DocumentTypeReference.listType(User::class.java),
)
```

### 13. Java 示例

```java
import com.mingliqiye.document.core.DocumentTypeReference;
import com.mingliqiye.document.format.csv.CsvMapper;

import java.util.List;

public class CsvDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        CsvMapper mapper = new CsvMapper();

        String csv = "name,age\n小于,18\n";
        List<User> users = mapper.parse(
                csv,
                DocumentTypeReference.listType(User.class));

        System.out.println(users.get(0).name + ":" + users.get(0).age);
        System.out.println(mapper.format(users));
    }
}
```

### 14. 无嵌套模式下的嵌套报错

```kotlin
import com.mingliqiye.document.format.csv.exception.CsvException

val mapper = CsvMapper()
val node = com.mingliqiye.document.core.ObjectNode.of(
    "user" to com.mingliqiye.document.core.ObjectNode.of(
        "name" to com.mingliqiye.document.core.StringNode.of("小于"),
    ),
)
val error = runCatching { mapper.format(node) }.exceptionOrNull()
check(error is CsvException)   // 默认不支持嵌套单元格
```

### 15. 修改分隔符 + 无表头往返

```kotlin
import com.mingliqiye.document.format.csv.CsvConfig

val mapper = CsvMapper(CsvConfig.builder().delimiter('|').header(false).build())
val users = listOf(User().apply { name = "小于"; age = 18 })
val text = mapper.format(users)
println(text)   // 小于|18
val restored = mapper.parse(text, DocumentTypeReference.listType(User::class.java))
check(restored[0].name == "小于")
```

### 16. 参考实现 读写特性

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.CsvConfig
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.csv.CsvReadFeature
import com.mingliqiye.document.format.csv.CsvWriteFeature

val mapper = CsvMapper(
    CsvConfig.builder()
        .enable(CsvReadFeature.SKIP_EMPTY_LINES)
        .enable(CsvReadFeature.EMPTY_STRING_AS_NULL)
        .enable(CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
        .disable(CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW)
        .build(),
)

val node = mapper.parse("name,note\n\n小于,\n", Node::class.java)
// SKIP_EMPTY_LINES：空行被跳过；EMPTY_STRING_AS_NULL：note 空单元格读取为 null
check(node.asArray().size == 1)
check(node.asArray()[0].asObject()["note"]!!.isNull)
```

### 17. CsvSchema 格式选项

```kotlin
import com.mingliqiye.document.format.csv.CsvSchema
import com.mingliqiye.document.format.csv.CsvSchemaReader

val schema = CsvSchema.builder("event")
    .string("name")
    .string("note", required = false, nullable = true)
    .columnSeparator(';')
    .quoteChar('\'')
    .nullValue("NULL")
    .allowComments(true)
    .orderColumns("note", "name")
    .build()

val node = CsvSchemaReader.parse(
    "# comment\nnote;name\nNULL;小于\n",
    schema,
).asArray()[0].asObject()
check(node["note"]!!.isNull)
check(node["name"]?.asString() == "小于")
```

## 深度限制

```kotlin
val mapper = CsvMapper(
    CsvConfig.builder()
        .maxDepth(512)
        .build(),
)
```

默认 `512`；统计数组、行对象、嵌套 Schema/点分列名与 JSON 单元格深度。
超过限制抛 `CsvParseException`，`parseStream` 同样生效。

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 根节点类型 | 读取固定 `ArrayNode`；写出接受对象（单行）或数组（多行） |
| 无表头 | 列名 `column1`、`column2`…… |
| 表头重复 | 默认抛 `CsvParseException`（`FAIL_ON_DUPLICATE_HEADER_COLUMNS` 默认开启）；关闭后按最后一列取值 |
| 默认不跳过空行 | `SKIP_EMPTY_LINES` 默认关闭（对齐 参考实现），空行读成单空单元格记录；需要跳过时显式开启 |
| 默认引号规则（宽松） | 对齐 参考实现 默认：部分无需引号的值（含空格、`#`、反斜杠等）也会加引号；需要最小必要引号时开启 `STRICT_CHECK_FOR_QUOTING` |
| `WRAP_AS_ARRAY` | 本库 Node 根固定数组，等价恒开启，不提供“根对象序列”表示 |
| 引号 / 无引号区分 | Commons CSV 不暴露单元格是否带引号；`EMPTY_UNQUOTED_STRING_AS_NULL`、`ONLY_UNQUOTED_NULL_VALUES_AS_NULL`、`EMPTY_UNQUOTED_STRING_AS_MISSING` 按“全部视为无引号”近似 |
| 自定义 `lineSeparator` | 仅写出生效；读取按 `\r` / `\n` / `\r\n` 标准换行解析（与 参考实现 一致） |
| `arrayElementSeparator` | 默认 null 表示 JSON 文本数组（本库扩展）；显式设置后为 参考实现 风格分隔文本，元素必须是单标量字段且元素内不能包含分隔符 |
| `ESCAPE_CONTROL_CHARS_WITH_ESCAPE_CHAR` | 仅转义 Commons CSV 可读回的 `\b` / `\t` / `\n` / `\f` / `\r`；其余控制字符原样输出 |
| `columnReordering` / `strictHeaders` | 未提供：本库始终按列名映射，表头列顺序任意 |
| 行字段数不一致 | 短行缺失单元格按空串；长行多余单元格忽略（无 Schema）；Schema 模式可配 `FAIL_ON_MISSING_COLUMNS` / `INSERT_NULLS_FOR_MISSING_COLUMNS` |
| 默认不支持嵌套 | 对象/数组单元格写出抛 `CsvException` |
| `JSON` 模式的非 JSON 单元格 | 只有 `{` / `[` 开头的单元格会尝试解析；非法 JSON 抛 `CsvParseException` |
| `JSON` 模式裸标量 | 不支持（`123` 等按普通文本处理） |
| `COLUMNS` 模式数组 | 写出嵌套数组抛 `CsvException` |
| `COLUMNS` 模式列前缀冲突 | `CsvParseException` / `CsvException` |
| Schema 必填列缺失 / 单元格类型不符 | `CsvParseException`（读取）/ `CsvException`（写出） |
| Schema 未声明列 | 默认忽略；`failOnUnknownColumns=true` 报错 |
| 多级 Schema 子 Schema 的 `header` | 不参与列展开（嵌套对象固定点分列名） |
| 多级 Schema 列名冲突 | `build()` 抛 `IllegalArgumentException` |
| `formatPretty` | 与 `format` 相同 |
| `BinaryNode` / `NullNode`（无 Schema） | 不保型：读回字符串 / 空串 |
| 嵌套对象所有单元格为空 | 读取按对象缺席处理（便于可选对象往返） |

## 与通用 `DocumentMapper` 的差异

- 构造：`CsvConfig` / `CsvSchema` 专属配置与 `CsvMapper.builder().schema(...)`；
- 根节点固定为数组（读取）/ 对象或数组（写出）；
- `parse(String)` 默认走内部 `CsvTokenReader`，有 Schema/嵌套配置时回退 Node 路径；
- `formatPretty` 与 `format` 相同；
- CSV 专属：`CsvReader`、`CsvWriter`（内部）、`CsvSchemaReader` / `CsvSchemaWriter`、`CsvSchema` / `CsvSchemaBuilder`、`CsvNestedMode`、`String.toCsvNode()` / `Node.toCsvString()`；
- 额外提供 参考实现 对齐配置：`CsvReadFeature` / `CsvWriteFeature` 的 `enable/disable/configure`，以及 `CsvSchema` 的引号/转义/行分隔符/列分隔符/数组元素分隔符/null 文本/跳过首行/注释/列重排选项。

## 相关链接

- [格式总览](./)
- [Schema 模块](../modules/schema.md)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [format-csv 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-csv/README.md)
