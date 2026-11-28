---
title: CSV
description: format-csv 模块：CsvMapper、CsvConfig、CSV Schema、嵌套单元格与多级 Schema 读写
---

# CSV

`format-csv` 提供 CSV 读写，核心类型是 `CsvMapper`，可选 `CsvSchema` 做列类型约束。

CSV 只能表达平坦表格：根节点固定为 `ArrayNode`，每个元素是一行 `ObjectNode`；写出时根对象按单行处理。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:csv:2.0.0")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `csv` |
| 版本 | `2.0.0` |
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
- **文件/流读写**：文本 UTF-8（可改 `fileCharset`）。

## 配置：`CsvConfig`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `delimiter` | `Char` | `,` | 字段分隔符 |
| `header` | `Boolean` | `true` | 是否使用表头；配置了 `schema` 时由 `CsvSchema.header` 决定 |
| `schema` | `CsvSchema?` | `null` | 非 null 时按 Schema 的列顺序与类型读写 |
| `nestedMode` | `CsvNestedMode?` | `null` | 嵌套值读写方式；`null` 表示不支持嵌套（默认，保持旧行为） |

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
| `build()` | 构建 `CsvSchema`（校验列名非空、顶层不重复、展开后列名不冲突、嵌套字段类型匹配） |

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
| `Node.toCsvString(delimiter = ',', header = true, nestedMode = null)` | 扩展函数：Node → CSV 文本 |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `format(value, OutputStream)` | ❌ | 先构造完整 `String` 再编码 |
| `format(value, File/Path)` | ❌ | 同上 |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

写出规则（无 Schema）：

- 根节点可以是对象（单行）或数组（每行必须是对象）；其他类型抛 `CsvException`；
- 表头取所有行字段名的并集，保持字段首次出现的顺序；
- 单元格：`null` → 空串，字符串 / 数字 `raw` / 布尔 / Base64 二进制；对象/数组只有在 `JSON` 模式下写成 JSON 文本，否则抛 `CsvException`；
- 使用 commons-csv 标准引号规则（含分隔符、引号、换行的字段自动加引号）；
- 记录分隔符固定 `\n`。

写出规则（有 Schema）：

- 列顺序与表头由 Schema 决定；行中未声明顶层字段默认忽略，是否报错由 `failOnUnknownColumns` 控制；
- 缺少 `required` 列抛 `CsvException`；可选嵌套对象缺席时其列写成空单元格；
- 嵌套对象按子 Schema 展开，嵌套对象上的额外字段忽略；
- 带元素 Schema 的数组始终按元素 Schema 规范化后写成 JSON 数组。

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

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 根节点类型 | 读取固定 `ArrayNode`；写出接受对象（单行）或数组（多行） |
| 无表头 | 列名 `column1`、`column2`…… |
| 表头重复 | `CsvParseException` |
| 行字段数不一致 | 短行缺失单元格按空串；长行多余单元格忽略（无 Schema） |
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
- CSV 专属：`CsvReader`、`CsvWriter`（内部）、`CsvSchemaReader` / `CsvSchemaWriter`、`CsvSchema` / `CsvSchemaBuilder`、`CsvNestedMode`、`String.toCsvNode()` / `Node.toCsvString()`。

## 相关链接

- [格式总览](./)
- [Schema 模块](../modules/schema.md)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [format-csv 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-csv/README.md)
