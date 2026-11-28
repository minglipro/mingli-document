# format-avro

Apache Avro 格式模块。

提供 Schema、wire 编解码、OCF 容器与 schema evolution。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:avro:2.1.0")
```

## 主要 API

```text
AvroMapper
AvroMapperBuilder
AvroSchema
AvroSchemaBuilder
AvroContainerReader
AvroContainerWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper

val mapper = AvroMapper(avroSchema)
val bytes = mapper.formatBytes(value)
val restored = mapper.parse(bytes, Node::class.java)
```

## Schema evolution

```kotlin
val mapper = AvroMapper(writerSchema, readerSchema)
val restored = mapper.parse(bytes, User::class.java)
```

## OCF

```kotlin
val ocf = mapper.formatContainerBytes(records, AvroCodec.DEFLATE)
val appended = mapper.appendContainerBytes(ocf, moreRecords)
val nodes = mapper.parseContainerToNodes(appended)
```

支持 codec：

```text
NULL
DEFLATE
SNAPPY
```

文件追加：

```kotlin
mapper.appendContainer(file, records)
```

## 特性

- Avro Schema <-> `org.apache.avro.Schema`
- record / array / map / enum / fixed / union / bytes
- Object Container File
- OCF codec：null / deflate / snappy
- appendTo 文件追加
- writer/reader 双 Schema 演进
- 解析深度限制
- 文件/流读写

## 限制

- 裸二进制需要调用方提供 Schema
- 追加不能改变容器 codec/schema
- 不支持 bzip2 / xz / zstandard
- 非有限浮点拒绝

## 构建

```bash
gradle :format-avro:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
