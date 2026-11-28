# openapi

基于 `schema` 模块生成 OpenAPI 3.0 文档的模块。

- 输入格式无关的 `DocumentSchema`，输出格式无关的 `ObjectNode`（`OpenApiGenerator.generate()`）；
- 需要文本时输出 OpenAPI JSON（`OpenApiGenerator.generateJson()`），内部实现，不引入 JSON/网络依赖；
- 支持 `components.schemas`（object properties / required / array items / descriptions / nullable / `$ref`）
  与由 `OpenApiOperation` 配置的 `paths`。

## Maven

```kotlin
implementation("com.mingliqiye.document:openapi:2.3.0")
```

## 主要 API

```text
OpenApiGenerator
OpenApiConfig
OpenApiConfigBuilder
OpenApiOperation
OpenApiServer
OpenApiFieldType
OpenApiPrimitiveType / OpenApiReferenceType / OpenApiArrayType / OpenApiMapType / OpenApiEnumType
DocumentSchema.toOpenApi(config)
Iterable<DocumentSchema>.toOpenApi(config)
```

## 快速开始

```kotlin
import com.mingliqiye.document.openapi.OpenApiConfig
import com.mingliqiye.document.openapi.OpenApiGenerator
import com.mingliqiye.document.openapi.toOpenApi
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType

val user = DocumentSchemaBuilder("User")
    .field("id", SchemaType.LONG, description = "用户 id")
    .field("name", SchemaType.STRING, description = "用户名")
    .field("age", SchemaType.INT, required = false, nullable = true)
    .build()

val config = OpenApiConfig.builder("Demo API", "1.0.0")
    .description("示例 API")
    .server("https://api.example.com", "生产")
    .operation("/users/{id}", "GET", "User", summary = "查询用户", operationId = "getUser", tags = listOf("user"))
    .build()

// 格式无关的文档树
val document = user.toOpenApi(config)
// 或使用生成器组合多个 Schema
val document2 = OpenApiGenerator.of(config).addSchema(user).generate()
// JSON 文本（pretty = true 时两空格缩进）
val json = OpenApiGenerator.of(config).addSchema(user).generateJson(pretty = true)
```

```json
{
  "openapi": "3.0.3",
  "info": {
    "title": "Demo API",
    "version": "1.0.0",
    "description": "示例 API"
  },
  "servers": [
    {
      "url": "https://api.example.com",
      "description": "生产"
    }
  ],
  "paths": {
    "/users/{id}": {
      "get": {
        "tags": ["user"],
        "summary": "查询用户",
        "operationId": "getUser",
        "responses": {
          "200": {
            "description": "OK",
            "content": {
              "application/json": {
                "schema": { "$ref": "#/components/schemas/User" }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "schemas": {
      "User": {
        "type": "object",
        "properties": {
          "id": { "type": "integer", "format": "int64", "description": "用户 id" },
          "name": { "type": "string", "description": "用户名" },
          "age": { "type": "integer", "format": "int32", "nullable": true }
        },
        "required": ["id", "name"]
      }
    }
  }
}
```

## Schema 映射规则

| SchemaType | OpenAPI |
| --- | --- |
| `STRING` | `{"type": "string"}` |
| `INT` | `{"type": "integer", "format": "int32"}` |
| `LONG` | `{"type": "integer", "format": "int64"}` |
| `DOUBLE` | `{"type": "number", "format": "double"}` |
| `DECIMAL` | `{"type": "number"}`（不声明格式，避免暗示精度） |
| `BOOLEAN` | `{"type": "boolean"}` |
| `BYTES` | `{"type": "string", "format": "byte"}`（Base64） |
| `ARRAY` | `{"type": "array", "items": {"type": "string"}}`（默认元素类型为 string） |
| `MAP` | `{"type": "object", "additionalProperties": {"type": "string"}}`（默认值类型为 string） |
| `OBJECT` | `{"type": "object"}`（自由对象） |
| `NULL` | 不支持，抛 `OpenApiSchemaException`；可空字段请使用 `nullable = true` |

通用 `SchemaType` 不携带嵌套结构信息，`ARRAY`/`MAP`/`OBJECT` 的默认约定与
`format-avro` 一致；需要元素类型、枚举取值或 `$ref` 嵌套时使用字段类型覆盖。

每个 Schema 生成 `components.schemas.<name>`：

- `SchemaField.required = true` 的字段按声明顺序写入 `required` 数组；
- `SchemaField.nullable = true` 生成 `nullable: true`；
- `SchemaField.description` 非空白时生成 `description`；
- 空 Schema 生成 `{"type": "object", "properties": {}}`；
- Schema 名称需满足 OpenAPI component key 规则 `[A-Za-z0-9._-]+`。

## 字段类型覆盖：枚举、`$ref` 与嵌套

`OpenApiConfigBuilder` 提供按 `schemaName.fieldName` 覆盖字段类型的入口：

```kotlin
import com.mingliqiye.document.openapi.OpenApiFieldType

val config = OpenApiConfig.builder("Demo API", "1.0.0")
    // 字符串枚举
    .enumOf("Order", "status", "NEW", "PAID", "CANCELLED")
    // 嵌套对象 -> $ref
    .refOf("Order", "user", "User")
    // 数组元素 -> $ref
    .arrayOf("Order", "lines", OpenApiFieldType.reference("Line"))
    // Map 值 -> $ref
    .mapOf("Order", "attributes", OpenApiFieldType.reference("Attribute"))
    .build()

val document = listOf(order, user, line, attribute).toOpenApi(config)
```

等价写法：

```kotlin
config.fieldType("Order", "status", OpenApiFieldType.enumOf("NEW", "PAID"))
config.fieldType("Order", "lines", OpenApiFieldType.array(OpenApiFieldType.reference("Line")))
```

生成的字段片段：

```json
{
  "status": { "type": "string", "enum": ["NEW", "PAID", "CANCELLED"] },
  "user": { "$ref": "#/components/schemas/User" },
  "lines": { "type": "array", "items": { "$ref": "#/components/schemas/Line" } },
  "attributes": { "type": "object", "additionalProperties": { "$ref": "#/components/schemas/Attribute" } }
}
```

- `$ref` 目标必须是已注册的 Schema，否则抛 `OpenApiSchemaException`；
- 自引用（递归 Schema）允许，例如 `Tree.children` 的数组元素引用 `Tree`；
- 可空（`nullable = true`）的 `$ref` 字段按 OpenAPI 3.0 约定包装，避免 `$ref` 同级字段被忽略：

```json
{
  "address": {
    "allOf": [{ "$ref": "#/components/schemas/Address" }],
    "nullable": true,
    "description": "地址"
  }
}
```

## paths

```kotlin
val config = OpenApiConfig.builder("Demo API", "1.0.0")
    .operation("/users", "POST", "User", summary = "创建用户", operationId = "createUser")
    .operation("/users/{id}", "GET", "User", operationId = "getUser")
    .build()
```

- 每个 operation 生成 `200` 响应：`application/json` + `$ref` 到对应 Schema；
- `POST`/`PUT`/`PATCH` 额外生成 `required: true` 的 requestBody，同样通过 `$ref` 引用；
- HTTP 方法统一转大写存储，生成 JSON 时按 OpenAPI 规范写成小写；
- 同一路径 + 方法重复、路径不以 `/` 开头、方法非法、引用未注册 Schema 都会抛出异常。

当前 paths 只覆盖最基本的 operation 形态，不生成参数、headers、security、多状态码等内容。

## 配置

```kotlin
val config = OpenApiConfig.builder("标题", "1.0.0") // 必填且不能为空白
    .description("描述")                             // 可选
    .server("https://api.example.com", "生产")        // 可选，可多次调用
    .operation(/* ... */)                            // 可选
    .build()
```

## 异常

均继承自 `OpenApiException`（`OpenApiException : DocumentException`）：

- `OpenApiConfigException`：配置非法，如标题/版本/Server url 为空白、operation 路径或方法非法、
  operation 重复、字段类型覆盖重复、枚举取值为空或重复；
- `OpenApiSchemaException`：Schema 非法或不支持，如 Schema 名不符合 component 规则、字段名空白/重复、
  使用 `SchemaType.NULL`、`$ref` 指向未知 Schema、字段类型覆盖指向未知 Schema/字段、
  未注册任何 Schema、operation 引用未知 Schema。

## 构建

```bash
gradle :openapi:ktlintFormat :openapi:test
```

## 限制

1. 固定生成 OpenAPI `3.0.3`，不支持 3.1 / Swagger 2.0。
2. `SchemaType` 是通用类型，不携带元素类型/枚举/嵌套信息；复合类型默认按约定映射，
   嵌套结构需要通过字段类型覆盖补充（无法从 `DocumentSchema` 自动推导）。
3. `SchemaType.NULL` 无法映射到 OpenAPI 3.0，生成时抛异常。
4. 枚举覆盖只支持字符串枚举（`type: string` + `enum`），不支持数字枚举。
5. `DocumentSchema` 没有 schema 级描述，因此 `components.schemas.<name>` 不生成顶层 `description`。
6. paths 生成仅覆盖基本 operation（固定 200 响应、无参数/安全定义/headers/多状态码）。
7. JSON 文本输出由模块内部 writer 实现，仅用于生成；模块不提供 JSON 解析能力。
