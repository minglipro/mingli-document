---
home: true
sidebar: false
title: mingli-document
heroText: mingli-document
tagline: Kotlin / JVM 多格式文档映射库
actions:
  - text: 快速开始
    link: /guide/getting-started.html
    type: primary
  - text: 格式一览
    link: /formats/
    type: secondary
features:
  - title: 统一 Node 模型
    details: JSON/YAML/XML/Properties/TOML/CSV/Ion/CBOR/Smile/Avro/Protobuf 共用同一套 ObjectNode / ArrayNode / 标量节点模型，格式之间可以直接转来转去。
  - title: 两种映射路径
    details: Node 路径稳定通用；原生 Token Reader 路径直接从格式事件映射 Bean，减少中间树构建，并支持 Token 深度限制。
  - title: 完整 IO 能力
    details: String、ByteArray、File、Path、InputStream、OutputStream、Reader、Writer 全部覆盖；文本格式支持 charset、原子写、自动父目录。
  - title: 流式写出
    details: JSON、Ion 文本、XML、YAML、CSV、Properties、TOML、CBOR、Smile 已支持直接写 OutputStream，避免先构造完整 String/ByteArray。
  - title: 代码生成
    details: KSP 编译期生成枚举转换器与 DocumentModule；运行期 Bean 编解码器/属性访问器按需生成并缓存。
  - title: 安全与 fuzz
    details: XXE 防护、YAML alias 限制、深度/节点数/字符串长度限制；Jazzer、JQF、持续随机 fuzz 工具链。
footer: Apache-2.0 Licensed | Copyright © minglipro
---

## 一眼看懂

```kotlin
val mapper = JsonMapper()
val node = mapper.parse("""{"name":"小于","age":18}""", Node::class.java)
val user = mapper.parse("""{"name":"小于","age":18}""", User::class.java)
val json = mapper.format(user)
```

```kotlin
val yaml = YamlMapper()
val node = yaml.parse(yamlText, Node::class.java)
val bean = yaml.parse(yamlText, User::class.java)
```

## 模块地图

| 模块 | 职责 |
|---|---|
| `core` | Node 树、类型引用 |
| `annotation` | 格式无关注解 |
| `exception` | 异常基类 |
| `serialization` | DocumentMapper、Bean 映射、Token、配置 |
| `serialization-kotlin` | Kotlin data class / value class / sealed |
| `schema` | 格式无关 Schema |
| `codegen` | KSP 编译期代码生成 |
| `openapi` | OpenAPI 3.0 生成 |
| `format-*` | 各格式 Mapper |
| `bom` | Maven BOM |

## 从哪里开始

1. [安装与坐标](./guide/installation.md)
2. [核心概念](./guide/core-concepts.md)
3. [Bean 序列化](./guide/serialization.md)
4. [文件与流 IO](./guide/io.md)
5. [选择格式](./formats/)

## 版本

当前文档对应 `2.5.1`。历史变更见 [CHANGELOG](./reference/changelog.md)。
