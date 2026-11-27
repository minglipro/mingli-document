# format-json

JSON / JSONC 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:json:1.0.0")
```

## 主要 API

```text
JsonMapper
JsonMapperBuilder
JsonReader
JsonConfig
DuplicateKeyPolicy
JsonTokenReader
JsonTokenWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = JsonMapper()
val json = """{"name":"小于","age":18}"""

val user = mapper.parse(json, User::class.java)
val text = mapper.format(user)
```

## 配置

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper(
    JsonConfig.builder()
        .allowComments(true)
        .pretty(true)
        .duplicateKeyPolicy(DuplicateKeyPolicy.FAIL)
        .maxDepth(512)
        .build(),
)
```

## 特性

- JSONC 注释
- 严格 UTF-8 byte API
- 重复 key 策略：`LAST_WINS` / `FIRST_WINS` / `FAIL`
- 深度限制
- 文件/流读写
- Token 直读 Bean
- 轻量 JSON Schema 校验

## JSON Schema

```kotlin
import com.mingliqiye.document.format.json.schema.JsonSchema

val schema = JsonSchema.compile(
    """
    {
      "type": "object",
      "required": ["name"],
      "properties": {
        "name": {"type": "string", "minLength": 1}
      }
    }
    """.trimIndent(),
)

val errors = schema.validate(node)
schema.isValid(node)
schema.validateOrThrow(node)
```

支持：

```text
type / enum / const
properties / required / additionalProperties / patternProperties
items / minItems / maxItems / uniqueItems
minimum / maximum / exclusiveMinimum / exclusiveMaximum / multipleOf
minLength / maxLength / pattern
allOf / anyOf / oneOf / not
if / then / else
contains / minContains / maxContains
dependentRequired
propertyNames
format：date-time / date / time / email / hostname / ipv4 / ipv6 / uri / uuid
本地 $ref
```

不支持远程 `$ref`、`dependentSchemas`、`unevaluatedProperties` 等高级关键字。

## 构建

```bash
gradle :format-json:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
