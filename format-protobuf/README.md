# format-protobuf

Protocol Buffers 格式模块。

基于 `Descriptor` / `DynamicMessage`。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:protobuf:2.5.0")
```

## 主要 API

```text
ProtobufMapper
ProtobufSchema
ProtobufSchemaBuilder
ProtobufSchemaField
ProtobufType
ProtobufTypeReference
ProtobufUnknownFieldPolicy
ProtobufExtensionRegistry
ProtobufExtensionNaming
ProtobufUnknownFields
```

## 示例

```kotlin
import com.mingliqiye.document.format.protobuf.ProtobufMapper

val mapper = ProtobufMapper(descriptor)
val bytes = mapper.formatBytes(value)
val restored = mapper.parse(bytes, Node::class.java)
```

## Schema -> Descriptor

```kotlin
val descriptor = protobufSchema.toDescriptor()
val mapper = ProtobufMapper(descriptor)
```

## extension

注册扩展字段后，extension 会以对象字段形式读写；默认字段名为 extension 全名：

```kotlin
val extensions = ProtobufExtensionRegistry.of(fileDescriptor) // 含嵌套作用域 extension
val mapper = ProtobufMapper(descriptor, extensions)

val node = mapper.parse(bytes, Node::class.java)
val decoded = node["my.package.nickname"]

val restored = mapper.formatBytes(
    ObjectNode.of("my.package.nickname" to StringNode.of("小于")),
)
```

字段命名方式可选：

```kotlin
val extensions = ProtobufExtensionRegistry.builder()
    .addAll(fileDescriptor.extensions)
    .naming(ProtobufExtensionNaming.SIMPLE_NAME) // 或 FULL_NAME（默认）、BRACKETED
    .build()
```

未注册的 extension 与普通未知字段一样由 `ProtobufUnknownFieldPolicy` 处理。

## group

proto2 group 使用 start-group/end-group wire 标签，读写与 message 类似：

```kotlin
val schema = ProtobufSchema.builder("Container")
    .nestedMessage("Payload") {
        field("name", 1, ProtobufType.STRING, required = false)
    }
    .groupField("payload", 1, "Payload")
    .build()
```

也可以从外部 `Descriptor` 的 `TYPE_GROUP` 字段直接读写（`ProtobufType.GROUP`）。

## MessageSet

`option message_set_wire_format = true` 的 message 自动按 MessageSet wire format 读写；内容只能
通过 extension 表达：

```kotlin
val mapper = ProtobufMapper(messageSetDescriptor, ProtobufExtensionRegistry.of(fileDescriptor))
val node = mapper.parse(bytes, Node::class.java) // {"my.package.extension": {...}}
val restored = mapper.formatBytes(node)          // start group + type_id + message + end group
```

未知 MessageSet item 由未知字段策略处理；`PRESERVE` 时解析为 `$unknownFields` 并可按原 wire 写回。

## 未知字段

```kotlin
ProtobufUnknownFieldPolicy.FAIL     // 默认：遇到未知字段抛 ProtobufParseException
ProtobufUnknownFieldPolicy.IGNORE   // 丢弃未知字段
ProtobufUnknownFieldPolicy.PRESERVE // 保留为 "$unknownFields" 字段并支持写回
```

`PRESERVE` 的结构（varint/fixed32/fixed64/lengthDelimited/groups）见 `ProtobufUnknownFields`
文档；写出时必须使用同一策略，否则对象中的 `$unknownFields` 会被当作未声明字段拒绝。

## proto3 语义

- 无 `optional` 的 singular 标量字段：默认值不写 wire（与官方编码一致）；解析时默认值不产生字段。
- `optional`（含 proto3 `optional` 与 proto2 optional/required）：按 presence 处理，显式写默认值会
  保留 presence；`null` 表示未设置。
- `string` 写出前校验可编码为合法 UTF-8（拒绝未配对 UTF-16 代理项）；proto3 `string` 解析时由
  protobuf-java 严格校验 UTF-8，proto2 非法 UTF-8 由 protobuf-java 以替换字符解码。
- `bytes` 接受 `BinaryNode` 或 Base64 字符串；`enum` 接受名称或数字（未知数字保留为数字）。
- 可 packed 的 repeated 标量在 proto3 中按 packed 读写。

## 支持

- 标量类型
- string / bytes
- enum
- repeated
- map
- 嵌套 message
- oneof
- proto2 group
- extension（含嵌套作用域 / repeated / message 类型）
- MessageSet wire format
- 未知字段策略 `FAIL` / `IGNORE` / `PRESERVE`
- 解析深度限制
- 文件/流读写

## 解析深度

```kotlin
val mapper = ProtobufMapper(
    descriptor,
    ProtobufUnknownFieldPolicy.FAIL,
    maxDepth = 64,
)
```

超过限制抛出 `ProtobufParseException`。

## 限制

- Schema（`ProtobufSchema`）不能声明 extension 与 extension range；extension 通过外部
  `FileDescriptor` / `FieldDescriptor` 注册，MessageSet 的 payload 类型同样由外部 Descriptor 提供。
  extension 注册表必须与 mapper 使用同一个 Descriptor 实例（Protobuf 按实例匹配 extension），
  用重建的 Descriptor 注册会按未知字段处理。
- `ProtobufSchema.toDescriptor()` 生成的文件描述符固定 proto2 语法；proto3 `optional`、map entry
  等外部 Descriptor 只能通过 `ProtobufSchema.fromDescriptor` 读取。
- MessageSet 未知 item 无法在 `PRESERVE` 之外保留；`PRESERVE` 结构只支持 length-delimited 的
  MessageSet 未知字段。
- MessageSet 的已知 extension payload 采用惰性解析，本模块在根消息解析前做了额外结构校验，
  因此根 MessageSet 的畸形 payload 会报 `ProtobufParseException`；嵌套在其他消息字段里的
  MessageSet 不做该预校验（仍由 protobuf-java 惰性解析）。
- map entry 内部的未知字段在 `IGNORE`/`PRESERVE` 下不保留（map 值不是对象节点）；`FAIL` 仍会报错。
- `$unknownFields` 中的数值按无符号十进制表达，重写时依赖 wire 类型键；不是普通业务字段。
- Node 数字模型不支持 NaN / Infinity；proto3 `optional` 等 presence 语义不改变 Node 表示。
- 扩展字段没有 required 语义（proto2 规范不允许 required extension）。

## 构建

```bash
gradle :format-protobuf:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
'

## 复用 Mapper

不同 Java 类只要对应同一个 Protobuf message，就可以共享同一个 Mapper；
不同 message 类型使用不同 Descriptor，通过 `ProtobufMapperFactory` 缓存：

```kotlin
val factory = ProtobufMapperFactory(
    unknownFieldPolicy = ProtobufUnknownFieldPolicy.IGNORE,
)
val userMapper = factory.mapper(userDescriptor)
val orderMapper = factory.mapper(orderDescriptor)
```

Mapper 是线程安全的读对象，建议按 Descriptor 复用，而不是每次调用都新建。
Descriptor / FileDescriptor / extension 注册表也应只构建一次。

也可以只创建一个 Mapper，在 parse/format 时传入本次使用的 Descriptor：

```kotlin
val mapper = ProtobufMapper(defaultDescriptor)
val bytes = mapper.formatBytes(value, currentDescriptor)
val node = mapper.parseToNode(bytes, currentDescriptor)
val bean = mapper.parse(bytes, currentDescriptor, User::class.java)
```

'