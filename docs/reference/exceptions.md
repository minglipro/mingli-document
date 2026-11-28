# 异常参考

`mingli-document` 的异常体系以 `DocumentException` 为根，所有异常均为**运行时异常**
（unchecked）。调用方通常只需要捕获 `DocumentException`；需要区分输入错误与映射错误时，
再按具体子类捕获。

```kotlin
try {
    val user = mapper.parse(text, User::class.java)
} catch (e: DocumentException) {
    // 格式解析、Bean 映射、类型不匹配、OpenAPI 配置等全部覆盖
}
```

## 异常层级

```text
java.lang.Throwable
└── java.lang.Exception
    └── java.lang.RuntimeException
        └── com.mingliqiye.exception.MingLiBaseException
            └── com.mingliqiye.document.exception.DocumentException
                │
                ├── DocumentTypeException                       （exception 模块）
                ├── DocumentSerializationException              （serialization）
                ├── JsonException                               （format-json）
                │   └── JsonParseException
                ├── JsonSchemaException                         （format-json）
                ├── JsonSchemaValidationException               （format-json）
                ├── YamlException                               （format-yaml）
                │   └── YamlParseException
                ├── XmlException                                （format-xml）
                │   └── XmlParseException
                ├── PropertiesException                         （format-properties）
                │   └── PropertiesParseException
                ├── TomlException                               （format-toml）
                │   └── TomlParseException
                ├── CsvException                                （format-csv）
                │   └── CsvParseException
                ├── IonException                                （format-ion）
                │   └── IonParseException
                ├── CborException                               （format-cbor）
                │   └── CborParseException
                ├── SmileException                              （format-smile）
                │   └── SmileParseException
                ├── AvroException                               （format-avro）
                │   └── AvroParseException
                ├── ProtobufException                           （format-protobuf）
                │   └── ProtobufParseException
                └── OpenApiException                            （openapi）
                    ├── OpenApiConfigException
                    └── OpenApiSchemaException
```

说明：

- `MingLiBaseException` 来自外部依赖 `com.mingliqiye.exception:mingli-exception:1.1.2-Final`；
- 内置格式统一采用「`XxxException`（open，模块基类）+ `XxxParseException`（final，解析失败）」
  的两层结构；
- `JsonSchemaException` / `JsonSchemaValidationException` 直接继承 `DocumentException`，
  不属于 `JsonException` 分支；
- 所有异常最终都继承 `DocumentException`；多数异常提供 `(message, throwable)` 构造，
  格式模块会把底层解析器异常作为 cause 包装（`JsonSchemaValidationException` 以
  `errors` 列表构造）。

## 根异常

### DocumentException

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
| `message` | `null` | 错误描述 |
| `throwable` | `null` | 原始异常（cause） |
| `enableSuppression` | `false` | 是否启用抑制异常 |
| `writableStackTrace` | `true` | 是否生成堆栈 |

### DocumentTypeException

```kotlin
public class DocumentTypeException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : DocumentException(message, throwable)
```

只在 `core` 的 `Node.asXxx()` 类型不匹配时抛出；消息模板固定：

| 调用 | 消息 |
|---|---|
| `asObject()` | `Expected object but was <type>` |
| `asArray()` | `Expected array but was <type>` |
| `asNumber()` | `Expected number but was <type>` |
| `asString()` | `Expected string but was <type>` |
| `asBoolean()` | `Expected boolean but was <type>` |
| `asBinary()` | `Expected binary but was <type>` |

其中 `<type>` 是 `NodeType.value`（`object` / `array` / `number` / `string` /
`boolean` / `binary` / `null`）。

## 逐异常目录

### serialization

#### DocumentSerializationException

```kotlin
public class DocumentSerializationException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : DocumentException(message, throwable)
```

最常用的业务异常，覆盖：

| 场景 | 典型消息 / 原因 |
|---|---|
| 缺字段且策略为 `FAIL` | 字段名、目标类 |
| 未知字段 + `failOnUnknownFields` | 字段名、目标类 |
| 字段类型无法转换 | 含路径（`$.user.age`） |
| 基本类型字段读到 `null` | 类型不匹配 |
| 无可用构造器 / 实例化失败 | `Cannot instantiate ...` |
| `MissingFieldPolicy` 相关 | 缺字段或空集合构造 |
| 序列化递归超过 128 层 | `Maximum serialization depth exceeded` |
| 对象图存在循环引用（未开启 `writeReferences`） | 循环引用路径 |
| `Node` 树超限 | `Node depth ...` / `Node count ...` / `String length ...` |
| `Node` 树成环 | `Node tree contains a cycle` |
| 二进制 Mapper 调文本 API | `... does not support binary parsing/writing` |
| 文本 Mapper 调 `Reader`/`Writer` 于二进制 | `... does not support character streams` |
| `merge` 目标为 null / 非 Bean | `Merge target must not be null` 等 |
| `merge` 源不是对象节点 | `Expected object ...`（`DocumentTypeException` 包装） |
| Token 流为空/尾随内容 | `Empty token stream` / `Unexpected trailing token content` |
| `$ref` 解析失败 | `Cannot resolve $ref '...'` / `Remote $ref is not enabled: ...` / `Circular $ref detected: ...` |
| 多态类型缺失/未知/类型属性非字符串 | 类型属性名与值 |
| 值类转账失败 | `Cannot box/unbox value class ...` |
| 转换器实例化/执行失败 | `Cannot instantiate converter ...` / cause |
| Kotlin value class / sealed 不支持 | 见 [serialization-kotlin](../modules/serialization-kotlin.md) |

### format-json

| 异常 | 继承 | 触发场景 | 典型消息 |
|---|---|---|---|
| `JsonException` | `DocumentException` | JSON 模块异常基类 | — |
| `JsonParseException` | `JsonException` | JSON/JSONC 语法错误、非法 UTF-8、重复 key `FAIL`、深度超限、数字/布尔字面量非法 | `<message> at line <n>, column <m>`；`Invalid UTF-8 input` |
| `JsonSchemaException` | `DocumentException` | JSON Schema 编译或使用错误 | 自由文本 |
| `JsonSchemaValidationException` | `DocumentException` | JSON Schema 校验失败；`errors: List<JsonSchemaValidationError>` | 所有错误按行拼接 |

### format-yaml / format-xml

| 异常 | 触发场景 | 典型消息 |
|---|---|---|
| `YamlParseException` | YAML 语法错误、NaN/Infinity、递归节点、自定义 tag 非法、alias 超限、`maxStringLength` 超限 | `Invalid YAML: ...`、`Unsupported YAML tag: ...` |
| `XmlParseException` | XML 语法错误、DOCTYPE/外部实体被禁用、无根元素、嵌套深度超限、混合内容不支持 | `Invalid XML: ...`、`XML nesting depth N exceeds maxDepth M` |

（模块基类分别为 `YamlException`、`XmlException`。）

### format-properties / format-toml

| 异常 | 触发场景 | 典型消息 |
|---|---|---|
| `PropertiesParseException` | Properties 解析失败、key 路径非法（空 key、空段、未闭合 `[`、非法 index、冲突路径） | `Properties key must not be empty`、`Conflicting Properties key path '...'` |
| `TomlParseException` | TOML 语法错误、根不是 table、不支持的类型、NaN/Infinity | `Invalid TOML: ...`、`TOML document root must be a table` |

（模块基类分别为 `PropertiesException`、`TomlException`，均继承 `DocumentException`。）

### format-csv

| 异常 | 触发场景 | 典型消息 |
|---|---|---|
| `CsvException` | 模块异常基类；写出非法结构（根不是对象/数组、嵌套单元格、未知/冲突列） | `CSV root must be an object or array but was ...` |
| `CsvParseException` | CSV 解析失败、表头重复、未知/缺失 schema 列、嵌套 JSON 单元格非法 | `Duplicate CSV header '...'`、`Invalid CSV: ...` |

### format-ion / format-cbor / format-smile

| 异常 | 触发场景 | 典型消息 |
|---|---|---|
| `IonParseException` | 文本/二进制 Ion 解析失败、0/多个顶层值、NaN/Infinity、类型保真解析失败 | 自由文本 |
| `CborParseException` | CBOR 字节非法、截断、深度超限、tag 保真解析失败 | 自由文本 |
| `SmileParseException` | Smile 字节非法、截断、深度超限 | 自由文本 |

（模块基类分别为 `IonException`、`CborException`、`SmileException`。）

### format-avro / format-protobuf

| 异常 | 触发场景 |
|---|---|
| `AvroParseException` | Avro wire/OCF 字节非法、schema 不匹配、schema evolution 不兼容 |
| `ProtobufParseException` | protobuf 字节非法、Descriptor 不匹配、未知字段策略触发 |

（模块基类分别为 `AvroException`、`ProtobufException`。）

### openapi

| 异常 | 触发场景 | 典型消息 |
|---|---|---|
| `OpenApiException` | 模块异常基类 | — |
| `OpenApiConfigException` | 标题/版本/Server url 空白；operation 路径/方法/schemaName 非法、operation 重复；字段类型覆盖重复、枚举取值空/重复 | `OpenAPI title must not be blank`、`Operation path must start with '/' ...`、`Unsupported HTTP method: ...`、`Duplicate operation for ...` | 
| `OpenApiSchemaException` | 未注册 Schema、Schema 名非法、字段空/重复、`SchemaType.NULL`、`$ref` 未知、字段覆盖未知、operation 引用未知 Schema、重复 Schema | `At least one DocumentSchema is required`、`Invalid schema name '...'`、`... uses SchemaType.NULL ...` |

## 按操作阶段归类

### 解析阶段

| 输入问题 | 异常 |
|---|---|
| 语法错误 | 格式的 `XxxParseException` |
| 编码错误（非法 UTF-8） | `JsonParseException` |
| 深层嵌套 | 格式解析器超限异常（`XxxParseException`）或 `DocumentSerializationException`（Mapper 层限制） |
| 结构合法但目标类型不匹配 | `DocumentSerializationException` |
| 目标类型是 `Node` | 不校验，直接返回树 |

### 映射阶段

| 问题 | 异常 |
|---|---|
| 字段缺失 + `FAIL` | `DocumentSerializationException` |
| 未知字段 + `failOnUnknownFields` | `DocumentSerializationException` |
| null 写入基本类型字段 | `DocumentSerializationException` |
| 转换器返回非法值 / 自身抛异常 | `DocumentSerializationException`（包装） |
| 多态类型缺失/未知 | `DocumentSerializationException` |
| `$ref` 无法解析 | `DocumentSerializationException` |
| 对象循环 | `DocumentSerializationException` |

### 节点树限制

| 限制 | 异常 |
|---|---|
| `maxNodeDepth` | `DocumentSerializationException: Node depth N exceeds maxNodeDepth M` |
| `maxNodeCount` | `DocumentSerializationException: Node count exceeds maxNodeCount M` |
| `maxStringLength` | `DocumentSerializationException: String length N exceeds maxStringLength M` |
| 成环 | `DocumentSerializationException: Node tree contains a cycle` |

### 配置阶段

| 问题 | 异常 |
|---|---|
| 负数限制 | `IllegalArgumentException` |
| 空白 `typeName` / `typeProperty` | `IllegalArgumentException` |
| 子类型不可赋值 / 类型名重复 / 空白类型名 | `IllegalArgumentException`（`build()` 时） |
| `kotlinSealedSubtypes` 传入非 sealed | `IllegalArgumentException` |
| `kotlinModule` 传入非 value class | `IllegalArgumentException` |
| OpenAPI 标题/版本/url 等 | `OpenApiConfigException` |
| `@DocumentEnumCodec` 参数非法 | KSP 编译错误（不是运行时异常） |

> 配置期不接受「半合法」配置：宁可尽早抛 `IllegalArgumentException`，也避免在映射阶段
> 才失败。

### 实例化阶段

| 问题 | 异常 |
|---|---|
| 无可用构造器且无 `@DocumentCreator` / `@DocumentBuilder` | `DocumentSerializationException` |
| 构造器抛异常 | `DocumentSerializationException`（cause 保留） |
| record 反射不可用（JDK 8） | 按普通 Bean 处理，不报错 |
| Kotlin data class 但未加 `kotlinModule()` | `DocumentSerializationException`（无无参构造器） |

## 捕获模式

### 模式一：统一兜底（推荐）

```kotlin
fun loadUser(text: String): User? = try {
    mapper.parse(text, User::class.java)
} catch (e: DocumentException) {
    logger.warn("load user failed: ${e.message}", e)
    null
}
```

### 模式二：区分输入错误与映射错误

```kotlin
try {
    mapper.parse(text, User::class.java)
} catch (e: JsonParseException) {
    // 调用方给的文本坏了 → 返回 400
    throw BadRequestException("invalid json", e)
} catch (e: DocumentSerializationException) {
    // 文本没问题，但结构不匹配 → 返回 422
    throw UnprocessableEntityException("schema mismatch", e)
}
```

### 模式三：类型安全取值

```kotlin
val obj = node.asObjectOrNull() ?: return null
val name = obj["name"]?.asStringOrNull()
```

### 模式四：仅处理自定义格式异常

```kotlin
try {
    FooMapper().parse(text, User::class.java)
} catch (e: FooParseException) {
    // 只关心 Foo 格式的语法错误
}
```

### 反例

```kotlin
// ❌ 捕获 Throwable 会吞掉 OOM/中断
try { ... } catch (e: Throwable) { }

// ❌ 只捕获 Exception 不够精确，且会把非文档异常也当作解析失败
try { ... } catch (e: Exception) { }

// ❌ 按 message 分支（message 不是稳定契约）
if (e.message?.contains("Expected object") == true) { ... }
```

## cause 链

格式解析异常通常保留底层解析器 cause，排查时建议打印完整堆栈：

```kotlin
catch (e: DocumentException) {
    generateSequence(e) { it.cause }.forEach { println(it) }
}
```

示例 cause 链：

```text
JsonParseException: unexpected character at line 1, column 3
  caused by: ...
```

## 与 IllegalArgumentException 的边界

| 类别 | 异常 |
|---|---|
| 输入文档内容问题 | `DocumentException` 子类 |
| 节点类型访问 | `DocumentTypeException` |
| 调用方配置错误（负数、空名、非法注册） | `IllegalArgumentException` |
| 数字转换溢出 | `ArithmeticException`（`NumberNode.toXxx` / `BigDecimal.xxxValueExact`） |
| Token Reader 状态错误（`intValue()` 非 NUMBER） | `IllegalStateException` |
| 下标越界 | `IndexOutOfBoundsException`（`ArrayNode.get`） |

## 扩展自定义异常

自定义格式模块建议：

```kotlin
public open class FooException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : DocumentException(message, throwable, enableSuppression, writableStackTrace)

public class FooParseException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : FooException(message, throwable, enableSuppression, writableStackTrace)
```

约定：

- 模块基类 `open`，解析异常 `final`；
- 解析异常构造函数应 `@JvmOverloads`，方便 Java 调用；
- 包装底层异常时保留 cause；
- 不要继承 `DocumentTypeException`（它是 `final`）；
- 不要在错误 message 中输出敏感数据（如完整文档内容、密钥）。

## 限制

- 所有异常 unchecked，编译器不提示；
- 异常 message 为英文且不保证跨版本稳定；
- `DocumentTypeException` 不含字段路径；
- 批量错误汇总只有 `JsonSchemaValidationException.errors` 支持；
- 部分底层库异常可能直接透传（例如格式模块未包装的运行时异常），建议按
  `DocumentException` 兜底后再记录原始异常类型；
- KSP/编译期错误不是运行时异常，见 [codegen](../modules/codegen.md)。

相关页面：

- [exception 模块](../modules/exception.md)
- [token 参考](./token.md)
- [api 参考](./api.md)
