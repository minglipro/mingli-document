# schema

格式无关的文档 Schema 模块。

## 主要类型

```text
DocumentSchema
DocumentSchemaBuilder
SchemaField
SchemaType
```

`SchemaType`：

```text
STRING
INT
LONG
DOUBLE
DECIMAL
BOOLEAN
BYTES
ARRAY
OBJECT
MAP
NULL
```

## Maven

```kotlin
implementation("com.mingliqiye.document:schema:1.4.0")
```

## 示例

```kotlin
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType

val schema = DocumentSchemaBuilder("user")
    .field("name", SchemaType.STRING)
    .field("age", SchemaType.INT, required = false, nullable = true)
    .build()

println(schema.field("age")?.type) // INT
```

## 使用方

- `format-csv`：`CsvSchema`
- `format-avro`：`AvroSchema`
- `format-protobuf`：`ProtobufSchema`

## 构建

```bash
gradle :schema:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
