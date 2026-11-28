# exception 模块

`exception` 是文档库的异常基类模块。它只定义两个类型：公共基类 `DocumentException` 与
类型不匹配异常 `DocumentTypeException`，其余异常由各模块继承扩展。

- 坐标：`com.mingliqiye.document:exception`
- 当前版本：`2.2.0`
- 包名：`com.mingliqiye.document.exception`
- 目标字节码：JDK 8
- 公开依赖：`com.mingliqiye.exception:mingli-exception:1.1.1-Final`（`api`）

## 职责

| 类型 | 作用 |
|---|---|
| `DocumentException` | 库内所有异常的公共基类，统一对外捕获入口 |
| `DocumentTypeException` | `Node` 类型与预期不符时抛出（`asObject()` 等） |

模块本身不含业务逻辑；它的价值在于给调用方一个稳定的捕获边界：

```kotlin
try {
    mapper.parse(text, User::class.java)
} catch (e: DocumentException) {
    // 格式解析失败、序列化失败、类型不匹配都能在这里兜住
    log.error("document error: ${e.message}", e)
}
```

## 依赖关系

```text
com.mingliqiye.exception:mingli-exception:1.1.1-Final
                 ▲ api
                 │
        DocumentException（本模块）
                 ▲
      ┌──────────┼──────────────────────────────┐
      │          │                              │
DocumentTypeException                DocumentSerializationException（serialization）
                                             ▲
      format-* 的 XxxException ◄──────────────┤
      OpenApiException（openapi） ◄───────────┘
```

- 外部依赖 `mingli-exception` 提供 `com.mingliqiye.exception.MingLiBaseException`；
  `DocumentException` 直接继承它，因此库异常与外部的 mingli 系列异常同属一个体系。
- 由于所有格式异常都继承 `DocumentException`，调用方**只需捕获一个基类**。
- `serialization`、`core`、`openapi`、`format-*` 都通过 `api(project(":exception"))`
  暴露这个基类，不需要使用方单独声明。

## 异常层级

```text
Throwable
└── Exception
    └── RuntimeException（mingli-exception 的运行时异常体系）
        └── MingLiBaseException
            └── DocumentException
                ├── DocumentTypeException
                ├── DocumentSerializationException        （serialization 模块）
                ├── JsonException                         （format-json）
                │   └── JsonParseException
                ├── JsonSchemaException                   （format-json）
                ├── JsonSchemaValidationException         （format-json）
                ├── YamlException                         （format-yaml）
                │   └── YamlParseException
                ├── XmlException                          （format-xml）
                │   └── XmlParseException
                ├── PropertiesException                   （format-properties）
                │   └── PropertiesParseException
                ├── TomlException                         （format-toml）
                │   └── TomlParseException
                ├── CsvException                         （format-csv）
                │   └── CsvParseException
                ├── IonException                          （format-ion）
                │   └── IonParseException
                ├── CborException                         （format-cbor）
                │   └── CborParseException
                ├── SmileException                        （format-smile）
                │   └── SmileParseException
                ├── AvroException                         （format-avro）
                │   └── AvroParseException
                ├── ProtobufException                     （format-protobuf）
                │   └── ProtobufParseException
                └── OpenApiException                      （openapi）
                    ├── OpenApiConfigException
                    └── OpenApiSchemaException
```

完整逐类说明见参考章节的 [异常参考](../reference/exceptions.md)。

## DocumentException

```kotlin
public open class DocumentException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : MingLiBaseException(message, throwable, enableSuppression, writableStackTrace)
```

| 参数 | 默认值 | 说明 |
|---|---|---|
| `message` | `null` | 错误信息 |
| `throwable` | `null` | 原始异常（保留 cause 链） |
| `enableSuppression` | `false` | 是否启用异常抑制 |
| `writableStackTrace` | `true` | 是否生成堆栈 |

设计要点：

- **运行时异常**：不强制 `throws`，调用方按需捕获；
- **可带 cause**：格式模块会把底层解析器的异常包装进来（例如 XML 的 SAX 异常、
  Ion 的 `IonException`），排查时保留完整因果链；
- **默认不抑制**：`enableSuppression = false`；
- **公共基类**：自定义格式模块应继承它，保证使用方的捕获边界统一。

## DocumentTypeException

```kotlin
public class DocumentTypeException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : DocumentException(message, throwable)
```

只用于「节点类型与预期不符」：

| 调用 | 触发条件 | 消息示例 |
|---|---|---|
| `node.asObject()` | 节点不是对象 | `Expected object but was array` |
| `node.asArray()` | 节点不是数组 | `Expected array but was string` |
| `node.asNumber()` | 节点不是数字 | `Expected number but was null` |
| `node.asString()` | 节点不是字符串 | `Expected string but was object` |
| `node.asBoolean()` | 节点不是布尔 | `Expected boolean but was number` |
| `node.asBinary()` | 节点不是二进制 | `Expected binary but was string` |

安全取值请用 `asXxxOrNull()`，或用 `isXxx` 先判断：

```kotlin
val name = node.asObjectOrNull()?.get("name")?.asStringOrNull() ?: "unknown"
```

`DocumentTypeException` **不携带路径信息**，它来自 `core`；需要字段路径的错误由
`serialization` 的 `DocumentSerializationException` 提供。

## 各模块异常职责

| 模块 | 异常 | 常见抛出条件 |
|---|---|---|
| `core` | `DocumentTypeException` | `Node.asXxx()` 类型不匹配 |
| `serialization` | `DocumentSerializationException` | Bean 映射失败、缺字段 `FAIL`、未知字段、`Node` 限制、循环引用、Token 流非法、二进制 API 不支持、`merge` 非法 |
| `format-json` | `JsonParseException` / `JsonSchemaException` / `JsonSchemaValidationException` | JSON/JSONC 语法错误、重复 key `FAIL`、非法 UTF-8、深度超限；JSON Schema 编译/校验失败 |
| `format-yaml` | `YamlParseException` | YAML 语法错误、多文档根不符合预期、alias 超限、自定义 tag 非法 |
| `format-xml` | `XmlParseException` | XML 语法错误、DTD/外部实体被禁用、根元素不符合预期 |
| `format-properties` | `PropertiesParseException` | 键路径非法（空 key、空段、未闭合数组下标、转义错误）、key 路径冲突 |
| `format-toml` | `TomlParseException` | TOML 语法错误、根不是 table、不支持的类型、NaN/Infinity |
| `format-csv` | `CsvParseException` | CSV 解析失败、表头重复、未知/缺失 schema 列、嵌套 JSON 单元格非法 |
| `format-ion` | `IonParseException` | Ion 语法错误、多根/空文档、NaN/Infinity、类型保真解析失败 |
| `format-cbor` | `CborParseException` | CBOR 字节非法、截断、深度超限、tag 保真解析失败 |
| `format-smile` | `SmileParseException` | Smile 字节非法、截断、深度超限 |
| `format-avro` | `AvroParseException` | Avro wire/OCF 字节非法、schema 不匹配、schema evolution 不兼容 |
| `format-protobuf` | `ProtobufParseException` | protobuf 字节非法、Descriptor 不匹配、未知字段策略触发 |
| `openapi` | `OpenApiConfigException` / `OpenApiSchemaException` | 配置非法（标题/版本/路径/方法/重复 operation/重复字段覆盖）；Schema 非法（名称、字段、`$ref`、`SchemaType.NULL`） |
| `codegen` | KSP 编译错误 | 注解标在非枚举、非 public 枚举、参数非法、生成类名重复、模块参数冲突 |

## 何时抛出（按调用面）

### 解析

```kotlin
mapper.parse(invalidText, User::class.java)
//  → format-* 的 XxxParseException（语法/格式错误）
//  → DocumentSerializationException（语法正确但无法映射成 Bean）
```

### 序列化 / 映射

```kotlin
mapper.parse("""{"age":"abc"}""", User::class.java)
//  → DocumentSerializationException（数字字段无法解析）
```

### 类型访问

```kotlin
mapper.parse("""[1,2]""", ObjectNode::class.java).asObject()
//  → DocumentTypeException: Expected object but was array
```

> 注意：解析得到 `Node` 后，`parse(text, Node::class.java)` 走节点直通，不会做 Bean 校验。

### 配置

```kotlin
DocumentMapperConfig.builder().maxInputSize(-1)
//  → IllegalArgumentException（配置构建期，非 DocumentException）
```

配置期错误通常抛 `IllegalArgumentException`，而不是 `DocumentException`；例如：

- `maxInputSize` / `maxStringLength` / `maxNodeDepth` / `maxNodeCount` 为负数；
- `typeName` / `typeProperty` 为空白；
- 子类型注册不合法（子类不可赋值、类型名重复、注解类型名为空）；
- `kotlinSealedSubtypes` 传入非 sealed 类型；
- OpenAPI 的 `title` / `version` 为空。

### 代码生成

KSP 处理器通过 `logger.error(...)` 报告编译错误（`@DocumentEnumCodec` 只能标枚举、
枚举必须 public、生成类名重复、模块名冲突、包名/类名非法），**不会**在运行时抛异常。

## 捕获示例

### 统一捕获

```kotlin
try {
    val user = mapper.parse(text, User::class.java)
} catch (e: DocumentException) {
    // 覆盖格式错误 + 映射错误 + 类型错误 + OpenAPI 错误
    logger.warn("parse failed: ${e.message}", e)
}
```

### 分层捕获

```kotlin
try {
    val user = mapper.parse(text, User::class.java)
} catch (e: JsonParseException) {
    // JSON 语法/编码错误：输入本身坏了
} catch (e: DocumentSerializationException) {
    // 语法正确但结构不匹配：字段缺失、类型不符、未知字段
} catch (e: DocumentException) {
    // 其他文档层错误（例如自定义转换器抛出的包装异常）
}
```

### 安全取值（不抛异常）

```kotlin
val obj = node.asObjectOrNull() ?: return null
val name = obj["name"]?.asStringOrNull()
```

### 包装为业务异常

```kotlin
try {
    mapper.format(order, file)
} catch (e: DocumentException) {
    throw OrderPersistException("cannot persist order ${order.id}", e)
}
```

## 扩展点

自定义格式模块的推荐结构：

```kotlin
// format-foo/exception/FooException.kt
public open class FooException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : DocumentException(message, throwable, enableSuppression, writableStackTrace)

// format-foo/exception/FooParseException.kt
public class FooParseException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : FooException(message, throwable, enableSuppression, writableStackTrace)
```

内置格式都遵循相同的两层结构：`XxxException`（模块基类，open）+ `XxxParseException`
（解析失败，final）。这样使用方既能捕获整个格式模块，也能只捕获解析错误。

自定义 Mapper 内部逻辑错误建议直接抛 `DocumentSerializationException`，与内置
`serialization` 行为保持一致。

## 限制

- 异常均为 **unchecked**（运行时异常），编译期不强制处理；需要时请显式 `try/catch`；
- `DocumentTypeException` 不含路径、不区分「字段不存在」与「值不是目标类型」，需要这些
  信息请改用 `DocumentSerializationException` 或自行包装；
- 异常消息是英文，便于日志检索，但不是稳定 API，不建议按 message 做分支判断；
- `DocumentException` 的 `enableSuppression` 默认关闭，批量处理时不会自动聚合多个错误
  （JSON Schema 校验是例外：`JsonSchemaValidationException.errors` 汇总全部错误）；
- JDK 8 目标，不能使用 `record` 形式的异常负载；
- 不要继承 `DocumentTypeException`（它是 `final` class）。

## 依赖坐标

```kotlin
implementation("com.mingliqiye.document:exception:2.2.0")
```

通常由 `core` / `serialization` / `format-*` 传递引入；只有需要捕获
`DocumentException` 且不依赖其他模块时（极少见）才需要直接声明。

## 构建

```bash
gradle :exception:test
gradle :exception:ktlintCheck
```

相关文档：

- [异常参考](../reference/exceptions.md)：逐类签名、触发条件、错误消息
- [serialization](./serialization.md)：`DocumentSerializationException` 的完整使用场景
