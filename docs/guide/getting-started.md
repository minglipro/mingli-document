---
title: 快速开始
---

# 快速开始

> `mingli-document` 是一个**格式无关的 Java / Kotlin 文档处理库**。
> 它以统一的文档树（`Node`）作为中间层，在 JSON、YAML、XML、Properties、TOML、CSV、
> Ion、CBOR、Smile、Avro、Protobuf 之间读写，并支持 Java Bean 与 Kotlin data class
> 的映射。
>
> 本页目标：10 分钟内跑通第一个例子，理解整体分层，知道接下来该读哪一页。

## 目录

- [项目定位](#项目定位)
- [能力总览](#能力总览)
- [5 分钟上手](#_5-分钟上手)
- [第一个 JSON 示例](#第一个-json-示例)
- [第一个 YAML 示例](#第一个-yaml-示例)
- [第一个 Java 示例](#第一个-java-示例)
- [坐标与模块](#坐标与模块)
- [项目目录结构](#项目目录结构)
- [学习路径](#学习路径)
- [下一步](#下一步)

## 项目定位

绝大多数 JSON 库把「文本格式」和「数据结构」焊死在一起：Jackson 的 `JsonNode`
只能表示 JSON，XML 有自己的一套 DOM，YAML 又是另一套对象。一旦你的系统需要在
多种格式之间转换、或者让同一个实体既能读 JSON 又能读 YAML，就会出现大量重复的
映射代码。

`mingli-document` 的做法是插入一层**统一的文档树**：

```text
文本 / 二进制
      ↓  parse
 DocumentMapper
      ↓  toNode
   Node 树（ObjectNode / ArrayNode / 标量）
      ↓  fromNode
Java Bean / Kotlin data class
```

由此得到三个稳定承诺：

1. **统一的模型**：所有格式共用 `Node` 树，格式之间可以直接互转，不必经过实体类。
2. **统一的 API**：换格式只换 Mapper 类，方法签名保持一致。
3. **统一的方言**：注解、配置、IO、Token 在 11 种格式上语义一致。

它适合：

- 配置中心类应用：同一份配置要支持 JSON / YAML / TOML / Properties。
- 数据交换与迁移：把 JSON 数据改写成 XML / CSV / Avro。
- 协议与网关：对外 JSON，对内 CBOR / Protobuf。
- 需要严格安全边界的服务端：统一的最大输入、深度、别名、XXE 防护。

它**不是**一个 JSON 解析器竞速库，也不是完整的 ORM / 序列化框架替代品。
性能基准、支持范围与限制都写在源码与各模块 README 中，本指南不夸大能力。

## 能力总览

| 能力 | 说明 | 详见 |
|---|---|---|
| 统一文档树 | `ObjectNode`、`ArrayNode`、`StringNode`、`NumberNode`、`BooleanNode`、`BinaryNode`、`NullNode` | [/modules/core.html](/modules/core.html) |
| 11 种格式 | JSON/JSONC、YAML、XML、Properties、TOML、CSV、Ion、CBOR、Smile、Avro、Protobuf | [/formats/](/formats/) |
| Bean 映射 | JavaBean、构造器注入、Builder、record、只读属性、merge | [Bean 序列化](/guide/serialization.html) |
| Kotlin 支持 | data class、value class、sealed class/interface 多态 | [/modules/serialization-kotlin.html](/modules/serialization-kotlin.html) |
| 文本 + 二进制 API | `parse(String)`、`format`、`parse(ByteArray)`、`formatBytes` | [文件与流](/guide/io.html) |
| Token 流 | `DocumentTokenReader` / `DocumentTokenWriter`，二进制格式直读 Bean | [Token 流式 API](/guide/token-stream.html) |
| 注解 | 字段名、忽略、日期、数字、转换器、多态、XML 根元素 | [/modules/annotation.html](/modules/annotation.html) |
| 配置体系 | 通用 `DocumentMapperConfig` + 格式专属 Config | [配置](/guide/configuration.html) |
| 安全边界 | 深度/节点数/字符串长度限制、XXE 防护、YAML SafeConstructor、alias 限制 | [Fuzz 与安全](/guide/fuzz-security.html) |
| 代码生成 | KSP 编译期枚举转换器、运行期 Bean 编解码器与访问器 | [代码生成](/guide/codegen.html) |
| 开发者工具 | JMH 基准、Jazzer / JQF / 持续随机 fuzz、OpenAPI 生成 | [/modules/openapi.html](/modules/openapi.html) |

格式能力速查（读取 / 写出均为 ✅ 表示双向支持）：

| 格式 | 读取 | 写出 | 代表能力 |
|---|---|---|---|
| JSON / JSONC | ✅ | ✅ | 注释、严格 UTF-8 byte API、重复 key 策略、深度限制、轻量 JSON Schema |
| YAML | ✅ | ✅ | 多文档、`!!binary`、自定义 tag、alias 限制 |
| XML | ✅ | ✅ | `type`/`nil` 协议、属性映射、PREFIX/CLARK 命名空间、混合内容策略 |
| Properties | ✅ | ✅ | 扁平键值、`.` 路径、`[index]` 数组 |
| TOML | ✅ | ✅ | 标准表/数组、嵌套表、内联表写出 |
| CSV | ✅ | ✅ | 平坦表、表头、类型推断、CSV Schema、嵌套单元格 |
| Ion | ✅ | ✅ | 文本 + 二进制、BLOB/CLOB、可选类型保真 |
| CBOR | ✅ | ✅ | 二进制、canonical、sequence 多值、可选 tag 保真 |
| Smile | ✅ | ✅ | 二进制、多消息流、Token 直读写 |
| Avro | ✅ | ✅ | Schema、wire、OCF、schema evolution |
| Protobuf | ✅ | ✅ | Descriptor + DynamicMessage、嵌套/enum/map/oneof/extension |

## 5 分钟上手

### 1. 加依赖

Gradle（Kotlin DSL）：

```kotlin
repositories {
    maven {
        url = uri("https://repository.mingliqiye.com/repository/maven-public/")
    }
    mavenCentral()
}

dependencies {
    implementation("com.mingliqiye.document.format:json:2.0.0")
    implementation("com.mingliqiye.document.format:yaml:2.0.0")
}
```

Maven：

```xml
<dependency>
    <groupId>com.mingliqiye.document.format</groupId>
    <artifactId>json</artifactId>
    <version>2.0.0</version>
</dependency>
```

只需要 JSON 时，加一个 `format:json` 就够了：它会传递依赖
`core`、`exception`、`serialization`。完整坐标见 [安装与依赖](/guide/installation.html)。

### 2. 写第一个类

```kotlin
class User {
    var name: String = ""
    var age: Int = 0
}
```

普通 JavaBean 规则即可：无参构造器 + 可写属性（getter/setter 或 public 字段）。

### 3. 创建 Mapper 并跑通

```kotlin
import com.mingliqiye.document.format.json.JsonMapper

fun main() {
    val mapper = JsonMapper()

    val user = User().apply {
        name = "小于"
        age = 18
    }

    // 实体 -> 文档树
    val node = mapper.toNode(user)
    println(node.asObject()["name"]?.asString()) // 小于

    // 文档树 -> 实体
    val restored = mapper.fromNode(node, User::class.java)
    println("${restored.name}:${restored.age}") // 小于:18

    // 文本 -> 实体
    val parsed = mapper.parse("""{"name":"小于","age":18}""", User::class.java)
    println(parsed.age) // 18

    // 实体 -> 文本
    println(mapper.format(parsed))       // 紧凑 JSON
    println(mapper.formatPretty(parsed)) // 缩进 JSON
}
```

到这里核心心智模型已经成立：

```text
实体 ←→ Node 树 ←→ 文本 / 二进制
```

## 第一个 JSON 示例

### 读取：文本到实体、文本到树

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper()

val json = """
    {
      "name": "小于",
      "age": 18,
      "tags": ["java", "kotlin"]
    }
""".trimIndent()

// 直接到实体
val user = mapper.parse(json, User::class.java)

// 或者先到通用文档树，再自行处理
val node: Node = mapper.parse(json, Node::class.java)
val name = node.asObject()["name"]?.asString()
val tags = node.asObject()["tags"]?.asArray()?.map { it.asString() }
println("$name / $tags") // 小于 / [java, kotlin]
```

### 写出：实体到文本

```kotlin
println(mapper.format(user))
println(mapper.formatPretty(user))
```

需要字节时：

```kotlin
val bytes: ByteArray = mapper.formatBytes(user) // UTF-8 编码的 JSON 字节
val back = mapper.parse(bytes, User::class.java)
```

> JSON 的 `parse(ByteArray)` 走**严格 UTF-8** 解码，非法字节会抛
> `JsonParseException`，不会静默替换。

### JSONC 与重复 key

```kotlin
import com.mingliqiye.document.format.json.DuplicateKeyPolicy
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper

val mapper = JsonMapper(
    JsonConfig.builder()
        .allowComments(true)                       // 允许 // 与 /* */ 注释
        .pretty(true)                              // format() 默认缩进
        .duplicateKeyPolicy(DuplicateKeyPolicy.FAIL) // 重复 key 直接报错
        .maxDepth(512)                             // 嵌套深度上限
        .build(),
)
```

## 第一个 YAML 示例

格式 Mapper 的 API 完全一致，换一个类即可：

```kotlin
import com.mingliqiye.document.format.yaml.YamlMapper

val yaml = YamlMapper()

val text = """
    name: 小于
    age: 18
""".trimIndent()

val user = yaml.parse(text, User::class.java)
val output = yaml.format(user)
```

YAML 多文档也是同一个 Mapper：

```kotlin
val documents = yaml.parseAllToNodes("a: 1\n---\nb: 2\n")
val textAll = yaml.formatAll(listOf(User().apply { name = "a" }, User().apply { name = "b" }))
```

### 换一种格式继续用

```kotlin
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.xml.XmlMapper

val xml = XmlMapper()
val csv = CsvMapper()

val xmlText = xml.format(user)
val csvText = csv.format(listOf(user)) // CSV 根节点是数组
```

Node 模型不变，所以也可以**不经过实体**直接跨格式转换：

```kotlin
val jsonNode = JsonMapper().parse("""{"name":"小于","age":18}""", Node::class.java)
val yamlText = YamlMapper().format(jsonNode)
val xmlText = XmlMapper().format(jsonNode)
```

## 第一个 Java 示例

```java
import com.mingliqiye.document.format.json.JsonMapper;

public class Demo {
    public static class User {
        private String name;
        private int age;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    public static void main(String[] args) {
        JsonMapper mapper = new JsonMapper();

        User user = new User();
        user.setName("小于");
        user.setAge(18);

        String json = mapper.format(user);
        User parsed = mapper.parse("{\"name\":\"小于\",\"age\":18}", User.class);
        System.out.println(parsed.getName());
    }
}
```

Java 侧注意：泛型目标类型没有 reified 语法，使用
`DocumentTypeReference.listType(User.class)` / `DocumentTypeReference.mapType(...)`，
或者 `DocumentTypeReference.of(clazz)`。

## 坐标与模块

普通模块 group 为 `com.mingliqiye.document`，格式模块 group 为
`com.mingliqiye.document.format` 且 artifactId 去掉 `format-` 前缀：

```text
com.mingliqiye.document:annotation
com.mingliqiye.document:exception
com.mingliqiye.document:core
com.mingliqiye.document:schema
com.mingliqiye.document:serialization
com.mingliqiye.document:serialization-kotlin
com.mingliqiye.document:codegen
com.mingliqiye.document:openapi
com.mingliqiye.document:mingli-document-bom

com.mingliqiye.document.format:json
com.mingliqiye.document.format:yaml
com.mingliqiye.document.format:xml
com.mingliqiye.document.format:properties
com.mingliqiye.document.format:toml
com.mingliqiye.document.format:csv
com.mingliqiye.document.format:ion
com.mingliqiye.document.format:cbor
com.mingliqiye.document.format:smile
com.mingliqiye.document.format:avro
com.mingliqiye.document.format:protobuf
```

当前版本 `2.0.0`。不想给每个模块写版本时用 BOM，详见
[安装与依赖](/guide/installation.html)。

## 项目目录结构

```text
mingli-document/
├── build.gradle.kts            # 根构建脚本：版本、发布、签名、Dokka
├── gradle.properties           # projectVersion / projectGroup / formatProjectGroup / jvmVersion
├── settings.gradle.kts         # 全部子模块
├── README.md                   # 项目总览
├── MIGRATION.md                # 1.x -> 2.0.0 迁移
├── SECURITY.md                 # 安全基线
├── benchmarks/                 # JMH 基准（不发布）
├── fuzz/                       # Jazzer / JQF / 持续 fuzz（不发布）
├── docs/                       # 本 VuePress 文档站
├── annotation/                 # 格式无关注解 + 标记接口
├── exception/                  # DocumentException / DocumentTypeException
├── core/                       # Node 树、DocumentTypeReference
├── schema/                     # DocumentSchema / SchemaField / SchemaType
├── serialization/              # DocumentMapper、Bean 映射、Token、配置、运行期代码生成
├── serialization-kotlin/       # data class / value class / sealed 支持
├── codegen/                    # KSP 处理器（编译期）
├── codegen-test/               # KSP 集成测试（不发布）
├── openapi/                    # OpenAPI 3.0 文档生成
└── format-*/                   # 11 个格式模块
```

包路径统一以 `com.mingliqiye.document` 开头：

```text
com.mingliqiye.document.core               Node / DocumentTypeReference
com.mingliqiye.document.annotation         注解
com.mingliqiye.document.exception          异常基类
com.mingliqiye.document.schema             Schema
com.mingliqiye.document.serialization      Mapper / 配置 / Token / Bean 映射
com.mingliqiye.document.serialization.kotlin   Kotlin 类型支持
com.mingliqiye.document.serialization.codegen  运行期代码生成
com.mingliqiye.document.format.<format>    各格式实现
com.mingliqiye.document.openapi           OpenAPI 生成
```

各模块细节见 [/modules/](/modules/)，各格式细节见 [/formats/](/formats/)。

## 学习路径

建议按以下顺序阅读：

1. **本页**：跑通例子，理解「实体 ←→ Node ←→ 文本」。
2. [安装与依赖](/guide/installation.html)：确认坐标、BOM、JDK/Kotlin 版本与发布仓库。
3. [核心概念](/guide/core-concepts.html)：Node 类型、Mapper 层次、Node vs Bean。
4. [Bean 序列化](/guide/serialization.html)：注解、构造器、record、Kotlin、多态、merge。
5. [文件与流](/guide/io.html)：String / ByteArray / File / Path / 流 的正确用法。
6. [配置](/guide/configuration.html)：把行为调成你想要的样子。
7. [Token 流式 API](/guide/token-stream.html)：大对象、二进制格式直读。
8. [代码生成](/guide/codegen.html) 与 [Fuzz 与安全](/guide/fuzz-security.html)：进阶能力。
9. 具体格式查 [/formats/](/formats/)，具体模块查 [/modules/](/modules/)。

想要一个真实可运行的完整示例集合，可以看仓库里的测试：`format-json/src/test`、
`serialization/src/test`、`serialization-kotlin/src/test` 覆盖了每种能力的最小复现。

## 下一步

| 我想…… | 去看 |
|---|---|
| 用 YAML/XML/CSV 读写 | [/formats/](/formats/) |
| 让 Kotlin data class 直接反序列化 | [Bean 序列化](/guide/serialization.html) |
| 处理文件、目录、字符集、原子写 | [文件与流](/guide/io.html) |
| 限制不可信输入的大小与深度 | [Fuzz 与安全](/guide/fuzz-security.html) |
| 从 Jackson / 1.x 迁过来 | [迁移指南](/guide/migration.html) |
| 查常见坑 | [常见问题](/guide/faq.html) |

::: tip 一句话记住
所有格式都是 `DocumentMapper` 的子类；所有数据都先变成 `Node`；
所有 IO 都围绕 `String`、`ByteArray`、`File`、`Path`、`InputStream`、`OutputStream`、
`Reader`、`Writer` 这几种来源展开。
:::
