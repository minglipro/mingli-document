# format-csv

CSV 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:csv:1.4.0")
```

## 主要 API

```text
CsvMapper
CsvReader
CsvWriter
CsvConfig
CsvSchema
CsvSchemaBuilder
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

## 特性

- 平坦表格
- 表头 / 无表头
- 类型推断
- CSV Schema
- 文件/流读写
- Token 直读 Bean
- 原生 CSV Token Reader

## 限制

- 只支持平坦对象行
- 不支持嵌套对象/数组单元格
- 根节点固定为数组

## 构建

```bash
gradle :format-csv:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
