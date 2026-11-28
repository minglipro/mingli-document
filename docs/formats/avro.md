---
title: Avro
description: format-avro 模块：AvroMapper、Schema、wire 编解码、OCF 容器、codec、schema evolution 与 append
---

# Avro

`format-avro` 提供 Apache Avro 的 Schema、wire 编解码与 Object Container File（OCF）支持，核心类型是 `AvroMapper`。

Avro 是 Schema 驱动的二进制格式：裸二进制数据不自带 Schema，读取必须提供 writer Schema；容器文件（OCF）在头部内嵌 Schema。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:avro:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `avro` |
| 版本 | `2.5.1` |
| 传递依赖 | `schema`、`core`、`exception`、`serialization` |
| 第三方依赖 | `org.apache.avro:avro:1.11.3`（api）、`org.xerial.snappy:snappy-java:1.1.10.5`（实现） |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.avro` |

## 特性

- **Avro Schema ↔ `org.apache.avro.Schema`**：`AvroSchema` 视图与 Apache Avro Schema 互相转换；
- **完整类型支持**：record / array / map / enum / fixed / union / bytes；
- **裸 wire 编解码**：`formatBytes` / `parse(ByteArray)` 读写单根 datum；
- **Object Container File（OCF）**：`formatContainerBytes` / `parseContainer*` 读写带 Schema 头的多记录容器；
- **OCF codec**：`NULL` / `DEFLATE` / `SNAPPY`；
- **追加**：`appendContainer` / `appendContainerBytes` 向已有容器追加记录，保留已有记录与 sync 标记；
- **Schema 演进**：writer / reader 双 Schema，支持新增带默认值字段、删除字段与类型提升；
- **解析深度限制**：`maxNodeDepth`；
- **文件/流读写**：二进制语义，`format(value, File/Path/OutputStream)` 走裸 wire。

## Schema：`AvroSchema`

`AvroSchema` 实现通用 `DocumentSchema`，只描述记录结构与字段类型映射，不依赖 Apache Avro 库。

| 成员 | 说明 |
|---|---|
| `name` | Avro 记录名（Avro 规范要求必填） |
| `namespace` | Avro 命名空间；`null` 表示不带命名空间 |
| `fullName` | `namespace.name`；无命名空间时等于 `name` |
| `fields: List<SchemaField>` | 字段列表，保持声明顺序 |
| `field(name)` | 按字段名查找 |
| `avroType(field)` / `avroType(name)` | 字段对应的 `AvroType` |
| `avroTypeName(name)` | 字段对应的 Avro 类型名（如 `string`） |
| `toJson()` | 生成轻量 Avro Schema JSON 文本 |
| `toAvroSchema()` | 转换为 `org.apache.avro.Schema` |
| `AvroSchema.builder(name)` | 构建器 |
| `AvroSchema.of(name, vararg fields)` / `of(name, namespace, vararg fields)` | 直接创建 |
| `AvroSchema.fromAvroSchema(schema)` | 从 Apache Avro Schema 还原视图 |

`AvroSchemaBuilder`：

| 方法 | 说明 |
|---|---|
| `namespace(String?)` | 设置命名空间 |
| `field(name, type: SchemaType, required = true, nullable = false, description = null)` | 通用 Schema 类型字段 |
| `field(name, avroType: AvroType, required = true, nullable = false, description = null)` | Avro 类型字段 |
| `build()` | 构建（记录名/命名空间/字段名按 Avro 规则校验，字段名不允许重复） |

`AvroType` 与 `SchemaType` 映射：

| `AvroType` | `typeName` | `schemaType` |
|---|---|---|
| `STRING` | `string` | `SchemaType.STRING` |
| `INT` | `int` | `SchemaType.INT` |
| `LONG` | `long` | `SchemaType.LONG` |
| `DOUBLE` | `double` | `SchemaType.DOUBLE` |
| `BOOLEAN` | `boolean` | `SchemaType.BOOLEAN` |
| `BYTES` | `bytes` | `SchemaType.BYTES` |
| `ARRAY` | `array` | `SchemaType.ARRAY` |
| `OBJECT` | `object` | `SchemaType.OBJECT` |
| `MAP` | `map` | `SchemaType.MAP` |
| `NULL` | `null` | `SchemaType.NULL` |

`SchemaType.DECIMAL` 没有独立 Avro 类型名，按 Avro 规范基于 `bytes`（decimal 逻辑类型）表达，映射为 `BYTES`。

`toAvroSchema()` 的复合字段约定（通用 `SchemaType` 不携带嵌套结构信息）：

- `ARRAY` → `array<string>`；
- `MAP` → `map<string>`；
- `OBJECT` → 以 `<记录名>_<字段名>` 命名的空嵌套 record；
- `DECIMAL` → 不含 precision/scale 的 `bytes`；
- 可空或非必填字段写成 `["null", "<type>"]` 并带 `"default": null`；
- 需要完整嵌套类型（自定义子 record、enum、fixed、非 string 元素）时，直接使用 Apache Avro Schema 构造 `AvroMapper`。

`AvroSchema.fromAvroSchema` 会把无法表达的类型归一化：

- `float` → `SchemaType.DOUBLE`；
- `enum` → `SchemaType.STRING`；
- `fixed` → `SchemaType.BYTES`（decimal 逻辑类型 → `SchemaType.DECIMAL`）；
- 含多个分支的 union 只保留第一个非 null 分支；
- 嵌套 record → `SchemaType.OBJECT`。

```kotlin
import com.mingliqiye.document.format.avro.AvroSchema
import com.mingliqiye.document.schema.SchemaType
import org.apache.avro.Schema

val view = AvroSchema.builder("User")
    .namespace("com.example")
    .field("id", SchemaType.LONG)
    .field("name", SchemaType.STRING)
    .field("nick", SchemaType.STRING, required = false, nullable = true)
    .build()

println(view.fullName)          // com.example.User
println(view.toJson())
val schema: Schema = view.toAvroSchema()
```

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `AvroMapper(schema: Schema)` | writer = reader = 给定 record Schema |
| `AvroMapper(schema: Schema, config: DocumentMapperConfig)` | 同上，带通用配置 |
| `AvroMapper(schema: AvroSchema)` / `AvroMapper(schema: AvroSchema, config)` | 内部转为 Apache Avro Schema |
| `AvroMapper(writerSchema: Schema, readerSchema: Schema)` / `+ config` | Schema 演进：写出用 writer，读取用 writer/reader 双 Schema |
| `AvroMapper(writerSchema: AvroSchema, readerSchema: AvroSchema)` | `AvroSchema` 版本 |

- 两个 Schema 都必须是 `RECORD`，否则构造抛 `IllegalArgumentException`；
- 属性：`writerSchema`、`readerSchema`、`schema`（等同 `writerSchema` 的别名）；
- 常量：`AvroMapper.FORMAT_NAME = "avro"`。

Builder：

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper

val mapper = AvroMapper.builder(schema).build()                                  // writer = reader
val evolving = AvroMapper.builder(writerSchema, readerSchema).build()            // 双 Schema
```

`AvroMapperBuilder` 继承 `DocumentMapperBuilder<AvroMapper>`，支持 `addSerializer` / `addDeserializer` / `addConverter` / `addModule` / `addModules` / `config` / `objectInstantiator` / `build`。

## 读取 API

| 方法 | 说明 |
|---|---|
| `parse(source: ByteArray, targetType)` | 裸单根 Avro binary datum；使用 writer Schema 读取，按 reader Schema 演进 |
| `parse(file: File, targetType)` / `parse(path: Path, targetType)` | 二进制文件 → 实体 |
| `parse(input: InputStream, targetType)` | 二进制流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |
| `parseContainerToNodes(source: ByteArray): List<Node>` | 读取 OCF 全部记录 |
| `parseContainer(source, targetType: Class<T>): List<T>` | 读取 OCF 并映射为实体列表 |
| `parseContainer(source, targetType: DocumentTypeReference<T>): List<T>` | 泛型版本 |

不支持的文本入口（抛 `DocumentSerializationException`）：`parse(String)`、`parse(reader: Reader)`、`format`、`formatPretty`、`format(value, writer)`。

读取规则：

- 裸二进制不自带 Schema，必须由调用方提供 writer Schema（构造 Mapper 时）；
- 读取结束要求 `decoder.isEnd()`，存在尾随字节时抛 `AvroParseException`；
- 数据损坏、字节截断、Schema 不匹配或无法演进统一抛 `AvroParseException`；
- Avro `float` / `double` 的 NaN、Infinity 无法由 `NumberNode` 表达，抛 `AvroParseException`；
- `maxNodeDepth` 在 datum → Node 转换时检查嵌套深度（record / map / array）；
- OCF 读取时 writer Schema 取自容器头部，reader Schema 使用 Mapper 的 `readerSchema`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `formatBytes(value)` | 裸单根 Avro binary datum（writer Schema） |
| `format(value, file: File / path: Path)` | 写裸 datum 到文件 |
| `format(value, output: OutputStream)` | 写裸 datum 到流 |
| `formatContainerBytes(values: Iterable<Any?>)` | 写 OCF（codec = `NULL`） |
| `formatContainerBytes(values, codec: AvroCodec)` | 写 OCF，指定 codec |
| `appendContainer(file: File, values, codec: AvroCodec? = null)` | 向 OCF 文件追加记录；文件不存在/为空时新建 |
| `appendContainer(path: Path, values, codec: AvroCodec? = null)` | 同上 |
| `appendContainerBytes(existing: ByteArray, values, codec: AvroCodec? = null)` | 向已有 OCF 字节追加，返回完整字节 |

不支持：`format(value): String`、`formatPretty`、`format(value, Writer)`。

写出规则：

- 记录字段按 Schema 顺序写出；
- `Node` 缺少字段时优先使用 Schema 默认值；可空 union 字段写 `null`；其余情况抛 `AvroException`；
- 记录中出现 Schema 未声明的字段抛 `AvroException`；
- `record` 的 `int` / `long` 数字超出范围抛 `AvroException`；
- `fixed` 字节长度必须等于 `fixedSize`，否则抛 `AvroException`；
- OCF 写出时只有 record 可以作为记录值，非 record datum 抛 `AvroException`。

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `formatBytes` | ❌ | 先写满 `ByteArrayOutputStream` 再返回 |
| `format(value, OutputStream)` | ❌ | 基类先得到 `ByteArray` 再写出 |
| `format(value, File/Path)` | ❌ | 同上 |
| `formatContainerBytes` / `appendContainer` | ❌ | 一次性写出容器字节 |

## Wire 编码（裸 datum）

Avro 二进制采用 schema 驱动的紧凑编码：

- 每个顶层 API 写**一个** datum（单根 record）；
- 编码中不含字段名、不含类型标记、不含 Schema；
- 读取时必须给出 writer Schema 才能解析；
- 提供 reader Schema 时按 Avro Schema 解析规则做演进；
- 读取完成后要求没有尾随字节。

## OCF 容器

Object Container File 结构：

```text
magic（"Obj\u0001"）
metadata（内嵌 writer Schema、avro.codec 等）
sync marker
data block（若干记录）
sync marker
...
```

API 行为：

- `formatContainerBytes(values, codec)`：头部内嵌 `writerSchema` 与 codec，记录顺序与入参一致；空集合会写出只含头部的合法容器；
- `parseContainer*`：`DataFileReader` 依据头部自动识别 codec 并解压，记录按容器顺序返回；
- `appendContainer(file, values, codec?)`：
  - 文件不存在或为空 → 新建容器（codec 缺省 `NULL`）；
  - 已存在容器 → 校验头部 Schema 与 codec；codec 传 `null` 时沿用容器原 codec，显式给出时不一致抛 `AvroException`；追加保留已有记录与 sync 标记；
- `appendContainerBytes(existing, values, codec?)`：输出以原容器字节开头，新记录使用原 sync 标记写入后续数据块；`existing` 为空时按新建容器处理。

### Codec：`AvroCodec`

| 枚举值 | `codecName` | 说明 |
|---|---|---|
| `AvroCodec.NULL` | `null` | 不压缩 |
| `AvroCodec.DEFLATE` | `deflate` | Java deflate |
| `AvroCodec.SNAPPY` | `snappy` | Google Snappy（依赖 snappy-java） |

- `AvroCodec.fromCodecName(name)`：按名称查找，未知返回 `null`；
- 读取时遇到不支持的 codec（如 bzip2、xz、zstandard）抛 `AvroParseException("Unsupported Avro OCF codec: ...")`；
- 追加不能改变容器原有 codec 与 Schema。

## Schema 演进

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper

val mapper = AvroMapper(writerSchema, readerSchema)
val restored = mapper.parse(bytes, User::class.java)
```

支持：

- 新增带默认值的字段；
- 删除字段；
- 类型提升（如 `int` → `long` / `double`）。

不兼容的演进抛 `AvroParseException`。OCF 读取时 writer Schema 来自容器头部，reader Schema 来自 Mapper。

## 类型映射

Node ↔ Avro datum：

| Node | Avro 读取 | Avro 写出 |
|---|---|---|
| `ObjectNode` | record（按 record 自身 Schema 字段顺序） | record（按 Schema 字段顺序） |
| `ArrayNode` | array | array |
| `ObjectNode`（字符串键） | map | map |
| `StringNode` | `string`（`Utf8`/`CharSequence` → 字符串）、`enum`（读成字符串） | `string` / `enum`（按 Schema symbol 校验） |
| `NumberNode` | `int` / `long` → 整数；`float` / `double` → 浮点（有限值）；`BigDecimal` / `BigInteger` 保留 | `int` / `long`（范围校验）、`float` / `double` |
| `BooleanNode` | `boolean` | `boolean` |
| `NullNode` | `null` | `null`；可空 union 支持 |
| `BinaryNode` | `bytes`（`ByteBuffer` 剩余字节）、`fixed`（字节数组） | `bytes` / `fixed`（fixed 校验长度） |

补充：

- Avro `float` 的 Node 表示会经过 `NumberNode`（BigDecimal）转换，写入时再转回 `Float`；
- `enum` 字段写出接受字符串 symbol（必须是 Schema 声明的 symbol）；
- union：Node 值按 Schema 分支匹配，`NullNode` 匹配 `null` 分支；
- record 中未声明字段报错；缺字段优先使用 Schema 默认值。

## 示例

### 1. 用 Apache Avro Schema 创建 Mapper

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper
import org.apache.avro.Schema

val schema = Schema.Parser().parse(
    """
    {
      "type": "record",
      "name": "User",
      "namespace": "com.example",
      "fields": [
        {"name": "id", "type": "long"},
        {"name": "name", "type": "string"},
        {"name": "nick", "type": ["null", "string"], "default": null}
      ]
    }
    """.trimIndent(),
)

val mapper = AvroMapper(schema)
```

### 2. Bean ↔ 裸字节

```kotlin
class User {
    var id: Long = 0
    var name: String = ""
    var nick: String? = null
}

val user = User().apply {
    id = 42
    name = "小于"
}

val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
check(restored.name == "小于")
```

### 3. Node → 裸字节 → Node

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.toNode(user)
val roundTrip = mapper.parse(mapper.formatBytes(node), Node::class.java)
check(roundTrip == node)
```

### 4. 直接写 Map

```kotlin
val bytes = mapper.formatBytes(
    mapOf(
        "id" to 7L,
        "name" to "阿明",
        "nick" to null,
    ),
)
val node = mapper.parse(bytes, Node::class.java)
println(node.asObject()["name"]?.asString())
```

### 5. 用 AvroSchema 构建 Schema

```kotlin
import com.mingliqiye.document.format.avro.AvroSchema
import com.mingliqiye.document.format.avro.AvroMapper
import com.mingliqiye.document.schema.SchemaType

val view = AvroSchema.builder("User")
    .namespace("com.example")
    .field("id", SchemaType.LONG)
    .field("name", SchemaType.STRING)
    .field("nick", SchemaType.STRING, required = false, nullable = true)
    .build()

val mapper = AvroMapper(view)
val bytes = mapper.formatBytes(user)   // user 的 id/name/nick 与 view 字段一一对应
println(mapper.parse(bytes, Node::class.java))
```

### 6. OCF 读写（指定 codec）

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.AvroCodec

val users = listOf(
    User().apply { id = 1; name = "a" },
    User().apply { id = 2; name = "b" },
)

val ocf = mapper.formatContainerBytes(users, AvroCodec.DEFLATE)
val nodes: List<Node> = mapper.parseContainerToNodes(ocf)
val beans: List<User> = mapper.parseContainer(ocf, User::class.java)
check(beans.size == 2)
```

### 7. 空 OCF

```kotlin
val empty = mapper.formatContainerBytes(emptyList())
check(mapper.parseContainerToNodes(empty).isEmpty())
```

### 8. 向文件追加记录

```kotlin
import com.mingliqiye.document.format.avro.AvroCodec
import java.io.File

val file = File("build/users.avro")
if (file.exists()) file.delete()

mapper.appendContainer(file, listOf(User().apply { id = 1; name = "a" }), AvroCodec.NULL)
mapper.appendContainer(file, listOf(User().apply { id = 2; name = "b" }))   // 沿用容器 codec

val nodes = mapper.parseContainerToNodes(file.readBytes())
check(nodes.size == 2)
```

### 9. 向字节追加记录

```kotlin
val first = mapper.formatContainerBytes(listOf(User().apply { id = 1; name = "a" }))
val appended = mapper.appendContainerBytes(first, listOf(User().apply { id = 2; name = "b" }))
check(mapper.parseContainerToNodes(appended).size == 2)
```

### 10. Schema 演进

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper
import org.apache.avro.Schema

val writerSchema = Schema.Parser().parse(
    """{"type":"record","name":"User","fields":[{"name":"id","type":"long"}]}""",
)

val readerSchema = Schema.Parser().parse(
    """
    {"type":"record","name":"User","fields":[
      {"name":"id","type":"long"},
      {"name":"name","type":"string","default":""}
    ]}
    """.trimIndent(),
)

val writerMapper = AvroMapper(writerSchema)
val bytes = writerMapper.formatBytes(mapOf("id" to 7L))

val readerMapper = AvroMapper(writerSchema, readerSchema)
val node = readerMapper.parse(bytes, Node::class.java)
println(node.asObject()["name"]?.asString())   // 默认值 "" 
```

### 11. enum / fixed / union

```kotlin
import org.apache.avro.Schema
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

val eventSchema = Schema.Parser().parse(
    """
    {
      "type": "record",
      "name": "Event",
      "fields": [
        {"name": "status", "type": {"type": "enum", "name": "Status", "symbols": ["NEW", "DONE"]}},
        {"name": "hash", "type": {"type": "fixed", "name": "Hash", "size": 4}},
        {"name": "payload", "type": ["null", "bytes"], "default": null}
      ]
    }
    """.trimIndent(),
)

val eventMapper = AvroMapper(eventSchema)
val event = ObjectNode.of(
    "status" to StringNode.of("DONE"),
    "hash" to BinaryNode.of(byteArrayOf(1, 2, 3, 4)),
    "payload" to NullNode.INSTANCE,
)

val eventBytes = eventMapper.formatBytes(event)
val restored = eventMapper.parse(eventBytes, Node::class.java)
check(restored.asObject()["status"]?.asString() == "DONE")
```

### 12. 文件 / 流（裸 datum）

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

mapper.format(user, File("build/user.avro"))
val fromFile = mapper.parse(File("build/user.avro"), User::class.java)

val output = ByteArrayOutputStream()
mapper.format(user, output)
val fromStream = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 13. 深度限制

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.format.avro.exception.AvroParseException

val nestedSchema = Schema.Parser().parse(
    """
    {"type":"record","name":"Node","fields":[
      {"name":"child","type":["null",{"type":"record","name":"Node2","fields":[
        {"name":"child","type":["null",{"type":"record","name":"Node3","fields":[
          {"name":"value","type":"int"}
        ]}],"default":null}
      ]}],"default":null}
    ]}
    """.trimIndent(),
)

val mapper = AvroMapper(nestedSchema)
val bytes = mapper.formatBytes(mapOf("child" to mapOf("child" to mapOf("value" to 1))))

val guarded = AvroMapper(nestedSchema, DocumentMapperConfig.builder().maxNodeDepth(1).build())
val error = runCatching { guarded.parse(bytes, Node::class.java) }.exceptionOrNull()
check(error is AvroParseException)
```

### 14. Logical type

支持 Apache Avro 常用 logical type：

| logicalType | 读取 Node | 写出 |
|---|---|---|
| `date` | `2024-01-02` | ISO 字符串 / epoch days |
| `time-millis` / `time-micros` | ISO 时间 | ISO / epoch |
| `timestamp-millis` / `timestamp-micros` | UTC ISO 时间戳 | ISO / epoch |
| `local-timestamp-*` | 无时区 ISO | ISO / epoch |
| `decimal` | 保留 scale 的 NumberNode | 数字 / 十进制字符串 |
| `uuid` | UUID 字符串 | UUID 字符串 |
| `duration` | `{months,days,millis}` | 对象 / 12 字节 BinaryNode |

示例：

```kotlin
val schema = Schema.Parser().parse(
    """
    {"type":"record","name":"Event","fields":[
      {"name":"at","type":{"type":"long","logicalType":"timestamp-millis"}},
      {"name":"id","type":{"type":"string","logicalType":"uuid"}}
    ]}
    """.trimIndent(),
)

val mapper = AvroMapper(schema)
val bytes = mapper.formatBytes(mapOf("at" to "2024-01-02T03:04:05.123Z", "id" to "550e8400-e29b-41d4-a716-446655440000"))
val node = mapper.parse(bytes)
```

未知 logicalType 回退底层 Avro 类型；已知 logicalType 但 schema 结构非法会抛
`AvroParseException` / `AvroException`。

### 15. Java 示例

```java
import com.mingliqiye.document.format.avro.AvroMapper;
import org.apache.avro.Schema;

public class AvroDemo {
    public static class User {
        public long id;
        public String name;
    }

    public static void main(String[] args) {
        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"User\",\"fields\":["
                        + "{\"name\":\"id\",\"type\":\"long\"},"
                        + "{\"name\":\"name\",\"type\":\"string\"}]}");

        AvroMapper mapper = new AvroMapper(schema);

        User user = new User();
        user.id = 7;
        user.name = "小于";

        byte[] bytes = mapper.formatBytes(user);
        User restored = mapper.parse(bytes, User.class);
        System.out.println(restored.id + ":" + restored.name);
    }
}
```

### 15. 文本 API 与非法 Schema

```kotlin
import com.mingliqiye.document.format.avro.AvroMapper
import com.mingliqiye.document.format.avro.exception.AvroParseException
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import org.apache.avro.Schema

// 非 record Schema（如 "int"）不能作为 Mapper 根 Schema
val error = runCatching { AvroMapper(Schema.create(Schema.Type.INT)) }.exceptionOrNull()
check(error is IllegalArgumentException)

// 文本 API 不支持
val textError = runCatching { mapper.parse("{}", User::class.java) }.exceptionOrNull()
check(textError is DocumentSerializationException)

// 裸数据尾随字节
val bytes = mapper.formatBytes(user)
val trailingError = runCatching {
    mapper.parse(bytes + byteArrayOf(0), Node::class.java)
}.exceptionOrNull()
check(trailingError is AvroParseException)
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| writer / reader Schema 不是 record | 构造 `AvroMapper` 抛 `IllegalArgumentException` |
| 裸二进制读取 | 必须由调用方提供 Schema；二进制本身不携带 |
| 文本 API（`parse(String)` / `format`） | `DocumentSerializationException`（只支持二进制） |
| 尾随字节 | `AvroParseException("Unexpected trailing bytes after Avro root datum")` |
| Schema 无法演进 | `AvroParseException` |
| 数据损坏 / 字节截断 | `AvroParseException` |
| `float` / `double` NaN、Infinity | `AvroParseException` |
| 追加时 Schema / codec 不一致 | `AvroException` |
| 不支持的 OCF codec（bzip2/xz/zstandard 等） | 读取抛 `AvroParseException` |
| OCF 记录不是 record | `AvroException` |
| `fixed` 字节长度不符 | `AvroException` |
| 未能表示为 Node 的 Avro 类型（如自定义逻辑类型） | 按 Schema 归一化规则处理，无法表示的抛异常 |
| 裸 wire 写出 | 只能写单条记录；多条记录请用 OCF |
| `maxNodeDepth` | 在 datum → Node 转换时生效 |

## 与通用 `DocumentMapper` 的差异

- 继承 `BinaryDocumentMapper`：仅二进制 API，文本/字符流 API 抛 `DocumentSerializationException`；
- Schema 驱动：构造必须提供 record Schema（或 writer/reader 双 Schema）；
- 额外能力：OCF 容器读写、append、codec、Schema 演进；
- 公开的 Node 级工具：`AvroSchema` / `AvroSchemaBuilder` / `AvroType` / `AvroCodec`；`AvroContainerReader` / `AvroContainerWriter` / `AvroWireReader` / `AvroWireWriter` / `AvroDatumConverter` 为内部实现，通过 `AvroMapper` 使用；
- 没有格式专属 Config 类，使用 `DocumentMapperConfig`。

## 相关链接

- [格式总览](./)
- [Schema 模块](../modules/schema.md)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-avro 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-avro/README.md)
