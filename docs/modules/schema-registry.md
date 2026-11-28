# Schema Registry

## 坐标

```kotlin
implementation("com.mingliqiye.document:schema-registry:2.3.0")
```

## 能力

```text
Schema 注册 / 版本管理
全局 id 与版本号
兼容性策略
DocumentSchema <-> JSON Schema 节点
远程 Registry SPI
```

## 快速开始

```kotlin
val registry = InMemoryDocumentSchemaRegistry()

val registered = registry.register(
    SchemaSubject.of("user-value"),
    schema,
)

val latest = registry.latest(SchemaSubject.of("user-value"))
val byId = registry.byId(registered.id)
```

## 兼容性策略

```kotlin
InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.BACKWARD)
```

```text
NONE      不检查
BACKWARD  允许删字段、加可选字段
FORWARD   允许加字段、删可选字段
FULL      双向兼容
```

## 远程 SPI

```kotlin
public interface SchemaRegistryClient {
    fun register(subject: String, schema: Node): RegisteredSchema
    fun latest(subject: String): RegisteredSchema?
    fun byId(id: Long): RegisteredSchema?
    fun versions(subject: String): List<RegisteredSchema>
    fun deleteSubject(subject: String)
}

val remote = RemoteDocumentSchemaRegistry(client)
```

`RemoteDocumentSchemaRegistry` 负责把 `DocumentSchema` 编码为 JSON Schema 节点后转发，
方便接入 Confluent Schema Registry 等远程实现。

## 异常

```text
SchemaRegistryException
SchemaCompatibilityException
SchemaSerializationException
SchemaNotFoundException
```

## 限制

```text
只提供内存实现
兼容性比较基于 DocumentSchema 字段级模型
数组/Map/OBJECT 嵌套兼容性暂不递归
AVRO/PROTOBUF 负载按 Node 透传
```
