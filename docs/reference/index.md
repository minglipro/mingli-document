# 参考总览

本「参考」章节是 `mingli-document` 的跨模块索引：注解、API、异常、Token 协议，以及版本
变更与路线图。与「模块」章节的区别是：模块章节按模块讲职责与用法，参考章节按**主题**
做全库索引，方便查签名、查行为、查异常。

- 文档对应版本：`2.1.0`
- 源码基线：`dev` 分支（文档编写时的仓库状态；`gradle.properties` 中
  `projectVersion=2.1.0`）
- 目标平台：JDK 8+（Java record 支持需要 JDK 16+ 运行时）
- 语言：Kotlin 2.1.0、Java 8+

## 页面索引

| 页面 | 内容 |
|---|---|
| [注解参考](./annotations.md) | 全部注解的签名、目标、保留策略、属性、优先级、示例 |
| [API 参考](./api.md) | `DocumentMapperApi` / `DocumentMapper` / `Node` / Builder / Token 等常用 API 索引 |
| [异常参考](./exceptions.md) | 异常层级、逐类触发条件、错误消息、捕获模式 |
| [Token 参考](./token.md) | Token 类型、Reader/Writer 协议、格式支持矩阵、自定义实现 |
| [变更记录](./changelog.md) | 版本历史（站点版 CHANGELOG） |
| [路线图](./roadmap.md) | 阶段计划、已完成/待办项、各版本交付状态 |

## 五分钟速览

```kotlin
// 1) 创建 Mapper
val mapper = com.mingliqiye.document.format.json.JsonMapper()

// 2) 文本 → Bean
val user = mapper.parse("""{"name":"小于","age":18}""", User::class.java)

// 3) Bean → 文本
val json = mapper.format(user)
val pretty = mapper.formatPretty(user)

// 4) 树 → Bean / Bean → 树
val node = mapper.toNode(user)
val copy = mapper.fromNode(node, User::class.java)

// 5) 泛型 + 文件
val type = com.mingliqiye.document.core.DocumentTypeReference.listType(User::class.java)
val users = mapper.parse(text, type)
mapper.format(users, java.io.File("users.json"))
```

## 术语表

| 术语 | 含义 |
|---|---|
| `Node` | 格式无关的文档树节点，7 种类型（object/array/string/number/boolean/binary/null） |
| Mapper | 某个格式的编解码入口，如 `JsonMapper`，统一继承 `DocumentMapper` |
| Node 路径 | 先建 `Node` 树再映射 Bean 的通用路径 |
| Token 路径 | 格式事件流直接映射 Bean 的流式路径 |
| `DocumentTypeReference` | 保留泛型信息的类型引用 |
| 转换器 | 实现 `DocumentBaseConverter` / Serializer / Deserializer 的自定义字段编解码 |
| 模块 | 实现 `DocumentModule` 的 Mapper 扩展包（如 `kotlinModule()`） |
| 字段级配置 | 通过注解或 `DocumentMapperConfigBuilder.field(...)` 为单个字段定制行为 |
| `$ref` | JSON Pointer 形式的本地/远程引用，可读写对象身份引用 |
| `MissingFieldPolicy` | 文档缺少 Bean 字段时的处理策略 |
| `MergePolicy` | 文档树合并到已有对象时的深浅合并/null 策略 |

## 快速查找

| 我想… | 去看 |
|---|---|
| 改字段名 / 忽略字段 / 自定义转换器 | [注解参考](./annotations.md) |
| 把 Java Bean 与 JSON 互转 | [api.md · DocumentMapperApi](./api.md#documentmapperapi) |
| 处理多态类型 | [注解参考](./annotations.md) + [serialization 模块](../modules/serialization.md) |
| 处理 Kotlin data class | [serialization-kotlin](../modules/serialization-kotlin.md) |
| 配置缺失字段 / 未知字段 / null 写出 / 命名策略 | [api.md · 配置](./api.md#documentmapperconfig) |
| 从二进制格式直接映射 Bean | [token.md](./token.md) |
| 限制文档深度/大小/字符串长度 | [api.md · 安全限制](./api.md#安全限制) |
| 捕获解析或映射错误 | [异常参考](./exceptions.md) |
| 生成 OpenAPI 文档 | [openapi 模块](../modules/openapi.md) |
| 用 KSP 生成枚举转换器 | [codegen 模块](../modules/codegen.md) |
| 统一依赖版本 | [bom 模块](../modules/bom.md) |

## 阅读约定

### 版本与包名

- 普通模块包前缀：`com.mingliqiye.document.<module>`
- 格式模块包前缀：`com.mingliqiye.document.format.<format>`
- 异常包：`com.mingliqiye.document.<module>.exception` 或
  `com.mingliqiye.document.format.<format>.exception`
- 代码示例默认导入 `com.mingliqiye.document.format.json.JsonMapper`（最常见场景）

### 文本格式与二进制格式

| 类别 | 支持的 API | 代表格式 |
|---|---|---|
| 文本格式 | `parse(String)`、`format`、`formatPretty`、`Reader`/`Writer`、`File`/`Path`/`InputStream`/`OutputStream` | JSON、YAML、XML、Properties、TOML、CSV、Ion（文本）、JSONC |
| 二进制格式 | `parse(ByteArray)`、`formatBytes`、`File`/`Path`/`InputStream`/`OutputStream` | CBOR、Smile、Avro、Protobuf、Ion（二进制） |

- 文本格式的 `parse(ByteArray)` / `formatBytes` 由基类提供默认实现（按字符集编解码）；
- 二进制格式的 `parse(String)` / `format` 会抛 `DocumentSerializationException`；
- 二进制格式不支持 `Reader` / `Writer` 重载。

### null 与缺字段

- `ObjectNode.get(key)` 返回 `null` 表示**字段不存在**；
- 返回 `NullNode.INSTANCE` 表示**字段存在且值为 null**；
- Bean 反序列化的缺字段行为由 `MissingFieldPolicy` 决定；
- `writeNulls = false` 时 Bean 的 null 字段不写出。

### 异常

- 所有库异常都继承 `DocumentException`（运行时异常）；
- 配置期错误通常是 `IllegalArgumentException`；
- 解析错误抛各格式的 `XxxParseException`；映射错误抛
  `DocumentSerializationException`；节点类型错误抛 `DocumentTypeException`；
- 详见 [异常参考](./exceptions.md)。

### 代码示例

示例默认使用 Kotlin，Java 调用方式一致（静态方法用 `@JvmStatic` 暴露的工厂：
`ObjectNode.of(...)`、`DocumentMapperConfig.builder()`、`JsonMapper.builder()` 等）。
省略的 import 可在各页面上下文中找到。

## 模块交叉索引

| 模块 | 参考内容 |
|---|---|
| [`annotation`](../modules/annotation.md) | 本参考的注解章节就是它的完整 API |
| [`core`](../modules/core.md) | `Node` / `NodeType` / `DocumentTypeReference` |
| [`exception`](../modules/exception.md) | 异常基类与格式异常层级 |
| [`serialization`](../modules/serialization.md) | Mapper、配置、Token、转换器 |
| [`serialization-kotlin`](../modules/serialization-kotlin.md) | Kotlin 类型支持 |
| [`schema`](../modules/schema.md) | `DocumentSchema` / `SchemaType` |
| [`codegen`](../modules/codegen.md) | KSP 枚举转换器生成 |
| [`openapi`](../modules/openapi.md) | OpenAPI 3.0 生成 |
| [`bom`](../modules/bom.md) | Maven BOM |
| `format-*` | 站点「格式」章节（JSON/YAML/.../Protobuf） |

## 稳定性说明

- 标注为 `public` 的 API 遵循语义化版本：2.x 内向后兼容；
- `internal` / `private` 类型不属于公共 API（例如 `Default*Node`、`SimpleDocumentSchema`、
  `KotlinDataClassInstantiator`），升级时可能变化；
- 异常 message 不是稳定契约，不要按 message 分支；
- 生成代码（KSP、`DocumentCodecSourceGenerator`）的类名/包名在 2.x 内保持兼容；
- 文档中的行为描述均标注了「源码依据」，若与你的版本不符，以对应版本的源码为准。

## 版本提醒

| 版本 | 说明 |
|---|---|
| `1.0.0` | 初始发布（core/annotation/exception/schema/serialization/serialization-kotlin + 11 格式 + BOM） |
| `1.1.0` | 文件/流 API、命名策略、多态、构造器注入、Builder、`$ref`、JSON Schema、安全限制 |
| `1.2.0` | 各格式解析深度限制、远程 `$ref` |
| `1.3.0` | Ion/CBOR 类型保真、CSV Token、`maxStringLength`、YAML alias 限制 |
| `1.4.0` | YAML/XML/Properties/TOML 原生 Token Reader、Schubfach Double、`fuzzContinuous` |
| `1.5.0` | Jazzer fuzz 目标与 `fuzzJazzer` |
| `2.1.0` | 代码生成（KSP/运行期）、Java record、只读属性、merge、CBOR sequence、Smile 多消息、Kotlin value class/sealed、CSV 嵌套、Protobuf 高级语义、安全基线、OpenAPI |

详细变更见 [变更记录](./changelog.md)，未来计划见 [路线图](./roadmap.md)。
