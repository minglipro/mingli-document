---
title: Protobuf
description: format-protobuf 模块：ProtobufMapper、ProtobufSchema/Descriptor、extension、group、MessageSet、未知字段策略与 proto3 语义
---

# Protobuf

`format-protobuf` 提供 Protocol Buffers 二进制读写，基于 `Descriptor` / `DynamicMessage`，核心类型是 `ProtobufMapper`。

与 JSON/YAML 这类自描述格式不同，Protobuf 的数据布局完全由 message Schema 决定：读写都必须先有 `Descriptor`。模块提供 `ProtobufSchema` 来构建 Schema（可生成 `Descriptor`），也支持直接使用 protoc 生成的 `Descriptor`。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:protobuf:2.0.0")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `protobuf` |
| 版本 | `2.0.0` |
| 传递依赖 | `schema`、`core`、`exception`、`serialization` |
| 第三方依赖 | `com.google.protobuf:protobuf-java:3.25.5` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.protobuf` |

## 特性

- **标量类型**：全部 Protobuf 标量（含无符号类型）；
- **string / bytes**：`bytes` 接受 `BinaryNode` 或 Base64 字符串；
- **enum**：读取已知值为名称字符串，未知数字保留为数字；写出接受名称或数字；
- **repeated**：`ArrayNode`，proto3 可 packed 的标量按 packed 读写；
- **map**：`ObjectNode`，key 统一转为字符串；
- **嵌套 message**：任意层级嵌套；
- **oneof**：同一 oneof 只能设置一个字段；
- **proto2 group**：start-group / end-group wire 标签；
- **extension**：注册后以对象字段形式读写（含嵌套作用域 / repeated / message 类型）；
- **MessageSet wire format**：`option message_set_wire_format = true` 自动识别；
- **未知字段策略**：`FAIL` / `IGNORE` / `PRESERVE`；
- **Schema 层**：`ProtobufSchema` → `Descriptor`，或从 `Descriptor` 还原 Schema，或导出 JSON 风格描述；
- **解析深度限制**：`ProtobufMapper(..., maxDepth)`；
- **文件/流读写**：二进制语义；
- **Mapper 复用**：`ProtobufMapperFactory` 按 Descriptor 缓存，线程安全。

## Schema 层

### `ProtobufSchema`

| 成员 | 说明 |
|---|---|
| `messageName` | message 名称，可含嵌套点号（如 `Outer.Inner`） |
| `packageName` | package 名；没有时为 `null` |
| `messageFullName` | `package.message`，无 package 时等于 `messageName` |
| `protobufFields: List<ProtobufSchemaField>` | Protobuf 字段列表，保持声明顺序 |
| `nestedMessages: List<ProtobufMessage>` | 嵌套 message 定义 |
| `enums: List<ProtobufEnum>` | 嵌套 enum 定义 |
| `messageSetWireFormat` | 是否使用 MessageSet wire format |
| `fields: List<SchemaField>` | 格式无关视图；repeated 字段为 `SchemaType.ARRAY` |
| `name` | 等于 `messageFullName` |
| `field(name)` / `protobufField(name)` / `protobufField(number)` | 按名称/编号查询 |
| `nestedMessage(name)` / `enumeration(name)` | 查询嵌套类型 |
| `toDescriptor(dependencies: Array<FileDescriptor> = emptyArray())` | 生成完整 `Descriptor` |
| `toJsonDescription(): Map<String, Any?>` | 导出 JSON 风格描述 |
| `ProtobufSchema.builder(messageName, packageName = null)` | 构建器 |
| `ProtobufSchema.fromDescriptor(descriptor)` | 从 `Descriptor` 还原 |
| `ProtobufSchema.fromJsonDescription(description)` | 从 JSON 风格描述还原 |

约束：

- message 名称、package 名称、字段名、字段编号、嵌套类型名均校验；
- 字段名与字段编号不允许重复；
- `messageSetWireFormat = true` 的 message 不能声明普通字段；
- `ProtobufSchema` 不能声明 extension 与 extension range（extension 通过外部 `FileDescriptor` 注册）。

`toDescriptor()` 生成 **proto2 语法**的单文件 Descriptor：字段类型/编号/repeated/required/oneof 映射为对应 `FieldDescriptorProto`，message/enum/group 类型引用解析为文件内绝对类型名，map 字段生成 `map_entry = true` 的嵌套 entry message，嵌套定义递归生成；`messageSetWireFormat` 设置对应 option。

### `ProtobufSchemaBuilder`

| 方法 | 说明 |
|---|---|
| `field(name, fieldNumber, type: ProtobufType, repeated = false, required = true, nullable = false, description = null, typeReference = null, oneofName = null)` | 添加字段 |
| `field(name, fieldNumber, type: SchemaType, ...)` | 使用格式无关类型（按 `ProtobufType.fromSchemaType` 映射） |
| `field(field: ProtobufSchemaField)` | 添加已构建字段 |
| `messageField(name, fieldNumber, messageTypeName, repeated = false, required = true, nullable = false, description = null, oneofName = null)` | message 字段，类型名相对当前 message 或以 `.` 开头的全限定名 |
| `messageField(name, fieldNumber, messageType: Descriptor, ...)` | 使用外部 `Descriptor` 的 message 字段 |
| `groupField(name, fieldNumber, messageTypeName, repeated = false, required = false, nullable = false, description = null)` | proto2 group 字段（对应 message 必须已作为嵌套 message 定义） |
| `groupField(name, fieldNumber, messageType: Descriptor, ...)` | 使用外部 group message Descriptor |
| `enumField(name, fieldNumber, enumTypeName, repeated = false, required = true, nullable = false, description = null, oneofName = null)` | enum 字段 |
| `enumField(name, fieldNumber, enumType: EnumDescriptor, ...)` | 使用外部 enum Descriptor |
| `mapField(name, fieldNumber, keyType: ProtobufType, valueType: ProtobufType, valueReference = null, description = null)` | map 字段 |
| `nestedMessage(message: ProtobufMessage)` / `nestedMessage(name) { ... }` | 嵌套 message（第二个重载接收 `ProtobufMessageBuilder` DSL） |
| `enumeration(enumSchema: ProtobufEnum)` / `enumeration(name, vararg values: Pair<String, Int>)` | 嵌套 enum |
| `messageSetWireFormat(enabled = true)` | 设置 MessageSet wire format |
| `build()` | 构建（校验字段名/编号/嵌套类型名重复） |

`ProtobufMessageBuilder`（嵌套 message 的 DSL 构建器，也可单独 `ProtobufMessage.builder(name)` 创建）提供与 Schema 构建器一致的方法：`field`、`messageField`、`groupField`、`enumField`、`mapField`、`nestedMessage`、`enumeration`、`build()`。

### `ProtobufSchemaField`

| 属性 | 说明 |
|---|---|
| `name` | 字段名 |
| `fieldNumber` | 字段编号（`1..536870911`，不能用保留区间 `19000..19999`） |
| `protobufType` | `ProtobufType` |
| `protobufTypeName` | `.proto` 类型名（如 `int32`） |
| `repeated` | 是否 repeated |
| `required` | 是否必填 |
| `nullable` | 是否允许 `null` |
| `description` | 字段描述 |
| `typeReference` | 复杂类型引用（message/group/enum/map） |
| `oneofName` | 所属 oneof；`null` 表示不属于 oneof |
| `elementType` | 非 repeated 即字段类型；repeated 为元素类型 |
| `schemaType` | 格式无关类型；repeated 为 `SchemaType.ARRAY` |
| `messageTypeName` / `enumTypeName` / `mapType` | 类型引用派生的便捷访问 |
| `toSchemaField()` / `toJsonDescription()` | 转换 |

校验：

- map 字段不能 repeated；map key 必须是可作 key 的标量（整型/布尔/字符串）；map value 不能是 map 或 group；value 为 message/enum 时必须提供对应引用；
- oneof 字段不能 repeated、不能是 map/group、不能 required。

### `ProtobufType` 与 `ProtobufTypeReference`

| `ProtobufType` | `typeName` | `schemaType` |
|---|---|---|
| `DOUBLE` | `double` | `DOUBLE` |
| `FLOAT` | `float` | `DOUBLE` |
| `INT32` | `int32` | `INT` |
| `INT64` | `int64` | `LONG` |
| `UINT32` | `uint32` | `LONG` |
| `UINT64` | `uint64` | `DECIMAL` |
| `SINT32` | `sint32` | `INT` |
| `SINT64` | `sint64` | `LONG` |
| `FIXED32` | `fixed32` | `LONG` |
| `FIXED64` | `fixed64` | `DECIMAL` |
| `SFIXED32` | `sfixed32` | `INT` |
| `SFIXED64` | `sfixed64` | `LONG` |
| `BOOL` | `bool` | `BOOLEAN` |
| `STRING` | `string` | `STRING` |
| `BYTES` | `bytes` | `BYTES` |
| `ENUM` | `enum` | `STRING` |
| `MESSAGE` | `message` | `OBJECT` |
| `GROUP` | `group` | `OBJECT` |
| `MAP` | `map` | `MAP` |

`ProtobufType.fromTypeName(name)` / `fromSchemaType(schemaType)`（`ARRAY`、`NULL` 无法映射为单个 Protobuf 类型，抛 `IllegalArgumentException`）。

`ProtobufTypeReference`（sealed interface）：

| 类型 | 用途 |
|---|---|
| `MessageType(typeName)` | message / group 字段引用；相对名或以 `.` 开头的全限定名 |
| `EnumType(typeName)` | enum 字段引用 |
| `MapType(keyType, valueType, valueReference?)` | map 字段类型 |

工厂：`ProtobufTypeReference.messageType(...)`、`enumType(...)`、`mapType(...)`。

### 嵌套 message / enum

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufSchema
import com.mingliqiye.document.format.protobuf.ProtobufType

val schema = ProtobufSchema.builder("Container")
    .nestedMessage("Payload") {
        field("name", 1, ProtobufType.STRING)
        field("count", 2, ProtobufType.INT32, required = false)
    }
    .messageField("payload", 1, "Payload")
    .enumeration("Status", "NEW" to 0, "DONE" to 1)
    .enumField("status", 2, "Status", required = false)
    .build()
```

## Mapper 构造与 Factory

`ProtobufMapper` 构造器：

| 构造器 | 说明 |
|---|---|
| `ProtobufMapper(descriptor, unknownFieldPolicy = FAIL, maxDepth = 0, extensions = ProtobufExtensionRegistry.EMPTY)` | 主构造器 |
| `ProtobufMapper(schema: ProtobufSchema)` | 由 Schema 生成 Descriptor |
| `ProtobufMapper(schema: ProtobufSchema, unknownFieldPolicy)` | 同上，指定策略 |
| `ProtobufMapper(descriptor, extensions: ProtobufExtensionRegistry)` | 默认 `FAIL` 策略 |
| `ProtobufMapper(descriptor, unknownFieldPolicy, extensions)` | 指定策略与 extension |

属性：`descriptor`、`unknownFieldPolicy`、`maxDepth`、`extensions`；常量 `ProtobufMapper.FORMAT_NAME = "protobuf"`。

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapper
import com.mingliqiye.document.format.protobuf.ProtobufUnknownFieldPolicy

val descriptor = schema.toDescriptor()
val mapper = ProtobufMapper(
    descriptor,
    ProtobufUnknownFieldPolicy.FAIL,
    maxDepth = 64,
)
```

### `ProtobufMapperFactory`

同一 Descriptor 共享一个 Mapper 实例，避免重复创建：

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapperFactory
import com.mingliqiye.document.format.protobuf.ProtobufUnknownFieldPolicy

val factory = ProtobufMapperFactory(
    unknownFieldPolicy = ProtobufUnknownFieldPolicy.IGNORE,
)
val userMapper = factory.mapper(userDescriptor)
val orderMapper = factory.mapper(orderDescriptor)
```

Factory 内部用 `ConcurrentHashMap<Descriptor, ProtobufMapper>` 缓存；Mapper 是线程安全的读对象，建议按 Descriptor 复用，Descriptor / FileDescriptor / extension 注册表也应只构建一次。

### 复用 Mapper + 显式 Descriptor

不同 message 类型可以共用一个 Mapper，在调用时传入本次 Descriptor：

```kotlin
val mapper = ProtobufMapper(defaultDescriptor)
val bytes = mapper.formatBytes(value, currentDescriptor)
val node = mapper.parseToNode(bytes, currentDescriptor)
val bean = mapper.parse(bytes, currentDescriptor, User::class.java)
```

## 读取 API

| 方法 | 说明 |
|---|---|
| `parse(source: ByteArray, targetType)` | 使用 Mapper 的 `descriptor` 解析 → Node → 实体 |
| `parse(source: ByteArray, descriptor: Descriptor, targetType: Class<T>)` | 使用显式 Descriptor |
| `parse(source: ByteArray, descriptor, targetType: DocumentTypeReference<T>)` | 显式 Descriptor 的泛型版本 |
| `parse(file: File / path: Path, targetType)` | 二进制文件 → 实体 |
| `parse(input: InputStream, targetType)` | 二进制流 → 实体 |
| `parseToNode(source: ByteArray, descriptor: Descriptor): Node` | 解析为 Node |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 Token 读取器 → 实体 |

不支持的文本入口（抛 `DocumentSerializationException`）：`parse(String)`、`parse(reader: Reader)`、`format`、`formatPretty`、`format(value, writer)`。

读取行为：

- 使用 `DynamicMessage.parseFrom(descriptor, bytes, extensionRegistry)` 解析；
- 解析成功后经 `ProtobufWireCodec` 转成 `Node`；
- 未知字段按 `unknownFieldPolicy` 处理；
- descriptor 带 `message_set_wire_format` 时先做 MessageSet 结构校验；
- 容器级嵌套深度超过 `maxDepth` 抛 `ProtobufParseException`；
- proto3 默认值字段在 Node 中**不出现**（解码时省略默认值 / 未设置字段）；读取结果对象只包含实际存在或非默认的字段。

## 写出 API

| 方法 | 说明 |
|---|---|
| `formatBytes(value)` | 实体 / Map / Node → Protobuf 字节（使用 Mapper 的 `descriptor`） |
| `formatBytes(value, descriptor: Descriptor)` | 使用显式 Descriptor 写出 |
| `format(value, file / path)` | 写二进制文件 |
| `format(value, output: OutputStream)` | 写二进制流 |
| `parseAll*` / `formatAll` | 不支持（Protobuf 没有多消息流 API，需要自行按长度前缀处理） |

写出细节：

- 实体先经 `toNode` 转成对象节点，再由 `ProtobufWireCodec.encode` 编码为 `DynamicMessage.toByteArray()`；
- 对象节点中出现 Descriptor 与注册表都未声明的字段名 → `ProtobufException`；
- `null` 表示“未设置”（跳过），必填字段为 `null` 抛 `ProtobufException`；
- oneof 同时设置多个字段 → `ProtobufException`；
- 字符串写出前校验可编码为合法 UTF-8（拒绝未配对 UTF-16 代理项）；
- 数字范围按字段类型校验（如 int32 越界抛 `ProtobufException`）；
- `enum` 接受名称或数字；
- `bytes` 接受 `BinaryNode` 或 Base64 `StringNode`。

## 类型映射（Node ↔ Protobuf）

| Protobuf 类型 | 解码为 Node | 编码接受 |
|---|---|---|
| `double` / `float` | `NumberNode`（NaN/Infinity 抛 `ProtobufParseException`） | `NumberNode` |
| `int32` / `sint32` / `sfixed32` | `NumberNode`（`Int` 值） | `NumberNode` |
| `uint32` / `fixed32` | `NumberNode`（无符号值用 `Long` 表示） | `NumberNode` |
| `int64` / `sint64` / `sfixed64` | `NumberNode`（`Long` 值） | `NumberNode` |
| `uint64` / `fixed64` | `NumberNode`（`raw` 为无符号十进制字符串） | `NumberNode` |
| `bool` | `BooleanNode` | `BooleanNode` |
| `string` | `StringNode` | `StringNode`（UTF-8 校验） |
| `bytes` | `BinaryNode` | `BinaryNode` 或 Base64 `StringNode` |
| `enum` | 已知值 `StringNode`（名称）；未知数字 `NumberNode` | `StringNode` 名称或 `NumberNode` 数字 |
| `message` / `group` | `ObjectNode` | `ObjectNode` |
| `repeated` | `ArrayNode`（空 repeated 不出现） | `ArrayNode` |
| `map` | `ObjectNode`（key 统一为字符串） | `ObjectNode`，key 字符串按字段类型解析 |
| 未知字段（`PRESERVE`） | `$unknownFields` 对象字段 | 同结构写回 |

map key 字符串形式：

- `string` key → 原样字符串；
- `bool` key → `"true"` / `"false"`；
- 有符号整数 key → 十进制字符串；
- 无符号整数 key → 无符号十进制字符串。

presence（字段存在性）：

- `optional` / `required` / proto3 `optional`：Node 对象是否包含该 key 表示 presence，`null` 表示未设置；解码时只输出已设置字段；
- 写出时显式设置默认值仍会保留 presence；
- proto3 无 `optional` 的 singular 标量：解码省略默认值、编码默认值不写 wire（与官方编码一致）。

## 未知字段策略

未知字段指 wire 中出现但 Descriptor 未声明（也不是已注册 extension）的字段编号。

| 策略 | 读取 | 写出 |
|---|---|---|
| `FAIL`（默认） | 遇到未知字段抛 `ProtobufParseException` | 对象出现未声明字段（包括 `$unknownFields`）抛异常 |
| `IGNORE` | 丢弃未知字段，只映射声明字段与已注册 extension | 对象出现未声明字段仍抛异常 |
| `PRESERVE` | 未知字段映射为 `$unknownFields` 字段并支持写回 | 接受 `$unknownFields` 并按原 wire 类型还原 |

`ProtobufUnknownFields` 字段名与结构：

```text
$unknownFields
  "99":  { "varint": 1 }
  "100": { "varint": [1, 2] }              // 同一编号出现多次 -> 数组
  "101": { "fixed32": 4294967295 }
  "102": { "fixed64": 18446744073709551615 }
  "103": { "lengthDelimited": <BinaryNode> }
  "104": { "groups": { "1": { "varint": 5 } } }
```

常量：`ProtobufUnknownFields.FIELD_NAME = "$unknownFields"`、`VARINT`、`FIXED32`、`FIXED64`、`LENGTH_DELIMITED`、`GROUPS`。

规则：

- key 为十进制字段编号（按编号升序）；
- 值为对象，最多包含 `varint` / `fixed32` / `fixed64` / `lengthDelimited` / `groups` 五种 wire 类型键；
- `varint` / `fixed64` 用无符号 64 位十进制，`fixed32` 用无符号 32 位数字，`lengthDelimited` 用 `BinaryNode`，`groups` 为嵌套相同结构；
- 解析与写出必须使用同一策略，否则对象中的 `$unknownFields` 会被当作未声明字段拒绝；
- map entry 内部未知字段在 `IGNORE` / `PRESERVE` 下不保留（map 值不是对象节点），`FAIL` 仍会报错；
- MessageSet 未知 item 在解析后以 `lengthDelimited` 形式出现（键为 type ID），写出时会按 MessageSet item 格式还原。

## extension

Protobuf extension 是“在 message 声明之外”声明的字段：wire 上仍是普通字段，但只有拿到对应 `FieldDescriptor` 才能解析。注册后以对象字段形式读写。

`ProtobufExtensionRegistry`：

| 成员 | 说明 |
|---|---|
| `ProtobufExtensionRegistry.EMPTY` | 空注册表 |
| `of(vararg extensions: FieldDescriptor)` | 默认全名命名注册 |
| `of(fileDescriptor: FileDescriptor)` | 注册文件中（含嵌套 message 内声明）的全部 extension |
| `of(fileDescriptor, naming)` | 指定命名方式 |
| `builder()` | 构建器：`add` / `addAll` / `naming` / `build` |
| `naming` | 命名方式 |
| `extensions` | 已注册 extension 列表，保持注册顺序 |
| `protobufRegistry` | 底层 `com.google.protobuf.ExtensionRegistry` |
| `isEmpty` | 是否为空 |
| `documentName(extension)` | extension 在当前命名方式下的文档字段名 |
| `extensionsFor(containingType)` | 某个消息已注册的 extension |
| `extension(containingType, documentName)` / `extension(containingType, fieldNumber)` | 按名/编号查找 |

`ProtobufExtensionNaming`：

| 值 | 文档字段名示例 | 说明 |
|---|---|---|
| `FULL_NAME`（默认） | `my.package.nickname` | Descriptor 保证唯一 |
| `SIMPLE_NAME` | `nickname` | 可读性好，但同一消息内同名会冲突（构建时报错） |
| `BRACKETED` | `[my.package.nickname]` | 与官方 Protobuf JSON 扩展写法一致 |

约束：

- 注册表中的 extension 必须与 Mapper 使用的 `Descriptor` 来自**同一个 Descriptor 实例**（通常是同一个 `FileDescriptor` 树）。Protobuf 按实例匹配 extension，用重建的 Descriptor 注册会按未知字段处理；
- 未注册的 extension 与普通未知字段一样由未知字段策略处理；
- 同一消息内文档字段名冲突（或与普通字段同名）时 `build()` 抛 `IllegalArgumentException`。

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufExtensionNaming
import com.mingliqiye.document.format.protobuf.ProtobufExtensionRegistry
import com.mingliqiye.document.format.protobuf.ProtobufMapper

// fileDescriptor 为包含 extend 声明的 FileDescriptor
val extensions = ProtobufExtensionRegistry.of(fileDescriptor)
val descriptor = fileDescriptor.findMessageTypeByName("Base")
val mapper = ProtobufMapper(descriptor, extensions)

val node = mapper.parse(bytes, com.mingliqiye.document.core.Node::class.java)
val nickname = node.asObject()["my.package.nickname"]?.asString()

val restored = mapper.formatBytes(
    com.mingliqiye.document.core.ObjectNode.of(
        "my.package.nickname" to com.mingliqiye.document.core.StringNode.of("小于"),
    ),
)

// 自定义命名方式
val simple = ProtobufExtensionRegistry.builder()
    .addAll(fileDescriptor.extensions)
    .naming(ProtobufExtensionNaming.SIMPLE_NAME)
    .build()
```

## proto2 group

group 在 wire 上使用 start-group/end-group 标签包围嵌套字段，语义与 message 相同。

Schema 声明：

```kotlin
val schema = ProtobufSchema.builder("Container")
    .nestedMessage("Payload") {
        field("name", 1, ProtobufType.STRING, required = false)
    }
    .groupField("payload", 1, "Payload")
    .build()
```

也可以直接使用外部 Descriptor 的 `TYPE_GROUP` 字段（`ProtobufType.GROUP`）读写。

## MessageSet

`option message_set_wire_format = true` 的 message 自动按 MessageSet wire format 读写；内容只能通过 extension 表达。

```kotlin
val schema = ProtobufSchema.builder("MessageSetRoot")
    .messageSetWireFormat()
    .build()

val mapper = ProtobufMapper(messageSetDescriptor, ProtobufExtensionRegistry.of(fileDescriptor))
val node = mapper.parse(bytes, Node::class.java)   // {"my.package.extension": {...}}
val restored = mapper.formatBytes(node)            // start group + type_id + message + end group
```

规则：

- MessageSet message 不能声明普通字段；
- 已知 extension 映射为对象字段；
- 未知 MessageSet item 由未知字段策略处理；`PRESERVE` 时解析为 `$unknownFields` 并可写回；
- `PRESERVE` 只支持 length-delimited 的 MessageSet 未知字段；
- 已知 extension payload 采用惰性解析；本模块在根消息解析前做了额外结构校验，因此根 MessageSet 的畸形 payload 会报 `ProtobufParseException`；嵌套在其他消息字段里的 MessageSet 不做该预校验（仍由 protobuf-java 惰性解析）。

## proto3 语义

- 无 `optional` 的 singular 标量字段：默认值不写 wire（与官方编码一致）；解析时默认值不产生字段（Node 中不出现）；
- `optional`（含 proto3 `optional` 与 proto2 optional/required）：按 presence 处理，显式写默认值会保留 presence；`null` 表示未设置；
- `string`：写出前校验可编码为合法 UTF-8（拒绝未配对 UTF-16 代理项）；proto3 `string` 解析时由 protobuf-java 严格校验 UTF-8，proto2 非法 UTF-8 由 protobuf-java 以替换字符解码；
- `bytes` 接受 `BinaryNode` 或 Base64 字符串；`enum` 接受名称或数字（未知数字保留为数字）；
- 可 packed 的 repeated 标量在 proto3 中按 packed 读写；
- Node 数字模型不支持 NaN / Infinity；presence 语义不改变 Node 表示（由字段是否出现表达）。

## 示例

### 1. Schema → Descriptor → Mapper

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapper
import com.mingliqiye.document.format.protobuf.ProtobufSchema
import com.mingliqiye.document.format.protobuf.ProtobufType

val schema = ProtobufSchema.builder("User", "com.example")
    .field("id", 1, ProtobufType.INT64)
    .field("name", 2, ProtobufType.STRING, required = false)
    .build()

val descriptor = schema.toDescriptor()
val mapper = ProtobufMapper(descriptor)

class User {
    var id: Long = 0
    var name: String? = null
}

val user = User().apply {
    id = 7
    name = "小于"
}

val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
check(restored.id == 7L)
```

### 2. Node 读写

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse(bytes, Node::class.java)
println(node.asObject()["id"]?.asNumber()?.toLong())   // 7
println(node.asObject()["name"]?.asString())           // 小于
```

### 3. repeated / map / enum / 嵌套 message

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufSchema
import com.mingliqiye.document.format.protobuf.ProtobufType

val schema = ProtobufSchema.builder("Profile")
    .nestedMessage("Address") {
        field("city", 1, ProtobufType.STRING)
    }
    .enumeration("Level", "LOW" to 0, "HIGH" to 1)
    .field("nickname", 1, ProtobufType.STRING, required = false)
    .field("tags", 2, ProtobufType.STRING, repeated = true)
    .mapField("scores", 3, ProtobufType.STRING, ProtobufType.INT32)
    .enumField("level", 4, "Level", required = false)
    .messageField("address", 5, "Address", required = false)
    .build()

val mapper = ProtobufMapper(schema)
val node = com.mingliqiye.document.core.ObjectNode.of(
    "nickname" to com.mingliqiye.document.core.StringNode.of("小于"),
    "tags" to com.mingliqiye.document.core.ArrayNode.of(
        com.mingliqiye.document.core.StringNode.of("a"),
        com.mingliqiye.document.core.StringNode.of("b"),
    ),
    "scores" to com.mingliqiye.document.core.ObjectNode.of(
        "math" to com.mingliqiye.document.core.NumberNode.of(90),
    ),
    "level" to com.mingliqiye.document.core.StringNode.of("HIGH"),
    "address" to com.mingliqiye.document.core.ObjectNode.of(
        "city" to com.mingliqiye.document.core.StringNode.of("杭州"),
    ),
)

val bytes = mapper.formatBytes(node)
val restored = mapper.parse(bytes, Node::class.java)
println(restored.asObject()["scores"]?.asObject()?.get("math")?.asNumber()?.toInt())
```

### 4. oneof

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufSchema
import com.mingliqiye.document.format.protobuf.ProtobufType

val schema = ProtobufSchema.builder("Payment")
    .field("card", 1, ProtobufType.STRING, required = false, oneofName = "method")
    .field("wallet", 2, ProtobufType.STRING, required = false, oneofName = "method")
    .build()

val mapper = ProtobufMapper(schema)

// 同时设置两个 oneof 字段会抛 ProtobufException
val conflicted = com.mingliqiye.document.core.ObjectNode.of(
    "card" to com.mingliqiye.document.core.StringNode.of("visa"),
    "wallet" to com.mingliqiye.document.core.StringNode.of("alipay"),
)
val error = runCatching { mapper.formatBytes(conflicted) }.exceptionOrNull()
check(error is com.mingliqiye.document.format.protobuf.exception.ProtobufException)
```

### 5. MapperFactory 复用

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapperFactory
import com.mingliqiye.document.format.protobuf.ProtobufUnknownFieldPolicy

val factory = ProtobufMapperFactory(ProtobufUnknownFieldPolicy.IGNORE)
val mapper1 = factory.mapper(schema.toDescriptor())
val mapper2 = factory.mapper(schema.toDescriptor())
check(mapper1 === mapper2)   // 同一 Descriptor 共享实例
```

### 6. 显式 Descriptor 调用

```kotlin
val mapper = ProtobufMapper(defaultDescriptor)

val node = mapper.parseToNode(bytes, currentDescriptor)
val bean = mapper.parse(bytes, currentDescriptor, User::class.java)
val out = mapper.formatBytes(bean, currentDescriptor)
```

### 7. 从 Descriptor 还原 Schema

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufSchema

// descriptor 可以来自 protoc 生成代码（Foo.getDescriptor().findMessageTypeByName("Bar")）
val view = ProtobufSchema.fromDescriptor(descriptor)
println(view.messageFullName)
println(view.toJsonDescription())

val back = ProtobufSchema.fromJsonDescription(view.toJsonDescription())
```

### 8. 未知字段策略对比

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapper
import com.mingliqiye.document.format.protobuf.ProtobufSchema
import com.mingliqiye.document.format.protobuf.ProtobufType
import com.mingliqiye.document.format.protobuf.ProtobufUnknownFieldPolicy

// 新版 Schema：id + extra
val newer = ProtobufSchema.builder("Item")
    .field("id", 1, ProtobufType.INT32)
    .field("extra", 2, ProtobufType.STRING, required = false)
    .build()
val bytes = ProtobufMapper(newer).formatBytes(
    com.mingliqiye.document.core.ObjectNode.of(
        "id" to com.mingliqiye.document.core.NumberNode.of(1),
        "extra" to com.mingliqiye.document.core.StringNode.of("新字段"),
    ),
)

// 旧版 Schema 只认识 id
val older = ProtobufSchema.builder("Item")
    .field("id", 1, ProtobufType.INT32)
    .build()
val olderDescriptor = older.toDescriptor()

// FAIL：未知字段报错
val failMapper = ProtobufMapper(olderDescriptor, ProtobufUnknownFieldPolicy.FAIL)
check(runCatching { failMapper.parse(bytes, Node::class.java) }.isFailure)

// IGNORE：丢弃未知字段
val ignored = ProtobufMapper(olderDescriptor, ProtobufUnknownFieldPolicy.IGNORE)
println(ignored.parse(bytes, Node::class.java))

// PRESERVE：保留到 $unknownFields，并可写回
val preserving = ProtobufMapper(olderDescriptor, ProtobufUnknownFieldPolicy.PRESERVE)
val node = preserving.parse(bytes, Node::class.java)
val unknown = node.asObject()["\$unknownFields"]
println(unknown)

val roundTrip = preserving.parse(preserving.formatBytes(node), Node::class.java)
check(roundTrip.asObject()["\$unknownFields"] != null)
```

### 9. extension 读写

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufExtensionNaming
import com.mingliqiye.document.format.protobuf.ProtobufExtensionRegistry
import com.mingliqiye.document.format.protobuf.ProtobufMapper

// fileDescriptor：包含 extend 声明的 FileDescriptor
val extensions = ProtobufExtensionRegistry.of(fileDescriptor)
val baseDescriptor = fileDescriptor.findMessageTypeByName("Base")
val mapper = ProtobufMapper(baseDescriptor, extensions)

val node = mapper.parse(bytes, Node::class.java)
val nickname = node.asObject()["my.package.nickname"]

val restored = mapper.formatBytes(
    com.mingliqiye.document.core.ObjectNode.of(
        "my.package.nickname" to com.mingliqiye.document.core.StringNode.of("小于"),
    ),
)

// 简单名命名：同一消息内不能有重名 extension
val simpleRegistry = ProtobufExtensionRegistry.builder()
    .addAll(fileDescriptor.extensions)
    .naming(ProtobufExtensionNaming.SIMPLE_NAME)
    .build()
```

### 10. group 读写

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapper
import com.mingliqiye.document.format.protobuf.ProtobufSchema
import com.mingliqiye.document.format.protobuf.ProtobufType

val schema = ProtobufSchema.builder("Container")
    .nestedMessage("Payload") {
        field("name", 1, ProtobufType.STRING, required = false)
    }
    .groupField("payload", 1, "Payload")
    .build()

val mapper = ProtobufMapper(schema)
val node = com.mingliqiye.document.core.ObjectNode.of(
    "payload" to com.mingliqiye.document.core.ObjectNode.of(
        "name" to com.mingliqiye.document.core.StringNode.of("小于"),
    ),
)

val bytes = mapper.formatBytes(node)
val restored = mapper.parse(bytes, Node::class.java)
check(restored.asObject()["payload"]?.asObject()?.get("name")?.asString() == "小于")
```

### 11. 文件 / 流

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

mapper.format(user, File("build/user.pb"))
val fromFile = mapper.parse(File("build/user.pb"), User::class.java)

val output = ByteArrayOutputStream()
mapper.format(user, output)
val fromStream = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 12. 深度限制

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapper
import com.mingliqiye.document.format.protobuf.ProtobufSchema
import com.mingliqiye.document.format.protobuf.ProtobufType
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException

val nestedSchema = ProtobufSchema.builder("Root")
    .nestedMessage("L1") {
        nestedMessage("L2") {
            field("value", 1, ProtobufType.INT32)
        }
        messageField("l2", 1, "L2", required = false)
    }
    .messageField("l1", 1, "L1", required = false)
    .build()

val descriptor = nestedSchema.toDescriptor()
val bytes = ProtobufMapper(descriptor).formatBytes(
    com.mingliqiye.document.core.ObjectNode.of(
        "l1" to com.mingliqiye.document.core.ObjectNode.of(
            "l2" to com.mingliqiye.document.core.ObjectNode.of(
                "value" to com.mingliqiye.document.core.NumberNode.of(1),
            ),
        ),
    ),
)

val guarded = ProtobufMapper(descriptor, ProtobufUnknownFieldPolicy.FAIL, maxDepth = 1)
val error = runCatching { guarded.parse(bytes, Node::class.java) }.exceptionOrNull()
check(error is ProtobufParseException)
```

### 13. 文本 API 不支持

```kotlin
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

val error = runCatching { mapper.parse("{}", User::class.java) }.exceptionOrNull()
check(error is DocumentSerializationException)
```

### 14. 错误：未知字段策略为 FAIL

```kotlin
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException

val bytes = mapper.formatBytes(user)
val strict = ProtobufMapper(schema.toDescriptor(), ProtobufUnknownFieldPolicy.FAIL)
// 若 bytes 含有当前 Descriptor 未声明的字段编号，这里会抛 ProtobufParseException
val result = runCatching { strict.parse(bytes, User::class.java) }
println(result.isFailure)
```

### 15. Java 示例

```java
import com.mingliqiye.document.format.protobuf.ProtobufMapper;
import com.mingliqiye.document.format.protobuf.ProtobufSchema;
import com.mingliqiye.document.format.protobuf.ProtobufType;

public class ProtobufDemo {
    public static class User {
        public long id;
        public String name;
    }

    public static void main(String[] args) {
        // Kotlin 默认参数在 Java 中不可用，需要传完整参数：
        // field(name, number, type, repeated, required, nullable, description, typeReference, oneofName)
        ProtobufSchema schema = ProtobufSchema.builder("User", "com.example")
                .field("id", 1, ProtobufType.INT64, false, true, false, null, null, null)
                .field("name", 2, ProtobufType.STRING, false, false, false, null, null, null)
                .build();

        ProtobufMapper mapper = new ProtobufMapper(schema);

        User user = new User();
        user.id = 7;
        user.name = "小于";

        byte[] bytes = mapper.formatBytes(user);
        User restored = mapper.parse(bytes, User.class);
        System.out.println(restored.id + ":" + restored.name);
    }
}
```

### 16. MessageSet 概念示例

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufExtensionRegistry
import com.mingliqiye.document.format.protobuf.ProtobufMapper
import com.mingliqiye.document.format.protobuf.ProtobufSchema

// messageSetDescriptor：带 option message_set_wire_format = true 的外部 Descriptor；
// fileDescriptor：声明了这些 extension 的 FileDescriptor。
// 注意：Schema 构建器可以声明 messageSetWireFormat，但 ProtobufSchema 不能声明
// extension 与 extension range，MessageSet 的 payload 类型必须由外部 Descriptor 提供。
val mapper = ProtobufMapper(messageSetDescriptor, ProtobufExtensionRegistry.of(fileDescriptor))

val node = mapper.parse(messageSetBytes, com.mingliqiye.document.core.Node::class.java)
// 已知 extension 以字段形式出现，例如 {"my.package.payload": {...}}
val restored = mapper.formatBytes(node)

// Schema 层只用于声明 wire format：
val declared = ProtobufSchema.builder("MessageSetRoot")
    .messageSetWireFormat()
    .build()
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 文本 API（`parse(String)` / `format`） | `DocumentSerializationException`（只支持二进制） |
| 未知字段策略 `FAIL` | 读取未知字段 / 写出未声明字段 → `ProtobufParseException` / `ProtobufException` |
| `PRESERVE` 与写回策略不一致 | `$unknownFields` 被当未声明字段拒绝 |
| map entry 内未知字段 | `IGNORE` / `PRESERVE` 下不保留；`FAIL` 报错 |
| oneof 多字段同时设置 | `ProtobufException` |
| 必填字段缺失 / null | 读取 `ProtobufParseException`；写出 `null` → `ProtobufException` |
| 数字越界 | `ProtobufException` |
| 字符串非法 UTF-8（未配对代理项） | `ProtobufException` |
| NaN / Infinity | `ProtobufParseException` |
| `maxDepth` 超限 | `ProtobufParseException` |
| extension 注册表与 Descriptor 实例不一致 | 该 extension 被当未知字段处理 |
| `ProtobufSchema.toDescriptor()` | 固定生成 proto2 语法；proto3 `optional`、map entry 等外部 Descriptor 只能通过 `fromDescriptor` 读取 |
| `ProtobufSchema` 声明 extension / extension range | 不支持（通过外部 `FileDescriptor` 注册） |
| MessageSet 未知 item | `PRESERVE` 之外无法保留；`PRESERVE` 只支持 length-delimited 结构 |
| 根 MessageSet 的畸形 payload | 解析前额外结构校验会抛 `ProtobufParseException` |
| 扩展字段 required | 无 required 语义（proto2 规范不允许 required extension） |
| 多 message 类型 | 用 `ProtobufMapperFactory` 或显式 Descriptor API 复用 Mapper |

## 与通用 `DocumentMapper` 的差异

- 继承 `BinaryDocumentMapper`：仅二进制 API，文本/字符流 API 抛 `DocumentSerializationException`；
- Schema 驱动：构造必须提供 `Descriptor` 或 `ProtobufSchema`；
- 额外能力：`ProtobufSchema` / `ProtobufSchemaBuilder` / `ProtobufMessage` / `ProtobufEnum`、`ProtobufExtensionRegistry`、group、MessageSet、未知字段策略、`ProtobufMapperFactory`；
- 显式 Descriptor 调用：`parseToNode` / `parse(source, descriptor, ...)` / `formatBytes(value, descriptor)`；
- Node 表示受 proto3 presence 影响：解码只输出实际存在或非默认字段；
- 没有格式专属 Config 类：未知字段策略、深度、extension 都是 Mapper 构造参数。

## 相关链接

- [格式总览](./)
- [Schema 模块](../modules/schema.md)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-protobuf 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-protobuf/README.md)
