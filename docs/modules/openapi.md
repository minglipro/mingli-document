# openapi 模块

`openapi` 基于 `schema` 模块生成 **OpenAPI 3.0.3** 文档。输入是格式无关的
`DocumentSchema`，输出是格式无关的 `ObjectNode`（可继续用任意 Mapper 序列化），或者
模块内部 writer 生成的 OpenAPI JSON 文本。

- 坐标：`com.mingliqiye.document:openapi`
- 当前版本：`2.1.0`
- 包名：`com.mingliqiye.document.openapi`
- 目标字节码：JDK 8
- 公开依赖：`schema`、`core`、`exception`

## 职责

| 能力 | 类型 |
|---|---|
| 生成器 | `OpenApiGenerator` |
| 配置 | `OpenApiConfig`、`OpenApiConfigBuilder` |
| 路径操作 | `OpenApiOperation` |
| server 定义 | `OpenApiServer` |
| 字段类型覆盖 | `OpenApiFieldType` 及其实现类 `OpenApiPrimitiveType` / `OpenApiReferenceType` / `OpenApiArrayType` / `OpenApiMapType` / `OpenApiEnumType` |
| Kotlin 扩展 | `DocumentSchema.toOpenApi(config)`、`Iterable<DocumentSchema>.toOpenApi(config)` |
| 异常 | `OpenApiException`、`OpenApiConfigException`、`OpenApiSchemaException` |

模块**不引入 JSON/网络依赖**：JSON 输出由内部 `OpenApiJsonWriter` 实现，仅用于生成。

## 依赖关系

```text
schema ──api──┐
core ────api──┼── api ── openapi
exception api─┘
```

- 输入 `DocumentSchema` 来自 `schema`；
- 输出 `ObjectNode` 来自 `core`；
- 异常继承 `DocumentException`；
- 与具体格式无关：可以把 `ObjectNode` 交给 `JsonMapper.format(...)`，也可以用
  `OpenApiGenerator.generateJson(...)` 直接拿 JSON 文本。

## OpenApiConfig

```kotlin
public class OpenApiConfig internal constructor(
    public val title: String,
    public val version: String,
    public val description: String?,
    public val servers: List<OpenApiServer>,
    public val operations: List<OpenApiOperation>,
)
```

| 属性 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `title` | `String` | 是（不能空白） | `info.title` |
| `version` | `String` | 是（不能空白） | `info.version` |
| `description` | `String?` | 否 | `info.description`；空白不输出 |
| `servers` | `List<OpenApiServer>` | 否 | 根节点 `servers` 数组；空则不输出 |
| `operations` | `List<OpenApiOperation>` | 否 | 生成 `paths` |

通过 `OpenApiConfig.builder(title, version)` 获取构建器。

## OpenApiConfigBuilder

| 方法 | 说明 |
|---|---|
| `description(description: String?)` | 设置描述；`null` 表示不输出 |
| `server(url: String, description: String? = null)` | 追加一个 server；`url` 不能空白 |
| `servers(servers: Iterable<OpenApiServer>)` | 批量追加 server |
| `operation(operation: OpenApiOperation)` | 追加路径操作；同 path + method 重复抛异常 |
| `operation(path, method, schemaName, summary?, operationId?, tags = emptyList())` | 便捷重载 |
| `fieldType(schemaName, fieldName, type)` | 覆盖字段的 OpenAPI 类型；重复配置抛异常 |
| `enumOf(schemaName, fieldName, vararg values)` | 字段映射为字符串枚举 |
| `refOf(schemaName, fieldName, targetSchemaName)` | 字段映射为 `$ref` |
| `arrayOf(schemaName, fieldName, items)` | 字段映射为数组 |
| `mapOf(schemaName, fieldName, values)` | 字段映射为 Map |
| `build()` | 生成不可变 `OpenApiConfig` |

构建期校验（抛 `OpenApiConfigException`）：

- `title` / `version` 为空白；
- `server` 的 `url` 为空白；
- operation 路径不以 `/` 开头、HTTP 方法不在支持列表、`schemaName` 空白；
- 同 path + method 的 operation 重复；
- 字段类型覆盖重复；枚举取值为空或重复；覆盖时 schema/字段名为空白。

支持的 HTTP 方法：`GET`、`PUT`、`POST`、`DELETE`、`OPTIONS`、`HEAD`、`PATCH`、`TRACE`
（存储时统一大写，生成 JSON 时小写）。

## OpenApiOperation

```kotlin
public class OpenApiOperation(
    public val path: String,
    method: String,
    public val schemaName: String,
    public val summary: String? = null,
    public val operationId: String? = null,
    tags: List<String> = emptyList(),
)
```

| 属性 | 说明 |
|---|---|
| `path` | 路径模板，必须以 `/` 开头，例如 `/users/{id}` |
| `method` | HTTP 方法，构造时 `trim().uppercase()` 并校验 |
| `schemaName` | 响应/请求体引用的 Schema 名称，生成时必须已注册 |
| `summary` | `summary`，空白不输出 |
| `operationId` | `operationId`，空白不输出 |
| `tags` | 标签列表，保持声明顺序；空则不输出 |

生成的 operation 结构固定：

- 总是生成 `200` 响应：`description: "OK"` + `application/json` + `$ref`；
- `POST` / `PUT` / `PATCH` 额外生成 `requestBody`（`required: true`，同样 `$ref`）；
- 不生成参数、headers、security、多状态码等内容。

## OpenApiServer

```kotlin
public class OpenApiServer(
    public val url: String,
    public val description: String? = null,
)
```

`url` 不能空白（否则 `OpenApiConfigException`）；`description` 空白时不输出。

## OpenApiGenerator

```kotlin
public class OpenApiGenerator(private val config: OpenApiConfig) {

    public fun addSchema(schema: DocumentSchema): OpenApiGenerator
    public fun addSchemas(schemas: Iterable<DocumentSchema>): OpenApiGenerator
    public fun generate(): ObjectNode
    public fun generateJson(pretty: Boolean = false): String

    public companion object {
        public const val OPENAPI_VERSION: String = "3.0.3"
        public fun of(config: OpenApiConfig): OpenApiGenerator
    }
}
```

| 方法 | 说明 |
|---|---|
| `addSchema` | 注册单个 Schema；同名重复抛 `OpenApiSchemaException` |
| `addSchemas` | 批量注册 |
| `generate()` | 校验全部 Schema/字段覆盖/operation，返回 `ObjectNode` |
| `generateJson(pretty = false)` | 输出 OpenAPI JSON；`pretty = true` 时两空格缩进 |
| `OPENAPI_VERSION` | 固定 `"3.0.3"` |

扩展函数：

```kotlin
fun DocumentSchema.toOpenApi(config: OpenApiConfig): ObjectNode
fun Iterable<DocumentSchema>.toOpenApi(config: OpenApiConfig): ObjectNode
```

等价于 `OpenApiGenerator.of(config).addSchema(this).generate()`（Iterable 版本按迭代顺序
注册，`components.schemas` 的字段顺序即注册顺序）。

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
    .operation(
        "/users/{id}",
        "GET",
        "User",
        summary = "查询用户",
        operationId = "getUser",
        tags = listOf("user"),
    )
    .build()

// 方式一：扩展函数
val document = user.toOpenApi(config)

// 方式二：生成器组合多个 Schema
val document2 = OpenApiGenerator.of(config).addSchema(user).generate()

// 方式三：直接生成 JSON 文本
val json = OpenApiGenerator.of(config).addSchema(user).generateJson(pretty = true)
```

输出结构：

```json
{
  "openapi": "3.0.3",
  "info": { "title": "Demo API", "version": "1.0.0", "description": "示例 API" },
  "servers": [{ "url": "https://api.example.com", "description": "生产" }],
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

| `SchemaType` | 生成的 OpenAPI |
|---|---|
| `STRING` | `{"type":"string"}` |
| `INT` | `{"type":"integer","format":"int32"}` |
| `LONG` | `{"type":"integer","format":"int64"}` |
| `DOUBLE` | `{"type":"number","format":"double"}` |
| `DECIMAL` | `{"type":"number"}`（不声明格式，避免暗示精度） |
| `BOOLEAN` | `{"type":"boolean"}` |
| `BYTES` | `{"type":"string","format":"byte"}`（Base64） |
| `ARRAY` | `{"type":"array","items":{"type":"string"}}`（默认元素类型 string） |
| `MAP` | `{"type":"object","additionalProperties":{"type":"string"}}`（默认值类型 string） |
| `OBJECT` | `{"type":"object"}`（自由对象） |
| `NULL` | 不支持，抛 `OpenApiSchemaException`；可空字段请用 `nullable = true` |

`components.schemas.<name>` 生成细节：

- `SchemaField.required = true` 的字段按声明顺序写入 `required` 数组；
- `SchemaField.nullable = true` 生成 `"nullable": true`；
- `SchemaField.description` 非空白时生成 `description`；
- 空 Schema 生成 `{"type":"object","properties":{}}`；
- Schema 名称必须满足 `[A-Za-z0-9._-]+`；
- `DocumentSchema` 没有 schema 级描述，因此不生成 component 顶层 `description`。

## 字段类型覆盖

通用 `SchemaType` 无法表达元素类型、枚举取值、嵌套 Schema；通过
`OpenApiConfigBuilder` 按 `schemaName.fieldName` 覆盖：

```kotlin
import com.mingliqiye.document.openapi.OpenApiFieldType

val config = OpenApiConfig.builder("Demo API", "1.0.0")
    .enumOf("Order", "status", "NEW", "PAID", "CANCELLED")
    .refOf("Order", "user", "User")
    .arrayOf("Order", "lines", OpenApiFieldType.reference("Line"))
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

规则：

- `$ref` 目标必须是已注册的 Schema，否则 `OpenApiSchemaException`；
- 自引用（递归 Schema）允许，例如 `Tree.children` 的数组元素引用 `Tree`；
- 可空（`nullable = true`）的 `$ref` 字段按 OpenAPI 3.0 约定包装，避免 `$ref` 同级
  字段被忽略：

```json
{
  "address": {
    "allOf": [{ "$ref": "#/components/schemas/Address" }],
    "nullable": true,
    "description": "地址"
  }
}
```

`OpenApiFieldType` 工厂：

| 工厂 | 说明 |
|---|---|
| `OpenApiFieldType.primitive(SchemaType)` | 基本类型，映射规则同字段声明类型（`NULL` 非法） |
| `OpenApiFieldType.reference(schemaName)` | 引用 components 中的另一个 Schema |
| `OpenApiFieldType.array(items)` | 数组，元素为指定类型 |
| `OpenApiFieldType.map(values)` | Map，值为指定类型 |
| `OpenApiFieldType.enumOf(vararg values)` | 字符串枚举；取值不能为空/重复 |

## paths

```kotlin
val config = OpenApiConfig.builder("Demo API", "1.0.0")
    .operation("/users", "POST", "User", summary = "创建用户", operationId = "createUser")
    .operation("/users/{id}", "GET", "User", operationId = "getUser")
    .build()
```

- 每个 operation 生成 `200` 响应：`application/json` + `$ref` 到对应 Schema；
- `POST`/`PUT`/`PATCH` 额外生成 `required: true` 的 `requestBody`，同样通过 `$ref`；
- HTTP 方法统一转大写存储，生成 JSON 时按 OpenAPI 规范写成小写；
- 同一路径 + 方法重复、路径不以 `/` 开头、方法非法、引用未注册 Schema 都会抛出异常；
- 同一路径下多个方法会合并到同一个 path item。

## 异常

均继承 `OpenApiException`（`OpenApiException : DocumentException`）：

| 异常 | 触发场景 |
|---|---|
| `OpenApiConfigException` | 标题/版本/Server url 空白；operation 路径不以 `/` 开头、方法非法、schemaName 空白、operation 重复；字段类型覆盖重复、枚举取值为空或重复、覆盖时 schema/字段名空白 |
| `OpenApiSchemaException` | 未注册任何 Schema；Schema 名空白或不符合 component 规则；字段名空白/重复；使用 `SchemaType.NULL`；`$ref` 指向未知 Schema；字段类型覆盖指向未知 Schema/字段；operation 引用未知 Schema；重复注册同名 Schema |

## 扩展点

1. **自定义 Schema**：实现 `DocumentSchema` 即可参与生成（见 [schema](./schema.md)）。
2. **自定义字段类型**：`OpenApiFieldType` 是 `sealed interface`，当前固定 5 种实现
   （primitive/reference/array/map/enum），无法从外部新增（如数字枚举需改本模块）。
3. **结果复用**：`generate()` 返回 `ObjectNode`，可继续用 `JsonMapper` / `YamlMapper` 输出或参与 `Node` 合并。
4. **JSON 输出**：`generateJson(pretty)` 是唯一文本输出入口；模块不提供解析能力。

## 限制

1. 固定生成 OpenAPI `3.0.3`，不支持 3.1 / Swagger 2.0。
2. `SchemaType` 是通用类型，不携带元素类型/枚举/嵌套信息；复合类型默认按约定映射，
   嵌套结构需要通过字段类型覆盖补充（无法从 `DocumentSchema` 自动推导）。
3. `SchemaType.NULL` 无法映射到 OpenAPI 3.0，生成时抛异常。
4. 枚举覆盖只支持字符串枚举（`type: string` + `enum`），不支持数字枚举。
5. `DocumentSchema` 没有 schema 级描述，因此 `components.schemas.<name>` 不生成顶层 `description`。
6. paths 生成仅覆盖基本 operation（固定 200 响应、无参数/安全定义/headers/多状态码）。
7. JSON 文本输出由模块内部 writer 实现，仅用于生成；模块不提供 JSON 解析能力。
8. 不支持 `securitySchemes`、`components.parameters`、`components.responses`、callbacks、links 等组件。
9. Schema 注册顺序决定输出顺序；`generate()` 每次都会重新校验全部输入。

## 坐标与构建

```kotlin
implementation("com.mingliqiye.document:openapi:2.1.0")

// 使用 BOM
implementation(platform("com.mingliqiye.document:mingli-document-bom:2.1.0"))
implementation("com.mingliqiye.document:openapi")
```

`openapi` 已被纳入 `bom/build.gradle.kts` 的 `constraints`。

```bash
gradle :openapi:ktlintFormat :openapi:test
gradle :openapi:build
```

相关文档：

- [schema 模块](./schema.md)：`DocumentSchema` / `SchemaField` / `SchemaType`
- [core 模块](./core.md)：`ObjectNode` 输出
- [API 参考](../reference/api.md)：`OpenApiConfigBuilder` 等索引
