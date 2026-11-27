# format-protobuf

Protocol Buffers 格式模块。

基于 `Descriptor` / `DynamicMessage`。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:protobuf:1.2.0")
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

## 支持

- 标量类型
- string / bytes
- enum
- repeated
- map
- 嵌套 message
- oneof
- 未知字段策略 `FAIL` / `IGNORE`
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

- 不支持 extensions / group / MessageSet
- 生成描述符固定 proto2 语法
- Node 数字模型不支持 NaN / Infinity

## 构建

```bash
gradle :format-protobuf:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
