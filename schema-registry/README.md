# schema-registry

基于 `schema` 模块的 Schema Registry 适配基础：本地内存 registry、版本/兼容性管理，
以及便于对接 Confluent Schema Registry 等远端服务的传输扩展点。

- Schema 以格式无关的 `DocumentSchema` 注册，内部编码为规范化 JSON Schema 节点；
- 版本按 subject 自增，id 全局分配，相同内容跨 subject 复用同一 id；
- 内置 `NONE` / `BACKWARD` / `FORWARD` / `FULL` 兼容性策略，也可注入自定义检查器；
- 不绑定具体远端实现：`SchemaRegistryClient` 负责传输，`RemoteDocumentSchemaRegistry`
  负责 `DocumentSchema` 编解码与转发。

## Maven

```kotlin
implementation("com.mingliqiye.document:schema-registry:2.0.0")
```

模块已包含在 BOM `com.mingliqiye.document:mingli-document-bom:2.0.0` 中。

## 主要 API

```text
SchemaSubject / SchemaId / SchemaVersion / SchemaFormat
RegisteredSchema
DocumentSchemaRegistry
InMemoryDocumentSchemaRegistry
SchemaCompatibilityChecker / SchemaCompatibilityPolicy / SchemaCompatibilityResult
DocumentSchemaCodec
SchemaRegistryClient
RemoteDocumentSchemaRegistry
```

## 快速开始

```kotlin
import com.mingliqiye.document.registry.InMemoryDocumentSchemaRegistry
import com.mingliqiye.document.registry.SchemaId
import com.mingliqiye.document.registry.SchemaSubject
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType

val registry = InMemoryDocumentSchemaRegistry() // 默认 SchemaCompatibilityPolicy.BACKWARD
val subject = SchemaSubject.of("user")

val v1 = registry.register(
    subject,
    DocumentSchemaBuilder("User")
        .field("id", SchemaType.LONG, description = "用户 id")
        .field("name", SchemaType.STRING)
        .build(),
    metadata = mapOf("owner" to "user-team"),
)
println("${v1.id.value} ${v1.version.value}") // 1 1

// 新增可选字段：BACKWARD 兼容，注册为版本 2
val v2 = registry.register(
    subject,
    DocumentSchemaBuilder("User")
        .field("id", SchemaType.LONG, description = "用户 id")
        .field("name", SchemaType.STRING)
        .field("email", SchemaType.STRING, required = false)
        .build(),
)

registry.latest(subject)      // v2
registry.byId(v1.id)          // v1
registry.versions(subject)    // [SchemaVersion(1), SchemaVersion(2)]
registry.latest(subject)?.documentSchema() // 反解回 DocumentSchema
```

## 语义

- **subject** 是版本演进单位：`versions(subject)` 从 `1` 开始连续自增；
- **id** 是全局 id：内容相同的 Schema 在不同 subject 下复用同一个 id
  （`byId` 返回最早注册该内容的记录）；
- **幂等注册**：与最新版本内容、元数据完全相同的重复注册直接返回已有记录，不产生新版本；
  仅元数据不同会生成新版本（复用同一 id）；
- **软删除**：`deleteSubject` 删除 subject 及其版本，但已分配的 id 不回收，`byId` 仍可查询；
- **未知查询**：`latest`/`byId` 返回 `null`，`versions` 返回空列表；
  对未知 subject 调用 `deleteSubject` 抛 `SchemaNotFoundException`。

## 规范化形式

本地 registry 把 `DocumentSchema` 编码为 JSON Schema（draft-07 子集）节点：

```json
{
  "title": "User",
  "type": "object",
  "properties": {
    "id": { "type": "integer", "format": "int64", "description": "用户 id" },
    "name": { "type": "string" },
    "tags": { "type": ["array", "null"] }
  },
  "required": ["id", "name"]
}
```

映射规则：

| SchemaType | 规范化节点 |
| --- | --- |
| `STRING` | `{"type": "string"}` |
| `INT` | `{"type": "integer", "format": "int32"}` |
| `LONG` | `{"type": "integer", "format": "int64"}` |
| `DOUBLE` | `{"type": "number", "format": "double"}` |
| `DECIMAL` | `{"type": "number"}` |
| `BOOLEAN` | `{"type": "boolean"}` |
| `BYTES` | `{"type": "string", "contentEncoding": "base64"}` |
| `ARRAY` | `{"type": "array"}`（通用类型不携带元素类型） |
| `OBJECT` | `{"type": "object"}` |
| `MAP` | `{"type": "object", "additionalProperties": {}}` |
| `NULL` | `{"type": "null"}` |
| `nullable = true` | `"type": [<基础类型>, "null"]`（`NULL` 本身可空，不加数组） |

`required = true` 的字段写入 `required` 数组；`SchemaField.description` 原样写入 `description`。
反解时忽略未知 JSON Schema 关键字（例如 `items`），无法映射的结构抛
`SchemaSerializationException`。

需要 JSON 文本时配合 `format-json`（本模块不依赖任何格式模块）：

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.registry.DocumentSchemaCodec

val mapper = JsonMapper()
val json = mapper.format(DocumentSchemaCodec.encode(schema))
val restored = DocumentSchemaCodec.decode(mapper.parse(json, Node::class.java))
```

## 兼容性策略

`InMemoryDocumentSchemaRegistry(compatibilityPolicy)` 接受任意 `SchemaCompatibilityChecker`，
内置枚举 `SchemaCompatibilityPolicy`：

| 变更 | NONE | BACKWARD | FORWARD | FULL |
| --- | --- | --- | --- | --- |
| 新增必填字段 | ✅ | ❌ | ✅ | ❌ |
| 新增可选字段 | ✅ | ✅ | ✅ | ✅ |
| 删除必填字段 | ✅ | ✅ | ❌ | ❌ |
| 删除可选字段 | ✅ | ✅ | ✅ | ✅ |
| 字段类型改变 | ✅ | ❌ | ❌ | ❌ |
| 可选 -> 必填 | ✅ | ❌ | ✅ | ❌ |
| 必填 -> 可选 | ✅ | ✅ | ❌ | ❌ |
| 非空 -> 可空 | ✅ | ✅ | ❌ | ❌ |
| 可空 -> 非空 | ✅ | ❌ | ✅ | ❌ |

`BACKWARD` = 新 Schema 能读取旧数据；`FORWARD` = 旧 Schema 能读取新数据；`FULL` 双向兼容。
目前没有数值类型提升（如 `INT` -> `LONG`），字段类型必须完全一致；需要更宽松的规则时
实现自定义检查器：

```kotlin
val registry = InMemoryDocumentSchemaRegistry { previous, candidate ->
    if (candidate.containsKey("x-forbidden")) {
        SchemaCompatibilityResult.incompatible("x-forbidden is not allowed")
    } else {
        SchemaCompatibilityResult.compatible()
    }
}
```

不兼容时注册抛 `SchemaCompatibilityException`，异常信息包含全部不兼容项。

## 远程扩展点

实现 `SchemaRegistryClient` 适配具体远端 registry（例如 Confluent REST：把 `Node` 写成
JSON 文本后调用 `POST /subjects/{subject}/versions`），再交给 `RemoteDocumentSchemaRegistry`：

```kotlin
class ConfluentSchemaRegistryClient(private val http: HttpClient) : SchemaRegistryClient {
    override fun register(
        subject: SchemaSubject,
        schema: Node,
        format: SchemaFormat,
        metadata: Map<String, String>,
    ): RegisteredSchema = TODO("POST /subjects/{subject}/versions")

    override fun latest(subject: SchemaSubject): RegisteredSchema? = TODO("GET /subjects/{subject}/versions/latest")
    override fun byId(id: SchemaId): RegisteredSchema? = TODO("GET /schemas/ids/{id}")
    override fun versions(subject: SchemaSubject): List<SchemaVersion> = TODO("GET /subjects/{subject}/versions")
    override fun deleteSubject(subject: SchemaSubject): List<SchemaVersion> = TODO("DELETE /subjects/{subject}")
}

val registry: DocumentSchemaRegistry = RemoteDocumentSchemaRegistry(ConfluentSchemaRegistryClient(httpClient))
```

- 适配器只负责把 `DocumentSchema` 编码为 JSON 节点（`SchemaFormat.JSON`）后转发；
- 远端返回的异构格式（`AVRO`/`PROTOBUF`）原样透传，`documentSchema()` 只能反解 JSON 负载；
- 兼容性由远端 registry 自身配置负责，适配器默认不做本地兼容性检查；
- 需要本地缓存/重试/预检时继承 `RemoteDocumentSchemaRegistry` 覆盖对应方法。

## 异常

均继承自 `SchemaRegistryException`（`SchemaRegistryException : DocumentException`）：

| 异常 | 触发场景 |
| --- | --- |
| `SchemaRegistryException` | 公共基类；subject/id/version 空白或非正数、未知 `SchemaFormat` 等 |
| `SchemaSerializationException` | `DocumentSchema` 编解码失败：Schema 名/字段名空白、字段重复、节点结构非法、字段类型无法映射 |
| `SchemaCompatibilityException` | 新版本与 subject 最新版本不兼容 |
| `SchemaNotFoundException` | 删除不存在的 subject |

## 线程安全

`InMemoryDocumentSchemaRegistry` 的版本分配、id 分配与兼容性检查在同一把锁内完成，
并发注册不会产生重复版本号或 id；`RegisteredSchema`、`metadata` 与查询结果均为不可变快照。
并发测试见 `InMemoryDocumentSchemaRegistryConcurrencyTest`。

## 构建

```bash
JAVA_HOME="C:\Program Files\Zulu\zulu-17" gradle :schema-registry:ktlintFormat :schema-registry:test --console=plain --no-daemon
```

## 限制

1. 只实现内存 registry；持久化与真正的远端客户端需自行实现 `SchemaRegistryClient`。
2. 兼容性比较基于 `DocumentSchema` 字段模型：不检查 `title`（Schema 名）变化，
   不做数值提升，`ARRAY`/`MAP`/`OBJECT` 不携带嵌套结构，因此不递归比较元素/值类型。
3. 规范化节点是 JSON Schema 子集：反解会忽略 `items`、`pattern`、`enum` 等未知关键字；
   远端异构格式（`AVRO`/`PROTOBUF`）只透传、不反解。
4. `SchemaType.NULL` 字段的 `nullable` 标记在往返后归一为 `false`（`NULL` 本身已可空）。
5. id 全局复用按“规范化节点内容”判断：不同 subject 注册相同内容共享 id，
   `byId` 返回最早注册的记录（Confluent 的 `GET /schemas/ids/{id}` 语义近似）。
6. `deleteSubject` 为软删除：不回收 id，`byId` 仍可查询被删除版本。
