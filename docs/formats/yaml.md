---
title: YAML
description: format-yaml 模块：YamlMapper、YamlConfig、多文档、!!binary、自定义 tag、alias 限制与原生 Token 读取
---

# YAML

`format-yaml` 提供 YAML 单文档与多文档读写，核心类型是 `YamlMapper`。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:yaml:2.1.0")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `yaml` |
| 版本 | `2.1.0` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | `org.yaml:snakeyaml:2.6` |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.yaml` |

## 特性

- **单文档 + 多文档**：`parse` / `format` 处理单文档；`parseAllToNodes` / `parseAll` / `formatAll` 处理 `---` 分隔的多文档；
- **安全构造**：默认使用 SnakeYAML `SafeConstructor`，只还原标准标量、序列、映射，不会实例化任意 Java 类型；
- **`!!binary` → `BinaryNode`**：Base64 二进制解码为真正的二进制节点，而不是 Base64 字符串；
- **timestamp → 字符串**：`!!timestamp` 映射为 ISO-8601 instant 文本（Token 路径与 Node 路径一致）；
- **原生 Token 路径**：`parse(String)` 默认通过 `YamlTokenReader` 直接消费 Token，不构建 Node 树；
- **自定义 tag 可选**：`allowCustomTags(true)` 后未知 tag（`!foo`、`!env`、`!!foo`、`!<tag:...>`）按节点种类还原；
- **alias 限制**：`maxAliases` 限制非标量 alias 数量，防御 alias 扩张攻击；
- **深度限制**：`maxNodeDepth` 交给 SnakeYAML 的 `nestingDepthLimit`；
- **文件/流读写**：文本 UTF-8（可改 `fileCharset`），`OutputStream` 直接流式写出；
- **递归 alias 防护**：递归节点（如 `&a {self: *a}`）抛 `YamlParseException`，不构建环。

## 配置：`YamlConfig`

`YamlConfig` 继承 `DocumentMapperConfig`，增加 YAML 专属选项。

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `pretty` | `Boolean` | `false` | `format()` 是否默认输出块格式（block）YAML；默认使用流式（flow）紧凑格式 |
| `maxAliases` | `Int` | `0` | 单文档允许的非标量 alias 最大数量；`0` 表示使用 SnakeYAML 默认值 |
| `allowCustomTags` | `Boolean` | `false` | 是否允许未知自定义 tag，默认保持 `SafeConstructor` 行为 |

`YamlConfigBuilder` 方法：

| 方法 | 说明 |
|---|---|
| `field(Class, String, DocumentBaseConverter)` | 字段转换器 |
| `missingFieldPolicy(MissingFieldPolicy)` | 缺字段策略 |
| `failOnUnknownFields(Boolean)` | 未知字段是否报错 |
| `writeNulls(Boolean)` | null 字段是否写出 |
| `pretty(Boolean)` | 默认块格式输出 |
| `maxAliases(Int)` | alias 数量限制（≥ 0） |
| `allowCustomTags(Boolean)` | 允许未知 tag |
| `subtype(...)` / `typeProperty(...)` | 多态子类型注册 |
| `resolveReferences(Boolean)` / `writeReferences(Boolean)` | 本地 `$ref` |
| `build()` | 构建 `YamlConfig` |

> `YamlConfigBuilder` 未暴露 `namingStrategy`、`fileCharset`、`atomicFileWrite`、`createParentDirectories`、`maxStringLength` 等通用选项；需要时直接构造 `DocumentMapperConfig` 传给 `YamlMapper`，此时 YAML 专属项回退默认值（`pretty=false`、`maxAliases=0`、`allowCustomTags=false`）。

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper

val mapper = YamlMapper(
    YamlConfig.builder()
        .pretty(true)          // format() 输出块格式
        .maxAliases(50)        // 限制 alias 数量
        .allowCustomTags(true)
        .build(),
)
```

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `YamlMapper()` | 默认配置：紧凑流式输出、SnakeYAML 默认 alias 限制、拒绝未知 tag |
| `YamlMapper(config: DocumentMapperConfig)` | 传 `YamlConfig` 时 YAML 专属选项生效 |

`YamlMapper.builder()` 返回 `YamlMapperBuilder`，继承 `DocumentMapperBuilder<YamlMapper>`：

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper

val mapper = YamlMapper.builder()
    .config(YamlConfig.builder().pretty(true).build())
    .addConverter(myConverter)
    .build()
```

Builder 提供 `addSerializer`、`addDeserializer`、`addConverter`、`addModule`/`addModules`、`config`、`objectInstantiator`、`build`。

## 读取 API

### 单文档

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType)` | 默认走原生 Token 读取；配置了非零 `maxNodeDepth` / `maxNodeCount` / `maxStringLength` 时回退 Node 路径 |
| `parse(source: ByteArray, targetType)` | 按 `fileCharset` 解码成文本后解析 |
| `parse(file / path, targetType)` | 文件 → 实体 |
| `parse(input: InputStream, targetType)` | 流 → 实体（读尽后按字符集解码） |
| `parse(reader: Reader, targetType)` | 字符流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |

### 独立读取器

```kotlin
import com.mingliqiye.document.format.yaml.YamlMultiDocumentReader
import com.mingliqiye.document.format.yaml.YamlReader

// 单文档
val node = YamlReader("name: 小于\nage: 18\n").read()

// 带限制与自定义 tag
val guarded = YamlReader(
    source = "a: &x [1, 2]\nb: *x\n",
    maxDepth = 32,
    maxAliases = 10,
    allowCustomTags = false,
).read()

// 多文档
val documents = YamlMultiDocumentReader("a: 1\n---\nb: 2\n").readAll()

// 静态便捷方法
val node2 = YamlReader.parse("a: 1\n")
val documents2 = YamlMultiDocumentReader.parseAll("a: 1\n---\nb: 2\n")

// 扩展函数
val node3 = "a: 1\n".toYamlNode()
val documents3 = "a: 1\n---\nb: 2\n".toYamlNodes()
```

| 类/函数 | 说明 |
|---|---|
| `YamlReader(source, maxDepth = 0, maxAliases = 0, allowCustomTags = false)` | 单文档读取器；空文本/仅注释返回 `NullNode`；多文档输入报错 |
| `YamlReader.parse(source)` | 静态单文档解析 |
| `String.toYamlNode()` | 字符串 → 单文档 Node |
| `YamlMultiDocumentReader(source, maxDepth = 0, maxAliases = 0, allowCustomTags = false)` | 多文档读取器，`readAll(): List<Node>` |
| `YamlMultiDocumentReader.parseAll(source)` | 静态多文档解析 |
| `String.toYamlNodes()` | 字符串 → Node 列表 |

多文档边界约定：

- 空白或只含注释的输入不构成文档，返回空列表；
- `---` / `...` 界定的空文档返回 `NullNode`，返回列表长度等于显式文档个数；
- 任一文档格式非法时整体抛 `YamlParseException`；
- anchor/alias 只在单个文档内有效，跨文档引用按 YAML 规范不支持（抛 `YamlParseException`）。

### 多文档 Mapper API

| 方法 | 说明 |
|---|---|
| `parseAllToNodes(source: String): List<Node>` | 多文档 → Node 列表 |
| `parseAll(source: String, targetType: Class<T>): List<T>` | 多文档 → 实体列表 |
| `parseAll(source: String, targetType: DocumentTypeReference<T>): List<T>` | 泛型目标实体列表 |
| `formatAll(values: Iterable<Any?>): String` | 多个值 → `---` 分隔的多文档文本 |
| `YamlMapper.parseAll<reified T>(source)` | 内联扩展，等价于 `parseAll(source, DocumentTypeReference.of<T>())` |

```kotlin
import com.mingliqiye.document.format.yaml.YamlMapper

val mapper = YamlMapper()
val text = "name: 小于\n---\nname: 阿明\n"
val users = mapper.parseAll(text, User::class.java)      // List<User>
val nodes = mapper.parseAllToNodes(text)                  // List<Node>
val output = mapper.formatAll(users)                      // "name: 小于\n---\nname: 阿明\n"
```

### Token 路径

`YamlMapper.parse(String)` 默认创建内部 `YamlTokenReader`（`internal`），直接把 YAML AST 转成 Token 流：

- `!!null` → `NULL`，`!!bool` → `BOOLEAN`；
- `!!int` / `!!float` → `NUMBER`，数字文本经 SafeConstructor 归一化（`0x1F`、`1_000` 输出十进制 raw；NaN/Infinity 拒绝）；
- `!!binary` → `BINARY`（Base64 解码为字节）；
- `!!timestamp` → `STRING`（ISO-8601 instant）；
- 未知 tag：默认拒绝；开启 `allowCustomTags` 后标量按字符串输出；
- merge key 与重复 key 复用 SafeConstructor 的 AST 级校验。

配置了非零 `maxNodeDepth` / `maxNodeCount` / `maxStringLength` 时回退 `NodeTokenReader` 路径，以保持 Node 树限制语义。

Token 层无法等价实现的语义：

- 递归节点（`&a {self: *a}`）抛 `YamlParseException`；
- 显式 `!!set` / `!!omap` / `!!pairs` 集合 tag 按底层 mapping/sequence 结构输出（Node 路径会先构造 Set/List 再得到 `ArrayNode`）；
- 显式 `!!bool` 非法值时与 Node 路径一致，得到 `NULL`。

需要自定义 Token 来源时，实现 `DocumentTokenReader` 后调用 `DocumentMapper.fromTokens(...)`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → YAML；`YamlConfig.pretty=true` 时使用块格式，否则使用流式紧凑格式 |
| `formatPretty(value)` | 实体 → 块格式 YAML |
| `formatBytes(value)` | 实体 → 按 `fileCharset` 编码的字节（默认 UTF-8） |
| `format(value, file / path)` | 写文件（遵循 `format` 的 pretty 配置） |
| `formatPretty(value, file / path)` | 写 pretty 文件 |
| `format(value, output: OutputStream)` | 通过 `OutputStreamWriter` + `YamlWriter.writeTo` 直接写出 |
| `formatPretty(value, output: OutputStream)` | 直接写出块格式 |
| `format(value, writer: Writer)` / `formatPretty(value, writer)` | 写字符流 |
| `Node.toYamlString(pretty: Boolean = false)` | 扩展函数：Node → YAML 文本 |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `format(value, OutputStream)` / `formatPretty(value, output)` | ✅ | Mapper 覆写，直接写字符流（内部 `TrimEndWriter` 去尾部空白） |
| `format(value, File/Path)` | ❌ | 先构造文本再按字符集写文件 |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

写出语义：

- 只根据 `Node` 类型生成标准 YAML，**不保留**读取时的自定义 tag；
- `BinaryNode` 交给 SnakeYAML 的 `byte[]` 表示器，输出 `!!binary` Base64 标量，可被读回 `BinaryNode`；
- 数字按 `raw` 区分整数（`BigInteger`）与小数/指数（`BigDecimal`）；
- 默认流式（flow）风格是单行紧凑形式，`pretty` / `formatPretty` 使用块格式（2 空格缩进）。

```kotlin
import com.mingliqiye.document.format.yaml.toYamlString
import com.mingliqiye.document.core.Node

val node = mapper.parse("name: 小于\nage: 18\n", Node::class.java)
println(node.toYamlString())
println(node.toYamlString(pretty = true))
```

## 类型映射

| Node | YAML 读取 | YAML 写出 |
|---|---|---|
| `ObjectNode` | mapping；非字符串 key 有限支持（见下） | mapping，字段顺序保持 |
| `ArrayNode` | sequence | sequence |
| `StringNode` | `!!str` / 普通标量 / `!!timestamp`（转 ISO 文本）/ `!!int` 之外的字符串形式 | 字符串标量（按需引号/转义） |
| `NumberNode` | `!!int` / `!!float`，`raw` 保留十进制归一化文本 | `!!int`（整数值）/ `!!float`（小数值） |
| `BooleanNode` | `!!bool` | `true` / `false` |
| `NullNode` | `null` / `~` / 空值 | `null` |
| `BinaryNode` | `!!binary`（Base64） | `!!binary`（SnakeYAML byte[] 表示） |

补充规则：

- 非字符串 mapping key：`null` → 字段名 `"null"`；string/number/boolean/char → `toString()`；其他类型不支持（Token 路径抛 `YamlParseException`，Node 路径 `YamlNodeMapping` 抛同类异常）；
- `!!timestamp` 读取为 `StringNode`（ISO-8601 instant 文本），写出为普通字符串；
- `!!set` / `!!omap` / `!!pairs` 在 Node 路径构造集合后得到 `ArrayNode`；
- YAML 的 NaN / Infinity 无法表示为 `NumberNode`，抛 `YamlParseException`；
- merge key（`<<`）由 SafeConstructor 展开，结果进入 `ObjectNode`；
- 重复 key 按 SafeConstructor 规则处理（后出现的覆盖）。

## 自定义 tag

默认拒绝未知 tag（`YamlParseException`）。开启 `allowCustomTags(true)` 后按节点种类还原：

- 标量 → `StringNode`（保留原始标量文本，不做 `!!int` / `!!bool` 隐式解析）；
- 序列 → `ArrayNode`；
- 映射 → `ObjectNode`（与普通 YAML 映射一致，先执行重复 key 校验与 merge key 展开）。

支持本地 tag（`!foo`）、全局自定义 tag（`!!foo`、`!<tag:example.com,2024:foo>`）。构造阶段只还原基础类型，不会实例化任意 Java 类型。anchor/alias/merge/深度限制仍然生效，但以自定义 tag 节点为目标的递归 alias 会被拒绝。

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.format.yaml.YamlMultiDocumentReader
import com.mingliqiye.document.format.yaml.YamlReader

// Node 路径直接传参
val node = YamlReader("value: !env HOME\n", allowCustomTags = true).read()
val documents = YamlMultiDocumentReader("a: !env HOME\n---\nb: !list [1, 2]\n", allowCustomTags = true).readAll()

// Mapper：配置同时作用于 Token 路径与 Node 回退路径
val mapper = YamlMapper(YamlConfig.builder().allowCustomTags(true).build())
val bean = mapper.parse("name: !env APP_NAME\n", User::class.java)
val all = mapper.parseAllToNodes("a: !env HOME\n---\nb: !list [1]\n")
```

写出侧不保留 tag：`!env HOME` 读成 `"HOME"` 字符串节点，再写出就是普通字符串。

## 示例

### 1. 字符串 ↔ Bean

```kotlin
import com.mingliqiye.document.format.yaml.YamlMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = YamlMapper()
val user = mapper.parse("""
    name: 小于
    age: 18
""".trimIndent(), User::class.java)

println(mapper.format(user))
println(mapper.formatPretty(user))
```

### 2. YAML → Node 树

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse("""
    name: 小于
    tags:
      - a
      - b
""".trimIndent(), Node::class.java)

println(node.asObject()["name"]?.asString())        // 小于
println(node.asObject()["tags"]?.asArray()?.size)   // 2
```

### 3. 多文档读取

```kotlin
val text = """
name: 小于
---
name: 阿明
"""

val users = mapper.parseAll(text, User::class.java)
check(users.size == 2)

val nodes = mapper.parseAllToNodes(text)
val yaml = mapper.formatAll(nodes)
println(yaml)
```

### 4. 多文档边界：显式空文档

```kotlin
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.format.yaml.YamlMultiDocumentReader

val docs = YamlMultiDocumentReader("---\na: 1\n---\n").readAll()
// 第一个文档 {a: 1}，第二个显式空文档 -> NullNode
check(docs.size == 2)
check(docs[1] is NullNode)

val empty = YamlMultiDocumentReader("   \n# 只有注释\n").readAll()
check(empty.isEmpty())
```

### 5. `!!binary` 往返

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node

val node = mapper.parse("data: !!binary |-\n  AAEC/w==\n", Node::class.java)
val bytes = node.asObject()["data"]?.asBinary()   // byteArrayOf(0, 1, 2, -1)
println(mapper.format(node))                       // 写回 !!binary
```

### 6. 紧凑 vs 块格式

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig

val compact = YamlMapper()
println(compact.format(node))          // 流式：{name: 小于, age: 18}

val block = YamlMapper(YamlConfig.builder().pretty(true).build())
println(block.format(node))            // 块格式：
// name: 小于
// age: 18
```

### 7. alias 限制

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.format.yaml.exception.YamlParseException

val yaml = """
base: &base
  a: 1
  b: 2
one: *base
two: *base
"""

val limited = YamlMapper(YamlConfig.builder().maxAliases(1).build())
val error = runCatching { limited.parse(yaml, Node::class.java) }.exceptionOrNull()
check(error is YamlParseException)
```

### 8. 递归 alias 被拒绝

```kotlin
import com.mingliqiye.document.format.yaml.YamlReader
import com.mingliqiye.document.format.yaml.exception.YamlParseException

val recursive = "a: &a\n  self: *a\n"
val error = runCatching { YamlReader(recursive).read() }.exceptionOrNull()
check(error is YamlParseException)
```

### 9. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.yaml"))
val fromFile = mapper.parse(File("build/user.yaml"), User::class.java)

mapper.formatPretty(user, Paths.get("build/user-pretty.yaml"))
val fromPath = mapper.parse(Paths.get("build/user-pretty.yaml"), User::class.java)
```

### 10. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.formatPretty(user, output)                       // 直接流式写
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 11. 读取时间戳为字符串

```kotlin
val node = YamlReader("time: 2024-01-02T03:04:05Z\n").read()
val time = node.asObject()["time"]
check(time?.isString == true)             // timestamp 统一映射为字符串
println(time?.asString())                 // 2024-01-02T03:04:05Z
```

### 12. Java 示例

```java
import com.mingliqiye.document.format.yaml.YamlMapper;

public class YamlDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        YamlMapper mapper = new YamlMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        String yaml = mapper.formatPretty(user);
        User restored = mapper.parse(yaml, User.class);
        System.out.println(restored.name + ":" + restored.age);
    }
}
```

### 13. 深度限制

```kotlin
import com.mingliqiye.document.format.yaml.YamlConfig
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.format.yaml.YamlReader

// 读取器级别
val error = runCatching { YamlReader("a:\n  b:\n    c: 1\n", maxDepth = 2).read() }.exceptionOrNull()
check(error is com.mingliqiye.document.format.yaml.exception.YamlParseException)

// Mapper 级别：maxNodeDepth > 0 时 Token 路径回退 Node 路径
val guarded = YamlMapper(YamlConfig.builder().maxNodeDepth(2).build())
val beanError = runCatching { guarded.parse("a:\n  b:\n    c: 1\n", Node::class.java) }.exceptionOrNull()
println(beanError?.message)
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 空文本 / 仅注释（单文档） | 返回 `NullNode` |
| 多文档输入给 `YamlReader.read()` | `YamlParseException`（请用 `YamlMultiDocumentReader`） |
| 多文档中跨文档 anchor 引用 | `YamlParseException`（YAML 规范不支持） |
| 未知 tag 且 `allowCustomTags=false` | `YamlParseException("Unsupported YAML tag: ...")` |
| 自定义 tag 节点作为递归 alias 目标 | `YamlParseException` |
| 非标量 alias 数量超 `maxAliases` | SnakeYAML 抛异常并包装为 `YamlParseException` |
| 嵌套深度超过 `maxDepth` | `YamlParseException` |
| 递归节点（`&a {self: *a}`） | `YamlParseException("Recursive YAML nodes are not supported")` |
| NaN / Infinity | `YamlParseException` |
| 非字符串 mapping key 为复杂类型 | `YamlParseException` |
| 单字符串超过 `maxStringLength` | `DocumentSerializationException`（Node 路径）或 `YamlParseException`（Token 路径） |
| 写出自定义 tag | 不支持保留，按普通 Node 类型写出 |
| 写出 `BinaryNode` | 写成 `!!binary` Base64 标量 |
| timestamp 往返 | 读为字符串，写回为普通字符串（类型信息丢失） |

## 与通用 `DocumentMapper` 的差异

- 构造：`YamlConfig` 专属配置类与 `YamlMapper.builder()`；
- `parse(String)`：默认走原生 `YamlTokenReader` Token 路径（非默认 Node 树），配置 Node 限制时自动回退；
- `format(value)`：受 `YamlConfig.pretty` 影响；
- `format(value, OutputStream)` / `formatPretty(value, OutputStream)`：覆写为直接写字符流（不先构造完整 `String`）；
- 多文档 API：`parseAllToNodes` / `parseAll` / `formatAll` 与扩展 `parseAll<T>`；
- YAML 专属：`YamlReader`、`YamlMultiDocumentReader`、`YamlConfig.allowCustomTags` / `maxAliases`；
- 多文档 `formatAll` 空集合返回空字符串，单个文档不额外添加起始 `---`。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [Token 与流式读写](../guide/token-stream.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-yaml 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-yaml/README.md)
