---
title: Properties
description: format-properties 模块：PropertiesMapper、点分 key 路径、[index] 数组、百分号转义与扁平文本互转
---

# Properties

`format-properties` 提供 Java Properties 格式读写，核心类型是 `PropertiesMapper`。

Properties 是扁平 key/value 文本；模块用 `.` 表示对象路径、`[index]` 表示数组下标，在扁平文本与 `Node` 树之间互转。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:properties:2.0.0")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `properties` |
| 版本 | `2.0.0` |
| 传递依赖 | `core`、`exception`、`serialization` |
| 第三方依赖 | 无（基于 JDK `java.util.Properties`） |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.properties` |

## 特性

- **`.` 对象路径**：`user.name=小于` → `{"user":{"name":"小于"}}`；
- **`[index]` 数组下标**：`user.tags[0]=a` → `{"user":{"tags":["a"]}}`；
- **百分号转义**：字段名中的 `%`、`.`、`[`、`]` 用 `%25` / `%2E` / `%5B` / `%5D` 表示，避免与路径分隔符冲突；
- **类型推断**：`true` / `false` / 数字 / 字符串；
- **路径冲突检测**：同一路径既做对象又做标量/数组时抛 `PropertiesParseException`；
- **原生 Token 读取**：`parse(String)` 通过内部 `PropertiesTokenReader` 直接从扁平 key/value 生成 Token 流，不构建 Node 树；
- **文件/流读写**：文本 UTF-8（可改 `fileCharset`）；
- **无专属配置类**：使用通用 `DocumentMapperConfig`。

## 配置

Properties 模块没有专属 Config 类，直接使用 `DocumentMapperConfig`（通过 `PropertiesMapper(config)` 传入）：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `missingFieldPolicy` | `KEEP_DEFAULT` | 缺字段策略 |
| `failOnUnknownFields` | `false` | 未知字段是否报错 |
| `writeNulls` | `true` | null 字段是否写出 |
| `namingStrategy` | `AS_IS` | 字段命名策略 |
| `fileCharset` | UTF-8 | 文件/流读写字符集 |
| `atomicFileWrite` | `false` | 原子写文件 |
| `createParentDirectories` | `false` | 自动创建父目录 |
| `maxInputSize` | `0` | 单次输入最大字节数 |
| `maxStringLength` | `0` | 字符串最大长度（Token 路径在生成 Token 时校验） |
| `maxNodeDepth` | `0` | Node 树最大深度（Token 路径同样生效） |
| `maxNodeCount` | `0` | Node 树最大节点数（Token 路径同样生效） |
| `resolveReferences` / `writeReferences` | `false` | 本地 `$ref` |
| `remoteReferenceResolver` | `null` | 远程 `$ref` 解析器 |

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.format.properties.PropertiesMapper

val mapper = PropertiesMapper(
    DocumentMapperConfig.builder()
        .fileCharset(Charsets.UTF_8)
        .atomicFileWrite(true)
        .maxNodeDepth(64)
        .build(),
)
```

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `PropertiesMapper()` | 默认配置 |
| `PropertiesMapper(config: DocumentMapperConfig)` | 通用配置 |

`PropertiesMapper.builder()` 返回 `PropertiesMapperBuilder`：

```kotlin
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.format.properties.PropertiesMapper

val mapper = PropertiesMapper.builder()
    .config(
        com.mingliqiye.document.serialization.DocumentMapperConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.FAIL)
            .build(),
    )
    .build()
```

## 读取 API

### Mapper 入口

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType)` | 原生 Token 路径：`PropertiesTokenReader` 直接生成 Token 流解码 Bean |
| `parse(source: ByteArray, targetType)` | 按 `fileCharset` 解码成文本后解析 |
| `parse(file / path, targetType)` | 文件 → 实体 |
| `parse(input: InputStream, targetType)` | 流 → 实体 |
| `parse(reader: Reader, targetType)` | 字符流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |
| `fromTokens(reader, targetType)` | 自定义 Token 读取器 → 实体 |

### 独立读取器

```kotlin
import com.mingliqiye.document.format.properties.PropertiesReader

val node = PropertiesReader("""
    name=小于
    age=18
""".trimIndent()).read()

// 静态便捷方法
val node2 = PropertiesReader.parse("a.b=1")

// 扩展函数
val node3 = "a.b=1".toPropertiesNode()
```

| 类/函数 | 说明 |
|---|---|
| `PropertiesReader(source)` | 读取器；`read(): Node` |
| `PropertiesReader.parse(source)` | 静态解析 |
| `String.toPropertiesNode()` | 字符串 → Node |

读取细节：

- 使用 JDK `Properties.load`，因此 `\uXXXX`、`\n`、行续接等 Properties 语法全部生效；
- key 先按字典序排序，再逐个插入路径树（因此输出顺序与文件无关）；
- 空输入返回空 `ObjectNode`；
- 值的类型推断顺序：`true` → `BooleanNode(true)`、`false` → `BooleanNode(false)`、可解析为 `BigDecimal` → `NumberNode`（保留原文）、否则 `StringNode`；
- 数组空洞（例如只有 `tags[1]`）会填充为空对象节点（`ObjectNode.EMPTY`）；
- 路径冲突（如同时出现 `a=1` 与 `a.b=2`）抛 `PropertiesParseException`。

### Token 路径

`PropertiesMapper.parse(String)` 使用内部 `PropertiesTokenReader`：

- 直接生成 `START_OBJECT` / `FIELD_NAME` / 标量 / `END_OBJECT` 等 Token，不构建完整 Node 树；
- 解析语义与 `PropertiesReader` 一致：key 先排序、`.` / `[index]` 还原、百分号转义、值类型推断、冲突路径报错；
- `maxNodeDepth` / `maxNodeCount` / `maxStringLength` 在生成 Token 时校验，超限抛 `DocumentSerializationException`；
- Token 路径无法校验的只有 Node 树循环检测（扁平原生结构不可能是环）；
- `maxInputSize` 仍由基类 `parse` 在创建 Token 读取器前校验。

需要自定义 Token 来源时，实现 `DocumentTokenReader` 后调用 `DocumentMapper.fromTokens(...)`。

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → Properties 文本 |
| `formatPretty(value)` | 与 `format` 输出相同（Properties 没有 pretty 概念） |
| `formatBytes(value)` | 实体 → 按 `fileCharset` 编码的字节 |
| `format(value, file / path)` | 写文件 |
| `format(value, output: OutputStream)` | 先编码为字节再写出（内部无流式 Token 写出器） |
| `format(value, writer: Writer)` | 直接写字符流 |
| `Node.toPropertiesString()` | 扩展函数：Node → Properties 文本 |

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `format(value, OutputStream)` | ❌ | 先构造完整 `String` 再按字符集编码 |
| `format(value, File/Path)` | ❌ | 同上 |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

写出规则：

- 根节点必须是 `ObjectNode` 或 `ArrayNode`，否则抛 `PropertiesException`；
- 对象字段用 `.` 连接路径；数组元素用 `[index]`；
- `NullNode` 写成空值（`path=`），读取回来是 `StringNode("")`（不保型）；
- `BinaryNode` 写成 Base64 字符串，读取回来是 `StringNode`（不保型）；
- 字段名中的 `%` `.` `[` `]` 分别编码为 `%25` `%2E` `%5B` `%5D`；
- key 中的 `\`、空格、`=`、`:`、`#`、`!` 与控制字符按 Properties 规则转义；
- 值首字符为空格或包含控制字符时转义为 `\uXXXX` 等。

```kotlin
import com.mingliqiye.document.format.properties.toPropertiesString
import com.mingliqiye.document.core.Node

val node = mapper.parse("""
    user.name=小于
    user.tags[0]=a
    user.tags[1]=b
""".trimIndent(), Node::class.java)

println(node.toPropertiesString())
// user.name=小于
// user.tags[0]=a
// user.tags[1]=b
```

## 路径与转义规则

```text
user.name=小于                -> {"user":{"name":"小于"}}
user.tags[0]=a                -> {"user":{"tags":["a"]}}
matrix[0][1]=2                -> {"matrix":[[null, 2]]}（空洞为 EMPTY 对象节点）
[0].name=小于                 -> [{"name":"小于"}]（根为数组）
a.b%2Ec=1                     -> {"a":{"b.c":1}}（%2E 转义点号）
a%5B0%5D=1                    -> {"a[0]":1}（%5B/%5D 转义方括号）
```

| 特殊字符 | 转义写法 |
|---|---|
| `%` | `%25` |
| `.` | `%2E` |
| `[` | `%5B` |
| `]` | `%5D` |

写出时自动应用上述转义，因此 `Node` 中带点号/方括号的字段名可以无损往返。

## 类型映射

| Node | Properties 读取 | Properties 写出 |
|---|---|---|
| `ObjectNode` | `.` 分段的嵌套对象 | 递归展开为 `.` 路径 |
| `ArrayNode` | `[index]` 下标；空洞填 `ObjectNode.EMPTY` | 递归展开为 `[index]` 路径 |
| `StringNode` | 无法推断为其它类型的文本 | 值文本（按需转义） |
| `NumberNode` | 可解析为 `BigDecimal` 的文本，保留原文 | `NumberNode.raw` |
| `BooleanNode` | `true` / `false` | `true` / `false` |
| `NullNode` | 不会从文本读出空值节点（空值读成 `StringNode("")`） | 空值（`path=`） |
| `BinaryNode` | 不会从文本读出（Base64 文本读成 `StringNode`） | Base64 字符串 |

## 示例

### 1. 字符串 ↔ Bean

```kotlin
import com.mingliqiye.document.format.properties.PropertiesMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = PropertiesMapper()
val user = mapper.parse("""
    name=小于
    age=18
""".trimIndent(), User::class.java)

println(mapper.format(user))
// age=18
// name=小于
```

### 2. Node 树读取

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse("""
    user.name=小于
    user.tags[0]=a
    user.tags[1]=b
""".trimIndent(), Node::class.java)

val userNode = node.asObject()["user"]?.asObject()
println(userNode?.get("name")?.asString())        // 小于
println(userNode?.get("tags")?.asArray()?.size)   // 2
```

### 3. 嵌套对象 + 数组

```kotlin
val text = """
    server.host=127.0.0.1
    server.port=8080
    server.allow[0]=user
    server.allow[1]=admin
"""

val node = mapper.parse(text, Node::class.java)
println(node.asObject()["server"]?.asObject()?.get("port")?.asNumber()?.toInt())
```

### 4. 百分号转义

```kotlin
val node = mapper.parse("""
    a.b%2Ec=1
    a%5B0%5D=2
""".trimIndent(), Node::class.java)

println(node.asObject()["a"]?.asObject()?.get("b.c")?.asNumber()?.toInt())  // 1
println(node.asObject()["a[0]"]?.asNumber()?.toInt())                        // 2
```

### 5. 根为数组

```kotlin
val node = mapper.parse("""
    [0].name=小于
    [1].name=阿明
""".trimIndent(), Node::class.java)

check(node.isArray)
println(node.asArray().size)   // 2
```

### 6. 数组空洞

```kotlin
val node = mapper.parse("tags[2]=c", Node::class.java)
val tags = node.asObject()["tags"]?.asArray()
check(tags?.size == 3)
check(tags?.get(0)?.isObject == true)   // 空洞填充为空对象节点
```

### 7. 路径冲突

```kotlin
import com.mingliqiye.document.format.properties.exception.PropertiesParseException

val error = runCatching {
    mapper.parse("a=1\na.b=2", Node::class.java)
}.exceptionOrNull()
check(error is PropertiesParseException)
```

### 8. null 与二进制不保型

```kotlin
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode

val node = ObjectNode.of(
    "empty" to NullNode.INSTANCE,
    "data" to BinaryNode.of(byteArrayOf(1, 2, 3)),
)
val text = mapper.format(node)      // empty= / data=AQID（Base64）
val restored = mapper.parse(text, Node::class.java)
check(restored.asObject()["empty"]?.isString == true)   // 读回空字符串
check(restored.asObject()["data"]?.isString == true)    // 读回 Base64 字符串
```

### 9. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.format(user, File("build/user.properties"))
val fromFile = mapper.parse(File("build/user.properties"), User::class.java)

mapper.format(user, Paths.get("build/user2.properties"))
val fromPath = mapper.parse(Paths.get("build/user2.properties"), User::class.java)
```

### 10. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

val output = ByteArrayOutputStream()
mapper.format(user, output)
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)
```

### 11. 字节数组读写

```kotlin
val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
check(restored.age == 18)
```

### 12. Java 示例

```java
import com.mingliqiye.document.format.properties.PropertiesMapper;

public class PropertiesDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        PropertiesMapper mapper = new PropertiesMapper();

        User user = new User();
        user.name = "小于";
        user.age = 18;

        String text = mapper.format(user);
        User restored = mapper.parse(text, User.class);
        System.out.println(restored.name + ":" + restored.age);
    }
}
```

### 13. 自定义字符集

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.format.properties.PropertiesMapper
import java.nio.charset.Charset

// Mapper 的文件/流读写按 fileCharset 编解码（默认 UTF-8）
val gbkMapper = PropertiesMapper(
    DocumentMapperConfig.builder()
        .fileCharset(Charset.forName("GBK"))
        .build(),
)

val bytes = gbkMapper.formatBytes(user)               // GBK 编码
val restored = gbkMapper.parse(bytes, User::class.java)
```

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 空文本 | 返回空 `ObjectNode` |
| 根节点不是对象/数组（写出） | `PropertiesException("Properties root must be an object or array")` |
| key 为空、以 `.` 结尾、`..`、未闭合 `[`、非法 `%` 转义 | `PropertiesParseException` |
| 路径同时是对象与标量/数组 | `PropertiesParseException("Conflicting Properties key path ...")` |
| 数组空洞 | 以空 `ObjectNode` 填充 |
| 值中的 `true`/`false`/数字文本 | 读取时推断为对应节点类型（无法强制保持字符串） |
| 需要保持字符串 `"true"` 的语义 | 用 Schema 或转换器处理（Properties 本身无类型标记） |
| `NullNode` / `BinaryNode` 往返 | 不保型：读回空字符串 / Base64 字符串 |
| 非 ASCII 文本 | 文件/流默认 UTF-8；如需 ISO-8859-1 语义自行管理编码 |

## 与通用 `DocumentMapper` 的差异

- 没有格式专属 Config 类，直接使用 `DocumentMapperConfig`；
- `parse(String)` 走原生 `PropertiesTokenReader`（不构建 Node 树），Token 生成时同时执行 Node 限制校验；
- `formatPretty` 与 `format` 输出一致（Properties 没有格式化概念）；
- Properties 专属：`PropertiesReader`、`String.toPropertiesNode()`、`Node.toPropertiesString()`；
- 文本格式语义：`parse(ByteArray)` / `formatBytes` 按 `fileCharset` 编解码。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [Token 与流式读写](../guide/token-stream.md)
- [API 参考](../reference/api.md)
- [format-properties 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-properties/README.md)
