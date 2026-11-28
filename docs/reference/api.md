# API 参考

本页是 `mingli-document` 常用 API 的跨模块索引，按「类 / 接口 → 成员」组织，便于速查。
完整行为说明在对应模块页；本页只列签名与一句话语义。

约定：

- 省略包名；`Node` 系列在 `com.mingliqiye.document.core`，Mapper 系列在
  `com.mingliqiye.document.serialization`；
- nullable 用 `?` 标注；
- 「实现类」表示 `format-*` 的 Mapper（如 `JsonMapper`）继承/实现；
- `@JvmStatic` / `@JvmOverloads` 已在签名旁标注，Java 可直接静态调用。

## DocumentMapperApi

`serialization` 模块的格式无关映射接口，所有 Mapper 都实现它。

```kotlin
public interface DocumentMapperApi {
    fun toNode(value: Any?): Node
    fun <T> fromNode(node: Node, targetType: Class<T>): T
    fun <T> fromNode(node: Node, targetType: DocumentTypeReference<T>): T

    fun <T> parse(source: String, targetType: Class<T>): T
    fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T
    fun format(value: Any?): String
    fun formatPretty(value: Any?): String

    fun <T> parse(source: ByteArray, targetType: Class<T>): T
    fun <T> parse(source: ByteArray, targetType: DocumentTypeReference<T>): T
    fun formatBytes(value: Any?): ByteArray

    fun <T> parse(file: File, targetType: Class<T>): T
    fun <T> parse(file: File, targetType: DocumentTypeReference<T>): T
    fun <T> parse(path: Path, targetType: Class<T>): T
    fun <T> parse(path: Path, targetType: DocumentTypeReference<T>): T
    fun <T> parse(input: InputStream, targetType: Class<T>): T
    fun <T> parse(input: InputStream, targetType: DocumentTypeReference<T>): T
    fun <T> parse(reader: Reader, targetType: Class<T>): T
    fun <T> parse(reader: Reader, targetType: DocumentTypeReference<T>): T
    fun format(value: Any?, file: File)
    fun format(value: Any?, path: Path)
    fun format(value: Any?, output: OutputStream)
    fun format(value: Any?, writer: Writer)
    fun formatPretty(value: Any?, file: File)
    fun formatPretty(value: Any?, path: Path)
    fun formatPretty(value: Any?, output: OutputStream)
    fun formatPretty(value: Any?, writer: Writer)

    fun <D> convert(source: Any?, destinationClass: Class<D>): D
    fun <D> convert(source: Any?, destinationType: DocumentTypeReference<D>): D
    fun <T> merge(node: Node, target: T): T
    fun <T> merge(node: Node, target: T, policy: MergePolicy): T

    // 对象映射便捷 API（format-agnostic，语义等价于上面的入口）
    fun readTree(source: String): Node
    fun readTree(source: ByteArray): Node
    fun readTree(file: File): Node
    fun readTree(path: Path): Node
    fun readTree(input: InputStream): Node
    fun readTree(reader: Reader): Node
    fun writeTree(node: Node): String
    fun writeTreeAsBytes(node: Node): ByteArray
    fun valueToTree(value: Any?): Node
    fun <T> treeToValue(node: Node, targetType: Class<T>): T
    fun <T> treeToValue(node: Node, targetType: DocumentTypeReference<T>): T
    fun <T> readValue(source: String, targetType: Class<T>): T
    fun <T> readValue(source: String, targetType: DocumentTypeReference<T>): T
    fun <T> readValue(source: ByteArray, targetType: Class<T>): T
    fun <T> readValue(source: ByteArray, targetType: DocumentTypeReference<T>): T
    fun <T> readValue(file: File, targetType: Class<T>): T
    fun <T> readValue(file: File, targetType: DocumentTypeReference<T>): T
    fun <T> readValue(path: Path, targetType: Class<T>): T
    fun <T> readValue(path: Path, targetType: DocumentTypeReference<T>): T
    fun <T> readValue(input: InputStream, targetType: Class<T>): T
    fun <T> readValue(input: InputStream, targetType: DocumentTypeReference<T>): T
    fun <T> readValue(reader: Reader, targetType: Class<T>): T
    fun <T> readValue(reader: Reader, targetType: DocumentTypeReference<T>): T
    fun writeValueAsString(value: Any?): String
    fun writeValueAsBytes(value: Any?): ByteArray
    fun <T> updateValue(target: T, overrides: Any?): T
    fun <D> convertValue(source: Any?, destinationClass: Class<D>): D
    fun <D> convertValue(source: Any?, destinationType: DocumentTypeReference<D>): D
    fun <T> readerFor(targetType: Class<T>): DocumentObjectReader<T>
    fun <T> readerFor(targetType: DocumentTypeReference<T>): DocumentObjectReader<T>
    fun writer(): DocumentObjectWriter
    fun writerWithDefaultPrettyPrinter(): DocumentObjectWriter
}
```

### reified 扩展（`serialization-kotlin`）

`parse<T>(String/ByteArray/File/Path/InputStream/Reader)`、`fromNode<T>(node)`、
`convert<T>(source)`、`readerFor<T>()` 位于 `serialization-kotlin` 模块的
`com.mingliqiye.document.serialization.kotlin` 包，等价于对应方法传入
`DocumentTypeReference.of<T>()`。纯 Java 的 `serialization` 模块不包含这些 reified API。

### 默认实现与异常

| 方法 | 默认行为 |
|---|---|
| `parse(ByteArray, ...)` | 抛 `DocumentSerializationException("... does not support binary parsing")` |
| `formatBytes(...)` | 抛 `DocumentSerializationException("... does not support binary writing")` |
| `merge(node, target)` | 委托 `merge(node, target, MergePolicy.REPLACE)` |
| `merge(node, target, policy)` | 抛 `DocumentSerializationException("... does not support merge")`（`DocumentMapper` 覆写） |
| `formatPretty(value, file/path/output/writer)` | 基类按 UTF-8 写出（`DocumentMapper` 覆写为可配置字符集） |
| `readTree(...)` | 委托 `parse(..., Any::class.java)` 取得节点；顶层 `null` 返回 `NullNode.INSTANCE` |
| `writeTree` / `writeTreeAsBytes` / `valueToTree` / `treeToValue` | 分别委托 `format` / `formatBytes` / `toNode` / `fromNode` |
| `readValue(...)` / `writeValueAs*` / `convertValue(...)` | 分别委托 `parse` / `format`、`formatBytes` / `convert` |
| `updateValue(target, overrides)` | 委托 `merge(toNode(overrides), target)`，默认 `MergePolicy.REPLACE` |
| `readerFor(...)` / `writer()` / `writerWithDefaultPrettyPrinter()` | 返回轻量包装 `DocumentObjectReader` / `DocumentObjectWriter` |

### 对象映射便捷 API 的语义

- 树/值 API 不新增格式绑定，行为与 `parse` / `format` / `toNode` / `fromNode` 一致；
- `readTree` 的六种来源覆盖 String / ByteArray / File / Path / InputStream / Reader；
  `readValue` 同；`DocumentObjectWriter.writeValue` 覆盖 File / Path / OutputStream / Writer；
- 文本专用的重载在二进制格式上抛 `DocumentSerializationException`；
- `DocumentMapper` 显式覆写全部便捷方法，保证 Java 通过具体 Mapper 类型可直接调用
  （接口默认方法在 JVM 上编译为 `DefaultImpls`，Java 按类成员查找看不到）。

## DocumentObjectReader

`DocumentMapperApi.readerFor(...)` 返回的轻量读取器，绑定目标类型：

| 成员 | 说明 |
|---|---|
| `targetType: DocumentTypeReference<T>` | 绑定的目标类型 |
| `readValue(source: String/ByteArray/File/Path/InputStream/Reader): T` | 等价对应 `parse` / `readValue` 重载 |

## DocumentObjectWriter

`DocumentMapperApi.writer()` / `writerWithDefaultPrettyPrinter()` 返回的轻量写出器：

| 成员 | 说明 |
|---|---|
| `pretty: Boolean` | 是否格式化输出（Java 访问器为 `getPretty()`） |
| `writeValueAsString(value: Any?): String` | 紧凑/格式化文本，等价 `format` / `formatPretty` |
| `writeValueAsBytes(value: Any?): ByteArray` | 紧凑/格式化字节 |
| `writeValue(value: Any?, file: File / path: Path / output: OutputStream / writer: Writer)` | 写入目标；流由调用方关闭 |

## DocumentMapper

`format-*` Mapper 的公共父类（抽象类），构造器为 `protected`，由格式模块调用。

| 成员 | 说明 |
|---|---|
| `formatName: String` | 格式名（`json` / `yaml` / ...） |
| `fromNode(node: Node, targetType: Type): Any?` | 以反射 `Type` 为目标的反序列化 |
| `readFrom(reader: DocumentValueReader, targetType: DocumentTypeReference<T>): T` | 直接从流式读取器反序列化，不构建完整树 |
| `fromTokens(reader, Class/TypeRef/Type)` | Token 流 → 实体/泛型/`Type` |
| `writeTo(value: Any?, writer: DocumentValueWriter)` | 实体 → 流式写出器（不构建完整树） |
| `merge(node, target, policy): T` | 合并实现 |
| `readTree` / `writeTree` / `writeTreeAsBytes` / `valueToTree` / `treeToValue` | 树 API 便捷方法（显式覆写，Java 可直接调用） |
| `readValue` / `writeValueAsString` / `writeValueAsBytes` / `updateValue` / `convertValue` | 值 API 便捷方法（显式覆写） |
| `readerFor` / `writer` / `writerWithDefaultPrettyPrinter` | `DocumentObjectReader` / `DocumentObjectWriter` 工厂（显式覆写） |
| `validateNodeLimits(node: Node)`（`protected`） | 校验节点限制，供格式层复用 |
| `usesTextFormat: Boolean`（`protected open`） | 默认 `true`；二进制格式覆写 `false` |
| `prettyOutputEnabled(): Boolean`（`protected open`） | 默认输出是否 pretty |
| `createTextTokenReader(source: String): DocumentTokenReader?`（`protected open`） | 文本 Token 读取器钩子 |
| `createTokenReader(source: ByteArray): DocumentTokenReader?`（`protected open`） | 字节 Token 读取器钩子 |
| `createStreamingTokenWriter(output: OutputStream, pretty: Boolean): DocumentTokenWriter?`（`protected open`） | 流式写出器钩子 |

`fromTokens` 额外校验：空 Token 流抛 `Empty token stream`；读取后仍有内容抛
`Unexpected trailing token content`。

## BinaryDocumentMapper

二进制格式 Mapper 的基类（`serialization`），构造器为 `protected`，参数与
`DocumentMapper` 相同但用 `parseBytes: (ByteArray) -> Node` 与
`writeBytes: (Node, Boolean) -> ByteArray` 替代文本函数。

- `usesTextFormat = false`；
- 文本 `parse(String)` / `format` 抛 `DocumentSerializationException`；
- 字节 API 由 `parseBytes` / `writeBytes` 提供。

## DocumentMapperConfig

| 属性 | 类型 | 默认值 |
|---|---|---|
| `missingFieldPolicy` | `MissingFieldPolicy` | `KEEP_DEFAULT` |
| `failOnUnknownFields` | `Boolean` | `false` |
| `writeNulls` | `Boolean` | `true` |
| `namingStrategy` | `PropertyNamingStrategy` | `AS_IS` |
| `fileCharset` | `Charset` | UTF-8 |
| `atomicFileWrite` | `Boolean` | `false` |
| `createParentDirectories` | `Boolean` | `false` |
| `maxInputSize` | `Long` | `0`（不限） |
| `maxStringLength` | `Int` | `0`（不限） |
| `maxNodeDepth` | `Int` | `0`（不限） |
| `maxNodeCount` | `Long` | `0`（不限） |
| `resolveReferences` | `Boolean` | `false` |
| `writeReferences` | `Boolean` | `false` |
| `remoteReferenceResolver` | `RemoteReferenceResolver?` | `null` |
| `deserializationFeatures` | `Set<DeserializationFeature>` | 空集合（全部关闭） |

工厂：`DocumentMapperConfig.builder()`、`DocumentMapperConfig.empty()`。
查询：`isEnabled(feature)`、`isEnabled(feature, default)`。

### DocumentMapperConfigBuilder

| 方法 | 校验 |
|---|---|
| `field(declaringClass, name, converter)` | 无 |
| `subtype(baseClass, typeName, subtypeClass)` | `build()` 时校验子类可赋值、类型名非空/重复 |
| `subtype(baseClass, typeName, subtypeClass, typeProperty)` | 同上 + 类型属性名非空白 |
| `typeProperty(baseClass, typeProperty)` | 非空白；`build()` 时要求该基类已注册子类型 |
| `missingFieldPolicy` / `failOnUnknownFields` / `writeNulls` / `namingStrategy` | 无 |
| `fileCharset` / `atomicFileWrite` / `createParentDirectories` | 无 |
| `maxInputSize(Long)` | `>= 0` |
| `maxStringLength(Int)` / `maxNodeDepth(Int)` / `maxNodeCount(Long)` | `>= 0` |
| `resolveReferences` / `writeReferences` / `remoteReferenceResolver` | 无 |
| `enable(feature)` / `disable(feature)` / `configure(feature, enabled)` / `failOnNullForPrimitives(fail)` | 无 |
| `build()` | 非法子类型注册抛 `IllegalArgumentException` |

### 策略类型

| 类型 | 值 |
|---|---|
| `MissingFieldPolicy` | `KEEP_DEFAULT`、`SET_NULL`、`SET_EMPTY`、`FAIL` |
| `PropertyNamingStrategy` | `AS_IS`、`SNAKE_CASE`、`KEBAB_CASE`、`UPPER_CAMEL`、`LOWER_CAMEL`；`translate(name)` |
| `DeserializationFeature` | `FAIL_ON_NULL_FOR_PRIMITIVES` |
| `MergePolicy` | `REPLACE`、`DEEP`、`SKIP_NULLS`、`DEEP_SKIP_NULLS`、`of(deepMerge, skipNulls)`；属性 `deepMerge`、`skipNulls` |

## DocumentMapperBuilder

格式模块 Builder 的公共基类（`format-*` 的 `XxxMapperBuilder` 继承它）：

| 方法 | 说明 |
|---|---|
| `addSerializer(serializer)` | 按 `serializer.getType().getRawType()` 注册 |
| `addDeserializer(deserializer)` | 按类型注册 |
| `addConverter(converter)` | 同时注册序列化与反序列化 |
| `addModule(module: DocumentModule)` | 按 `module.moduleName` 去重注册 |
| `addModules(vararg modules)` | 批量 |
| `config(config: DocumentMapperConfig)` | 设置 Mapper 配置 |
| `objectInstantiator(instantiator: ObjectInstantiator?)` | 设置实例化策略 |
| `propertyMetadataResolver(resolver: PropertyMetadataResolver?)` | 设置属性元数据解析器 |
| `build(): M` | 执行模块 `setupModule` 后创建 Mapper；模块注册项覆盖 Builder 同名项 |

格式模块入口：`JsonMapper.builder()`、`YamlMapper.builder()`、`XmlMapper.builder()`、
`PropertiesMapper.builder()`、`TomlMapper.builder()`、`CsvMapper.builder()`、
`IonMapper.builder()`、`CborMapper.builder()`、`SmileMapper.builder()`、
`AvroMapper.builder()`、`ProtobufMapper.builder()`。

## Node API

### Node（`core`）

| 成员 | 类型 |
|---|---|
| `type` | `NodeType` |
| `isObject` / `isArray` / `isNumber` / `isString` / `isBoolean` / `isBinary` / `isNull` | `Boolean` |
| `asObject()` / `asObjectOrNull()` | `ObjectNode` / `ObjectNode?` |
| `asArray()` / `asArrayOrNull()` | `ArrayNode` / `ArrayNode?` |
| `asNumber()` / `asNumberOrNull()` | `NumberNode` / `NumberNode?` |
| `asString()` / `asStringOrNull()` | `String` / `String?` |
| `asBoolean()` / `asBooleanOrNull()` | `Boolean` / `Boolean?` |
| `asBinary()` / `asBinaryOrNull()` | `ByteArray` / `ByteArray?` |

`asXxx()` 不匹配时抛 `DocumentTypeException`。

### NodeType

`OBJECT`、`ARRAY`、`NUMBER`、`STRING`、`BOOLEAN`、`BINARY`、`NULL`；每个枚举有
`value: String`（`"object"`…`"null"`）。

### ObjectNode

| 成员 | 类型 |
|---|---|
| `size` | `Int` |
| `keys` | `Set<String>`（插入顺序，只读） |
| `containsKey(key)` | `Boolean` |
| `get(key)` | `Node?` |
| `entries()` | `Set<Map.Entry<String, Node>>` |
| `asMap()` | `Map<String, Node>`（只读） |
| `ObjectNode.EMPTY` | `ObjectNode` |
| `ObjectNode.of(vararg Pair<String, Node>)` | 工厂；重复 key 后者覆盖 |
| `ObjectNode.from(Map<String, Node>)` | 工厂；保持迭代顺序 |

### ArrayNode

| 成员 | 类型 |
|---|---|
| `size` | `Int` |
| `get(index)` | `Node`；越界抛 `IndexOutOfBoundsException` |
| `asList()` | `List<Node>`（只读） |
| `iterator()` | `Iterator<Node>`（实现 `Iterable<Node>`） |
| `ArrayNode.EMPTY` | `ArrayNode` |
| `ArrayNode.of(vararg Node)` / `from(Iterable<Node>)` | 工厂 |

### 标量节点

| 类型 | 成员 / 工厂 |
|---|---|
| `StringNode` | `value: String`；`StringNode.of(String)` |
| `NumberNode` | `raw: String`、`value: BigDecimal`、`toByte/toShort/toInt/toLong/toBigInteger/toFloat/toDouble/toBigDecimal()`；`ofRaw(String)`、`of(BigDecimal/Int/Long/Double/Float/BigInteger)` |
| `BooleanNode` | `value: Boolean`；`BooleanNode.of(Boolean)` |
| `BinaryNode` | `value: ByteArray`（每次复制）；`BinaryNode.of(ByteArray)`（入参复制） |
| `NullNode` | `NullNode.INSTANCE`；`isNull = true` |

## DocumentTypeReference

| 成员 | 说明 |
|---|---|
| `type: Type` | 完整泛型类型 |
| `getRawType(): Class<T>` | 原始类型；无法解析抛 `IllegalArgumentException` |
| `DocumentTypeReference.of<T>()` | reified 工厂 |
| `DocumentTypeReference.of(clazz: Class<T>)` | Class 工厂 |
| `DocumentTypeReference.of(type: Type)` | Type 工厂 |
| `DocumentTypeReference.listType(elementType)` | `List<T>` 引用 |
| `DocumentTypeReference.mapType(keyType, valueType)` | `Map<K, V>` 引用 |

## Token API

### DocumentTokenType

```kotlin
START_OBJECT, END_OBJECT, START_ARRAY, END_ARRAY,
FIELD_NAME, STRING, NUMBER, BOOLEAN, NULL, BINARY
```

### DocumentToken

| 工厂 | `type` | 有效字段 |
|---|---|---|
| `startObject()` / `endObject()` | `START_OBJECT` / `END_OBJECT` | 无 |
| `startArray()` / `endArray()` | `START_ARRAY` / `END_ARRAY` | 无 |
| `fieldName(name)` | `FIELD_NAME` | `text` |
| `string(value)` | `STRING` | `text` |
| `number(raw)` | `NUMBER` | `text`（原始数字文本） |
| `boolean(value)` | `BOOLEAN` | `booleanValue` |
| `nullValue()` | `NULL` | 无 |
| `binary(value)` | `BINARY` | `binaryValue`（入参复制） |

字段：`type`、`text?`、`booleanValue?`、`binaryValue?`；构造器为 `private`。

### DocumentTokenReader

| 成员 | 说明 |
|---|---|
| `currentToken: DocumentTokenType?` | 当前 Token；未读或结束为 `null` |
| `textValue: String?` | FIELD_NAME / STRING / NUMBER 的文本 |
| `booleanValue: Boolean?` | 布尔值 |
| `binaryValue: ByteArray?` | 二进制值 |
| `intValue()` / `longValue()` / `doubleValue()` / `decimalValue()` | 从 `textValue` 解析；非法时抛 `IllegalStateException` |
| `nextToken(): DocumentTokenType?` | 前进；结束返回 `null` |

### DocumentTokenWriter

`DocumentTokenWriter : DocumentValueWriter`

| 成员 | 说明 |
|---|---|
| `writeBinary(value: ByteArray)` | 写二进制值（抽象） |
| `flush()` | 刷新底层输出；默认空实现 |
| `writeInt/Long/Double` | 默认转成 `writeNumber(value.toString())` |
| `writeNode(node: Node)` | 默认递归展开 Object/Array/标量/二进制 |
| 其余 | 继承 `DocumentValueWriter`（见下） |

### DocumentValueWriter / DocumentValueReader

- `DocumentValueWriter`：`writeNull/writeString/writeNumber/writeInt/writeLong/writeDouble/
  writeBoolean/startObject/endObject/startArray/endArray/fieldName/writeNode`；
- `DocumentValueReader`：`tryReadNull/readString/readNumberRaw/readInt/readLong/readDouble/
  readBoolean/beginObject/hasNextField/readFieldName/endObject/beginArray/hasNextElement/
  endArray/skipValue/readNode`；
- `NodeTokenReader(node)`：把 `Node` 树展开为 Token 流，用于无原生 Token 实现的回退。

## 转换器与模块

### 转换器接口

| 接口 | 方法 |
|---|---|
| `DocumentBaseSerializer<T>` | `getType(): DocumentTypeReference<T>`；`serialize(value, context): Node` |
| `DocumentBaseDeserializer<T>` | `getType(): DocumentTypeReference<T>`；`deserialize(node, context): T?` |
| `DocumentBaseConverter<T>` | 同时继承上面两个 |

### 上下文

| 类型 | 成员 |
|---|---|
| `SerializationContext` | `mapper: DocumentMapperApi`、`property: DocumentProperty?`、`path: String` |
| `DeserializationContext` | `mapper: DocumentMapperApi`、`property: DocumentProperty?`、`path: String` |
| `InstantiationContext` | `path: String`、`deserialize(node, targetType: Type): Any?` |
| `StreamingInstantiationContext` | `path: String`、`readValue(reader, targetType, property): Any?` |
| `TokenInstantiationContext` | `path: String`、`readValue(reader, targetType, property): Any?`、`skipValue(reader)` |

### DocumentModule / ModuleSetupContext

| 类型 | 成员 |
|---|---|
| `DocumentModule` | `moduleName: String`；`setupModule(context: ModuleSetupContext)` |
| `ModuleSetupContext` | `addSerializer`、`addDeserializer`、`addConverter`、`setObjectInstantiator`、`setPropertyMetadataResolver` |

### 扩展接口

| 类型 | 方法 |
|---|---|
| `ObjectInstantiator`（`fun interface`） | `instantiate(Class, ObjectNode, InstantiationContext): Any?`；`canInstantiate(Class): Boolean = true`；`instantiate(DocumentValueReader, Class, StreamingInstantiationContext): Any? = null`；`instantiate(DocumentTokenReader, Class, TokenInstantiationContext): Any? = null` |
| `PropertyMetadataResolver`（`fun interface`） | `resolve(type: Class<*>, propertyName: String): List<Annotation>?` |
| `RemoteReferenceResolver`（`fun interface`） | `resolve(uri: String): Node` |
| `DocumentProperty` | `declaringClass`、`name`、`type`、`documentName`、`annotations`、`getAnnotation`、`hasAnnotation`、`isIgnored()`、`getRawType()` |

## 代码生成 API（`serialization.codegen`）

| 类型 | 关键成员 |
|---|---|
| `DocumentAccessorCodegen` | `getter(type, propertyName): Function<Any, Any?>?`；`setter(...): BiConsumer<Any, Any?>?` |
| `DocumentRuntimeCodecFactory` | `constructor(namingStrategy = AS_IS, writeNulls = false)`；`converter(type): DocumentBaseConverter<T>` |
| `DocumentCodecSourceGenerator` | `enumConverter(enumType, packageName, converterName = ...): String`；`module(packageName, moduleName, converterTypes): String` |

## Schema / OpenAPI 索引

| 类型 | 关键成员 |
|---|---|
| `DocumentSchema` | `name`、`fields`、`field(name)`；`DocumentSchema.of(...)` |
| `SchemaField` | `name`、`type`、`required`、`nullable`、`description`；`SchemaField.of(...)` |
| `DocumentSchemaBuilder` | `field(name, type, required, nullable, description)`、`build()` |
| `SchemaType` | `STRING`、`INT`、`LONG`、`DOUBLE`、`DECIMAL`、`BOOLEAN`、`BYTES`、`ARRAY`、`OBJECT`、`MAP`、`NULL` |
| `OpenApiConfig` | `title`、`version`、`description`、`servers`、`operations`；`builder(title, version)` |
| `OpenApiConfigBuilder` | `description`、`server`、`servers`、`operation`、`fieldType`、`enumOf`、`refOf`、`arrayOf`、`mapOf`、`build` |
| `OpenApiGenerator` | `addSchema`、`addSchemas`、`generate`、`generateJson(pretty)`；`of(config)`；`OPENAPI_VERSION` |
| `DocumentSchema.toOpenApi(config)` | 扩展函数 |
| `Iterable<DocumentSchema>.toOpenApi(config)` | 扩展函数 |

## 安全限制

| 配置 | 超限异常（`DocumentSerializationException`） |
|---|---|
| `maxNodeDepth` | `Node depth N exceeds maxNodeDepth M` |
| `maxNodeCount` | `Node count exceeds maxNodeCount M` |
| `maxStringLength` | `String length N exceeds maxStringLength M` |
| 循环节点树 | `Node tree contains a cycle` |
| 序列化递归 > 128 | `Maximum serialization depth exceeded` |
| `maxInputSize` | 入口检查输入字节/字符长度（文件、路径、流、字节数组、字符串） |

## 常见调用组合

```kotlin
val json = JsonMapper()

// 文本 ↔ Bean
val text = json.format(user)
val restored = json.parse(text, User::class.java)

// 树 ↔ Bean（跨格式）
val yamlText = YamlMapper().format(json.toNode(user))

// 泛型集合 / 文件
val users = json.parse(text, DocumentTypeReference.listType(User::class.java))
json.format(user, File("user.json"))

// 二进制 / 合并
val bytes = CborMapper().formatBytes(user)
json.merge(ObjectNode.of("age" to NumberNode.of(20)), user, MergePolicy.DEEP)

// Token（自定义 reader）
val decoded = json.fromTokens(MyTokenReader(values), User::class.java)
```

## API 稳定性

- `public` API 在 2.x 内保持向后兼容；异常 message 非稳定契约；
- 构造器为 `protected` 的类型（`DocumentMapper`、`BinaryDocumentMapper`）只允许格式模块继承；
- 格式 Mapper 的 `internal constructor` 请改用 `builder()` 或公开构造器创建；
- `internal` 辅助类型（`Default*`、`Simple*`、Kotlin 内部实现等）不属于公共 API；
- 升级时建议重新编译（Kotlin 默认参数在二进制层面新增参数会影响已有编译产物）。

相关页面：[注解参考](./annotations.md) · [异常参考](./exceptions.md) ·
[Token 参考](./token.md) · [serialization 模块](../modules/serialization.md)
