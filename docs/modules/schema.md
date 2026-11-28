# schema 模块

`schema` 是格式无关的文档 Schema 模块。它只描述「一个文档对象有哪些字段、每个字段的
类型/必填/可空/描述」，不绑定任何格式，也不做校验或编解码；具体格式模块把自己的
Schema 适配到这套接口，`openapi` 再把它们翻译成 OpenAPI 3.0 文档。

- 坐标：`com.mingliqiye.document:schema`
- 当前版本：`2.0.0`
- 包名：`com.mingliqiye.document.schema`
- 目标字节码：JDK 8
- 公开依赖：`com.mingliqiye.document:core`（`api`）

## 职责

| 能力 | 类型 |
|---|---|
| 文档对象结构描述 | `DocumentSchema` |
| 字段定义 | `SchemaField` |
| 字段类型枚举 | `SchemaType` |
| 链式构建 | `DocumentSchemaBuilder` |
| 默认实现 | `SimpleDocumentSchema`（`internal`） |

模块保持最小化：没有 `validate`、没有 JSON Schema 关键字、没有嵌套 Schema 引用。
需要这些能力时由格式模块或 `openapi` 模块在自身层面补充。

> 注意：`format-json` 里的 `JsonSchema` / `JsonSchemaValidationException` 是独立的
> JSON Schema draft-07 子集实现，**不依赖**本模块，不要把两者混淆。

## 依赖关系

```text
core ◄── api ── schema ◄── api ── openapi
  ▲                ▲
  │ api            │ api
format-csv    format-avro / format-protobuf
```

- `schema` 只依赖 `core`；`SimpleDocumentSchema` 自身不直接使用 `Node`，但
  `DocumentSchema` 的消费方（如 OpenAPI 生成）需要 `core` 的节点类型。
- 格式模块对 `schema` 是 `api` 依赖：`CsvSchema`、`AvroSchema`、`ProtobufSchema`
  都实现 `DocumentSchema`，因此使用方可以直接把它们当 `DocumentSchema` 传递。
- `openapi` 依赖 `schema`，并提供 `DocumentSchema.toOpenApi(config)` 扩展。

## SchemaType

```kotlin
public enum class SchemaType {
    STRING,
    INT,
    LONG,
    DOUBLE,
    DECIMAL,
    BOOLEAN,
    BYTES,
    ARRAY,
    OBJECT,
    MAP,
    NULL,
}
```

| 值 | 语义 | OpenAPI 映射（openapi 模块） |
|---|---|---|
| `STRING` | 字符串 | `{"type":"string"}` |
| `INT` | 32 位整数 | `{"type":"integer","format":"int32"}` |
| `LONG` | 64 位整数 | `{"type":"integer","format":"int64"}` |
| `DOUBLE` | 浮点数 | `{"type":"number","format":"double"}` |
| `DECIMAL` | 十进制数 | `{"type":"number"}`（不声明 format） |
| `BOOLEAN` | 布尔 | `{"type":"boolean"}` |
| `BYTES` | 二进制/Base64 | `{"type":"string","format":"byte"}` |
| `ARRAY` | 数组 | `{"type":"array","items":{"type":"string"}}`（默认元素 string） |
| `OBJECT` | 自由对象 | `{"type":"object"}` |
| `MAP` | Map | `{"type":"object","additionalProperties":{"type":"string"}}` |
| `NULL` | 仅 null | OpenAPI 3.0 无法表达，生成时抛 `OpenApiSchemaException` |

`SchemaType` 只描述**类型类别**，不携带元素类型、枚举取值、嵌套结构或 Schema 引用；
这些细节在 `openapi` 模块通过字段类型覆盖补充（见 [openapi](./openapi.md)）。

## DocumentSchema

```kotlin
public interface DocumentSchema {
    val name: String
    val fields: List<SchemaField>
    fun field(name: String): SchemaField?

    companion object {
        fun of(name: String, vararg fields: SchemaField): DocumentSchema
    }
}
```

| 成员 | 类型 | 说明 |
|---|---|---|
| `name` | `String` | Schema 名称；OpenAPI 中作为 component key，需满足 `[A-Za-z0-9._-]+` |
| `fields` | `List<SchemaField>` | 字段列表，保持声明顺序 |
| `field(name)` | `SchemaField?` | 按字段名查找；不存在返回 `null` |
| `DocumentSchema.of(...)` | `DocumentSchema` | 便捷工厂，创建 `SimpleDocumentSchema` |

查找是 O(1)：`SimpleDocumentSchema` 构造时用 `associateBy` 建立名称索引。

## SchemaField

```kotlin
public class SchemaField(
    public val name: String,
    public val type: SchemaType,
    public val required: Boolean = true,
    public val nullable: Boolean = false,
    public val description: String? = null,
)
```

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | `String` | 必填 | 字段名 |
| `type` | `SchemaType` | 必填 | 字段类型 |
| `required` | `Boolean` | `true` | 是否必填 |
| `nullable` | `Boolean` | `false` | 是否允许 `null` |
| `description` | `String?` | `null` | 字段描述 |

`SchemaField.of(name, type, required, nullable)` 提供不带描述的重载；需要描述时直接构造。

```kotlin
val idField = SchemaField(
    name = "id",
    type = SchemaType.LONG,
    required = true,
    nullable = false,
    description = "用户 id",
)
```

## DocumentSchemaBuilder

```kotlin
public class DocumentSchemaBuilder(private val name: String) {
    fun field(
        name: String,
        type: SchemaType,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): DocumentSchemaBuilder

    fun build(): DocumentSchema
}
```

链式添加字段，`build()` 返回不可变快照：

```kotlin
val user = DocumentSchemaBuilder("User")
    .field("id", SchemaType.LONG, description = "用户 id")
    .field("name", SchemaType.STRING, description = "用户名")
    .field("age", SchemaType.INT, required = false, nullable = true)
    .build()

println(user.name)                     // User
println(user.fields.size)              // 3
println(user.field("age")?.nullable)   // true
println(user.field("missing"))         // null
```

`DocumentSchemaBuilder` 不做重名校验、不校验名称合法性；这些约束由消费方
（如 `OpenApiGenerator.validate()`）负责。

## 使用方

### format-csv：CsvSchema

CSV 的列顺序即字段顺序，并额外携带表头/嵌套配置：

```kotlin
val schema = CsvSchema.builder("user")
    .field("name", SchemaType.STRING)
    .field("age", SchemaType.INT)
    .build()

val mapper = CsvMapper(schema)
```

### format-avro：AvroSchema

Avro 记录名对应 `DocumentSchema.name`，可带 namespace；`AvroType` 与 `SchemaType`
双向映射：

```kotlin
val schema = AvroSchemaBuilder("Event")
    .namespace("com.example")
    .field("id", SchemaType.LONG)
    .field("payload", SchemaType.BYTES, nullable = true)
    .build()
```

### format-protobuf：ProtobufSchema

额外携带字段编号、repeated、嵌套 message/enum/map/oneof 等 protobuf 元数据：

```kotlin
val schema = ProtobufSchemaBuilder("User")
    .field("id", SchemaType.LONG, fieldNumber = 1)
    .field("name", SchemaType.STRING, fieldNumber = 2)
    .build()
```

### openapi：生成 OpenAPI 3.0

```kotlin
import com.mingliqiye.document.openapi.OpenApiConfig
import com.mingliqiye.document.openapi.toOpenApi

val config = OpenApiConfig.builder("Demo API", "1.0.0").build()
val document = user.toOpenApi(config)          // ObjectNode
val json = OpenApiGenerator.of(config).addSchema(user).generateJson(pretty = true)
```

## 自定义 Schema 实现

实现 `DocumentSchema` 即可接入 `openapi`：

```kotlin
class MySchema(
    override val name: String,
    override val fields: List<SchemaField>,
) : DocumentSchema {
    override fun field(name: String): SchemaField? = fields.firstOrNull { it.name == name }
}
```

注意：

- `name` 会成为 OpenAPI component key，必须满足 `[A-Za-z0-9._-]+`；
- `fields` 顺序会写入 `required` 数组与 `properties`，请保持稳定顺序；
- 字段名不能为空白、不能重复，否则 `OpenApiGenerator` 抛 `OpenApiSchemaException`；
- 只实现 Schema 不会自动带来「解析/写出」能力，Schema 与 Mapper 是两条线
  （CSV/Avro/Protobuf 的 Mapper 会显式接收各自 Schema）。

## 示例：Schema 驱动 OpenAPI

```kotlin
import com.mingliqiye.document.openapi.OpenApiConfig
import com.mingliqiye.document.openapi.OpenApiFieldType
import com.mingliqiye.document.openapi.OpenApiGenerator
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType

val user = DocumentSchemaBuilder("User")
    .field("id", SchemaType.LONG, description = "用户 id")
    .field("name", SchemaType.STRING)
    .build()

val order = DocumentSchemaBuilder("Order")
    .field("id", SchemaType.LONG)
    .field("user", SchemaType.OBJECT)
    .field("lines", SchemaType.ARRAY)
    .build()

val config = OpenApiConfig.builder("Shop API", "1.0.0")
    .refOf("Order", "user", "User")
    .arrayOf("Order", "lines", OpenApiFieldType.reference("Line"))
    .build()

val document = OpenApiGenerator.of(config)
    .addSchema(user)
    .addSchema(order)
    .addSchema(
        DocumentSchemaBuilder("Line")
            .field("sku", SchemaType.STRING)
            .field("count", SchemaType.INT)
            .build(),
    )
    .generate()
```

## 配置

`schema` 模块**没有配置项**。与 Schema 相关的配置在其消费方：

- `OpenApiConfig`：标题、版本、描述、servers、operations、字段类型覆盖；
- `CsvConfig` / `CsvSchema`：分隔符、表头、嵌套模式；
- `AvroSchema` / `ProtobufSchema`：格式专属元数据。

## 扩展点

1. **实现 `DocumentSchema`**：自定义 Schema 接入 OpenAPI 生成。
2. **工厂方法**：`DocumentSchema.of(...)` 用于快速构造简单 Schema。
3. **格式适配**：格式模块可以让自己的 Schema 实现 `DocumentSchema`，从而复用一个
   OpenAPI 生成器。内置的 CSV/Avro/Protobuf 都这样做。
4. **字段类型覆盖**：`SchemaType` 表达不了的细节，通过 `OpenApiConfigBuilder` 的
   `fieldType` / `enumOf` / `refOf` / `arrayOf` / `mapOf` 补充，不需要改 Schema 类型。

## 限制

- 没有 schema 级描述字段：OpenAPI 生成时不会输出 component 顶层 `description`；
- 没有嵌套结构：`ARRAY` 不描述元素类型，`MAP` 不描述值类型，`OBJECT` 是自由对象；
- 没有枚举类型（OpenAPI 通过字段类型覆盖表达字符串枚举）；
- 没有默认值、约束（min/max/pattern）、示例等 JSON Schema 关键字；
- 没有校验能力：`schema` 不会验证文档，也不会为 Mapper 自动生成校验逻辑；
- 不支持 Schema 引用/组合（`oneOf`/`allOf`/`$ref`），`$ref` 仅在 OpenAPI 输出层由
  `OpenApiFieldType.reference` 表达；
- `SchemaType.NULL` 在 OpenAPI 3.0 无法表达，生成时抛 `OpenApiSchemaException`；
- 字段名/名称合法性不在这里校验，由消费方（OpenAPI 生成器）负责。

## 依赖坐标

```kotlin
// Gradle
implementation("com.mingliqiye.document:schema:2.0.0")

// 或使用 BOM
implementation(platform("com.mingliqiye.document:mingli-document-bom:2.0.0"))
implementation("com.mingliqiye.document:schema")
```

通常不需要单独声明：`format-csv` / `format-avro` / `format-protobuf` / `openapi`
会通过 `api` 传递引入。

## 构建

```bash
gradle :schema:test
gradle :schema:ktlintCheck
```
