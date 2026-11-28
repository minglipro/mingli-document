# format-json

JSON / JSONC 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:json:2.4.0")
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
JsonMapper.parseLines / formatLines
JsonMapper.parseArray / formatArray
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

## 流式 API（大文件）

大文件不需要先读成 `String`：`parseLines` / `parseArray` 逐条解析并回调 Bean，
`formatLines` / `formatArray` 逐条写出，任意时刻内存中只有当前行/元素。

### JSON Lines / NDJSON

```kotlin
import com.mingliqiye.document.format.json.JsonMapper
import java.io.FileInputStream
import java.io.FileOutputStream

val mapper = JsonMapper()

// 逐行解析：每行一个完整 JSON 值，解析出一个 Bean 立刻回调
FileInputStream("users.ndjson").use { input ->
    mapper.parseLines(input, User::class.java) { user ->
        // 逐条处理，不会把整个文件读成 String
    }
}

// 逐行写出：每个值一行紧凑 JSON
FileOutputStream("users.ndjson").use { output ->
    mapper.formatLines(users, output)
}
```

也支持 `Reader` / `Writer` 与 `DocumentTypeReference`（泛型目标类型）：

```kotlin
import com.mingliqiye.document.core.DocumentTypeReference

mapper.parseLines(reader, DocumentTypeReference.of(User::class.java)) { user -> }
mapper.formatLines(users, writer)
```

语义：

- 逐行读取，内存中只保留当前行的文本与当前 Bean，不会走整体 `parse`。
- 空行/纯空白行跳过；首个字符为 UTF-8 BOM 时忽略；行内允许 JSONC 注释
  （`allowComments` 为 `true` 时），不支持整行注释。
- 畸形行抛 `JsonParseException`（消息带行号）；Bean 反序列化失败抛
  `DocumentSerializationException`（消息带行号）；`consumer` 抛出的异常原样传播。
- 行内容为 JSON `null` 时以 `null` 回调（`consumer` 参数类型为可空）。
- 不关闭调用方传入的流（只读取/flush），调用方负责关闭。
- `formatLines` 始终输出紧凑格式（即使配置 `pretty = true`），保证每个值独占一行；
  写出失败时输出可能只写入了一部分。

### 顶层 JSON 数组

```kotlin
// 逐元素解析顶层数组
FileInputStream("users.json").use { input ->
    mapper.parseArray(input, User::class.java) { user -> }
}

// 流式写出顶层数组（不构建完整数组节点）
FileOutputStream("users.json").use { output ->
    mapper.formatArray(users, output)
}
```

语义：

- 逐字符扫描数组元素边界，仅保留当前元素文本；元素内部仍复用完整 JSON 语法、
  嵌套深度、重复 key 与注释规则，空数组不产生回调。
- 顶层不是数组、元素畸形、数组未闭合、`]` 之后还有内容时抛 `JsonParseException`
  （元素畸形时消息带数组行号）；数组元素为 JSON `null` 时以 `null` 回调。
- 元素之间不共享 `$ref` 解析上下文；`formatArray` 始终输出紧凑格式。
- `parseArray` 只支持顶层数组；与 `parseLines` 一样不关闭调用方流。

### 输入上限

流式 API 无法预先知道总长度，`maxInputSize` 按“累计已从输入读取的字节数
（`InputStream`）/ 字符数（`Reader`）”校验，可能包含缓冲区预读；超限抛
`DocumentSerializationException`。

### 限制

- JSON Lines 每行必须是单个完整 JSON 值，不支持跨行值；单行大小决定峰值内存。
- `formatLines` / `formatArray` 的 `OutputStream` 变体直接流式写入；`Writer` 变体每个值
  会构建一次紧凑 JSON 文本（峰值内存为单个值）。
- 流式写出不支持 `writeReferences`（`$ref` 按对象身份写出）等只属于 `toNode`
  路径的能力；写出失败时输出可能部分写入。
- 流式解析不关闭调用方流，也不提供跳过/继续读取的游标语义。

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
- JSON Lines / NDJSON 与顶层数组流式读写（`parseLines` / `formatLines` / `parseArray` / `formatArray`）
- 轻量 JSON Schema 校验
- 紧凑模式纯 UTF-8 字节写出（不经过中间 String）
- Double 使用 Schubfach 最短表示

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
