---
title: XML
description: format-xml 模块：XmlMapper、XmlConfig、type/nil 协议、属性映射、PREFIX/CLARK 命名空间、混合内容策略与 XXE 防护
---

# XML

`format-xml` 提供 XML 读写，核心类型是 `XmlMapper`。

XML 没有原生类型信息，模块可选择用 `type` / `nil` 协议保存节点类型；`type` 默认不输出，
属性、命名空间与混合内容通过 `XmlConfig` 按需启用。

## Maven 坐标与依赖

```kotlin
implementation("com.mingliqiye.document.format:xml:2.5.1")
```

| 项 | 值 |
|---|---|
| groupId | `com.mingliqiye.document.format` |
| artifactId | `xml` |
| 版本 | `2.5.1` |
| 传递依赖 | `core`、`exception`、`serialization`；`annotation`（实现依赖） |
| 第三方依赖 | 无（使用 JDK `javax.xml.parsers.DocumentBuilderFactory` DOM） |
| 字节码 | JDK 8 |
| 包名 | `com.mingliqiye.document.format.xml` |

## 特性

- **`type` / `nil` 协议**：null 默认输出 `xsi:nil="true"`；用 `typeAttributes(true)` 开启后元素上额外输出
  `type="object|array|string|number|boolean|binary"`，保证非字符串标量严格无损往返；
- **属性映射**：启用后 XML attribute 映射为对象字段 `attributePrefix + localName`（默认 `@id`），写出时把带前缀的标量字段写成属性；
- **命名空间**：`PREFIX`（`prefix:local` + `xmlns` 属性字段）与 `CLARK`（`{uri}local`，写出自动分配前缀）两种模式；
- **混合内容策略**：`PRESERVE`（默认，文本进 `#text`）/ `PRESERVE_ORDERED`（额外 `#content` 有序数组）/ `REJECT`；
- **XXE 防护**：禁用 DOCTYPE、外部通用/参数实体、XInclude，关闭实体引用展开；
- **解析深度限制**：`maxNodeDepth` 限制 DOM 嵌套深度；
- **原生 Token 路径**：默认读取配置下 `parse(String)` 使用内部 `XmlTokenReader` 直接从 DOM 生成 Token，不构建 Node 树；
- **文件/流读写**：`OutputStream` 直接流式写出；
- **根元素名可配置**：`XmlConfig.rootName` 或类上的 `@DocumentRootName`。

## 配置：`XmlConfig`

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `rootName` | `String` | `"root"` | 写出时的根元素名；类上 `@DocumentRootName` 优先 |
| `attributePrefix` | `String?` | `null` | 属性字段前缀；`null` 表示不启用属性映射 |
| `attributeMode`（派生） | `Boolean` | `false` | 等价于 `attributePrefix != null` |
| `namespaceAware` | `Boolean` | `false` | 是否启用命名空间感知（前缀限定名模式） |
| `namespaceMode` | `XmlNamespaceMode` | `PREFIX` | `PREFIX` / `CLARK`；`CLARK` 强制启用 `namespaceAware` |
| `clarkNames`（派生） | `Boolean` | `false` | 是否 Clark 名称模式 |
| `textKey` | `String` | `"#text"` | 混合内容直接文本字段名；也用于标量属性包装对象的文本字段 |
| `itemsKey` | `String` | `"#items"` | 带属性数组元素的数组内容字段名 |
| `contentKey` | `String` | `"#content"` | 有序混合内容数组字段名 |
| `mixedContentPolicy` | `XmlMixedContentPolicy` | `PRESERVE` | 混合内容策略 |

常量：`XmlConfig.DEFAULT_ATTRIBUTE_PREFIX = "@"`、`DEFAULT_TEXT_KEY = "#text"`、`DEFAULT_ITEMS_KEY = "#items"`、`DEFAULT_CONTENT_KEY = "#content"`。

`XmlConfigBuilder` 方法：

| 方法 | 说明 |
|---|---|
| `field(Class, String, DocumentBaseConverter)` | 字段转换器 |
| `missingFieldPolicy` / `failOnUnknownFields` / `writeNulls` | 通用 Bean 映射选项 |
| `rootName(String)` | 根元素名 |
| `attributePrefix(String)` | 启用属性映射并指定前缀（不能为空） |
| `attributeMode(Boolean)` | 开关属性映射；开启时默认前缀 `@` |
| `namespaceAware(Boolean)` | 开关命名空间感知 |
| `namespaceMode(XmlNamespaceMode)` | `PREFIX` / `CLARK` |
| `textKey(String)` / `itemsKey(String)` / `contentKey(String)` | 自定义保留字段名（不能为空） |
| `mixedContentPolicy(XmlMixedContentPolicy)` | 混合内容策略 |
| `typeAttributes(Boolean)` | 是否输出 `type="..."` 协议属性，默认 `false` |
| `enable/disable/configure(XmlReadFeature)` | Jackson 风格读取特性：`EMPTY_ELEMENT_AS_NULL`、`PROCESS_XSI_NIL` |
| `enable/disable/configure(XmlWriteFeature)` | Jackson 风格写出特性：`WRITE_NULLS_AS_XSI_NIL` |
| `subtype(...)` / `typeProperty(...)` | 多态子类型 |
| `resolveReferences` / `writeReferences` | 本地 `$ref` |
| `build()` | 构建 `XmlConfig` |

`build()` 校验：

- `textKey`、`itemsKey`、`contentKey` 互不相同；
- 三个保留键都不能以 `attributePrefix` 开头；
- 开启 `namespaceMode(CLARK)` 会强制 `namespaceAware = true`；
- 开启命名空间感知且未显式配置属性映射时，自动启用属性映射（默认前缀 `@`），以便保留 `xmlns` 声明。

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlNamespaceMode

val mapper = XmlMapper(
    XmlConfig.builder()
        .rootName("user")
        .attributeMode(true)                       // 属性映射，前缀 @
        .namespaceMode(XmlNamespaceMode.CLARK)     // Clark 名称模式
        .build(),
)
```

## Mapper 构造与 Builder

| 构造器 | 说明 |
|---|---|
| `XmlMapper()` | 默认：根名 `root`、无属性映射、无命名空间感知、`PRESERVE` 混合内容 |
| `XmlMapper(config: XmlConfig)` | XML 配置 |
| `XmlMapper(config: DocumentMapperConfig)` | 通用配置（此时 XML 专属项全部取默认值） |

`XmlMapper.builder()` 返回 `XmlMapperBuilder`，用法与其他格式一致：

```kotlin
val mapper = XmlMapper.builder()
    .config(XmlConfig.builder().rootName("user").build())
    .build()
```

## 读取 API

### Mapper 入口

| 方法 | 说明 |
|---|---|
| `parse(source: String, targetType)` | 默认配置走原生 `XmlTokenReader`；非默认读取配置/Node 限制/`resolveReferences` 时回退 Node 路径 |
| `parse(source: ByteArray, targetType)` | 按 `fileCharset` 解码成文本后解析 |
| `parse(file / path, targetType)` | 文件 → 实体 |
| `parse(input: InputStream, targetType)` | 流 → 实体 |
| `parse(reader: Reader, targetType)` | 字符流 → 实体 |
| `fromNode(node, targetType)` | Node → 实体 |

原生 Token 路径的判断条件（`XmlMapper.usesNativeTokenReader`）：

- 未配置 `maxNodeDepth` / `maxNodeCount` / `maxStringLength`；
- 未配置 `resolveReferences`；
- `XmlConfig` 为 `null`，或读取配置为默认：无属性映射、无命名空间感知、`PREFIX` 模式、默认 `textKey` / `itemsKey` / `contentKey`、`PRESERVE` 混合内容。

否则回退 `NodeTokenReader`，保证属性、命名空间、有序混合内容等语义。

### 独立读取器

`XmlReader` 可以完全控制读取选项：

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMixedContentPolicy
import com.mingliqiye.document.format.xml.XmlNamespaceMode
import com.mingliqiye.document.format.xml.XmlReader

val reader = XmlReader(
    source = """<root type="object"><name type="string">小于</name></root>""",
    attributePrefix = null,                          // 或 "@"
    namespaceAware = false,
    namespaceMode = XmlNamespaceMode.PREFIX,
    textKey = XmlConfig.DEFAULT_TEXT_KEY,
    itemsKey = XmlConfig.DEFAULT_ITEMS_KEY,
    mixedContentPolicy = XmlMixedContentPolicy.PRESERVE,
    contentKey = XmlConfig.DEFAULT_CONTENT_KEY,
    maxDepth = 0,
)

val node = reader.read()

// 默认参数版本 + 扩展函数
val plain = XmlReader(source).read()
val extended = """<root><a type="number">1</a></root>""".toXmlNode()
```

| 类/函数 | 说明 |
|---|---|
| `XmlReader(source, attributePrefix=null, namespaceAware=false, namespaceMode=PREFIX, textKey="#text", itemsKey="#items", mixedContentPolicy=PRESERVE, contentKey="#content", maxDepth=0)` | 读取器；`read(): Node` |
| `XmlReader.parse(source)` | 静态解析（默认配置） |
| `String.toXmlNode()` | 字符串 → Node 扩展函数 |

### `type` / `nil` 协议与结构推断

默认 `typeAttributes = false`，写出时不生成 `type`；开启后元素上使用：

| 属性 | 含义 |
|---|---|
| `type="object"` | 对象节点 |
| `type="array"` | 数组节点 |
| `type="string"` | 字符串 |
| `type="number"` | 数字（文本按 `BigDecimal` 校验） |
| `type="boolean"` | `true` / `false` |
| `type="binary"` | Base64 编码的 `BinaryNode` |
| `xsi:nil="true"` | `NullNode` |

读取没有类型属性时按元素结构推断（`xsi:nil="true"` 始终优先解释为 null）：

- 无子元素、无属性 → 标量推断：`true` / `false` / 数字 / 字符串；
- 子元素全部名为 `item` → 数组；
- 否则对象；
- 对象中同名子元素出现多次时合并为 `ArrayNode`（保持首次出现顺序）；
- 对象的直接文本（裁剪后非空）写入 `#text`；
- 数组容器带直接文本时输出 `{ "#items": 数组, "#text": 文本 }` 包装对象。

## 属性映射

启用属性映射后：

- XML attribute → 对象字段 `attributePrefix + localName`，例如 `id="7"` → `@id`；`type` / `nil` 是协议属性，不映射为普通字段；
- 带属性的标量元素读出为包装对象 `{ 属性字段..., "#text": 标量 }`；
- 带属性的数组元素读出为包装对象 `{ 属性字段..., "#items": 数组 }`；
- Bean 侧用 `@DocumentName("@id")`、`@DocumentName("#text")`、`@DocumentName("#items")` 接收。

写出规则：

- 对象的所有字段都是属性字段、且 `#text` 为标量 → 写成带属性的标量元素；
- 对象包含 `#items` 数组、其余字段为属性字段或 `#text` → 写成带属性的数组元素；
- 普通对象含 `#text` 标量字段 → 文本写在子元素之前（混合内容）；
- `null` 属性被跳过；属性字段的值是对象/数组时抛 `IllegalArgumentException`。

```kotlin
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper

class Product {
    @field:DocumentName("@id")
    var id: Int = 0

    var name: String = ""
}

val mapper = XmlMapper(XmlConfig.builder().attributePrefix("@").build())
val product = Product().apply {
    id = 7
    name = "键盘"
}

val xml = mapper.format(product)
// <root type="object" id="7"><name type="string">键盘</name></root>
val restored = mapper.parse(xml, Product::class.java)
```

## 命名空间：PREFIX 与 CLARK

### PREFIX 模式（默认）

- 保留 `prefix:local` 前缀限定名；
- `xmlns` 声明作为属性字段保留，例如 `@xmlns:ns`；
- 需要 `namespaceAware = true` 时才做命名空间感知解析。

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper

val mapper = XmlMapper(
    XmlConfig.builder()
        .namespaceAware(true)
        .build(),
)

val node = mapper.parse(
    """<root xmlns:ns="urn:example" type="object"><ns:name type="string">小于</ns:name></root>""",
    com.mingliqiye.document.core.Node::class.java,
)
println(node.asObject()["@xmlns:ns"]?.asString())   // urn:example
println(node.asObject()["ns:name"]?.asString())      // 小于
```

### CLARK 模式

- 元素/属性名使用 `{uri}local` 形式，例如 `{urn:example}name`；
- 读取时不保留 `xmlns` 声明字段（URI 已内嵌在名称中）；
- 写出时按首次出现顺序分配 `ns0`、`ns1`… 前缀，并在根元素生成 `xmlns:nsN` 声明。

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlNamespaceMode

val mapper = XmlMapper(
    XmlConfig.builder()
        .namespaceMode(XmlNamespaceMode.CLARK)
        .build(),
)

val node = ObjectNode.of("{urn:example}name" to StringNode.of("小于"))
println(mapper.format(node))
// <root type="object" xmlns:ns0="urn:example"><ns0:name type="string">小于</ns0:name></root>
```

元素名中不满足 XML 名称规则的字符会被编码为 `_xHHHH_`，读取时自动解码；Clark 名称按 `{uri}local` 解析。

## 混合内容

混合内容指元素同时包含直接文本与子元素。

| 策略 | 读取行为 | 写出行为 |
|---|---|---|
| `PRESERVE`（默认） | 直接文本首尾裁剪后写入 `#text`；与子元素相对顺序不保留 | 文本固定在子元素之前写出 |
| `PRESERVE_ORDERED` | 兼容视图（`#text` + 子元素字段）之外，额外输出 `#content` 有序数组：文本项为 `StringNode`，子元素项为单字段对象 `{元素名: 节点}`；相邻文本节点合并，纯空白文本忽略 | `#content` 存在时按数组顺序交错写出文本与子元素；此时忽略 `#text` 与普通子元素字段；数组元素项元素名固定 `item`；有序区域内不插入 pretty 空白 |
| `REJECT` | 读到非空白直接文本与子元素并存时抛 `XmlParseException` | — |

```kotlin
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlMixedContentPolicy

val xml = """<root type="object">前<a type="string">中</a>后</root>"""

// 默认 PRESERVE
val plain = XmlMapper().parse(xml, Node::class.java)
println(plain)   // #text=前后（顺序不保留）

// PRESERVE_ORDERED
val orderedMapper = XmlMapper(
    XmlConfig.builder()
        .mixedContentPolicy(XmlMixedContentPolicy.PRESERVE_ORDERED)
        .build(),
)
val ordered = orderedMapper.parse(xml, Node::class.java)
val content = ordered.asObject()["#content"]?.asArray()
println(content)          // [前, {a: 中}, 后]
println(orderedMapper.format(ordered))   // 按 #content 顺序写回

// REJECT
val rejecting = XmlMapper(
    XmlConfig.builder()
        .mixedContentPolicy(XmlMixedContentPolicy.REJECT)
        .build(),
)
val error = runCatching { rejecting.parse(xml, Node::class.java) }.exceptionOrNull()
check(error is com.mingliqiye.document.format.xml.exception.XmlParseException)
```

## 写出 API

| 方法 | 说明 |
|---|---|
| `format(value)` | 实体 → 紧凑 XML（单行） |
| `formatPretty(value)` | 实体 → 缩进 XML |
| `formatBytes(value)` | 实体 → 按 `fileCharset` 编码的字节 |
| `format(value, file / path)` | 写文件 |
| `formatPretty(value, file / path)` | 写 pretty 文件 |
| `format(value, output: OutputStream)` | 通过 `OutputStreamWriter` + `XmlWriter.writeTo` 直接写出 |
| `formatPretty(value, output: OutputStream)` | 直接写出 pretty |
| `format(value, writer: Writer)` / `formatPretty(value, writer)` | 写字符流 |

根元素名优先级：

1. 类上的 `@DocumentRootName("user")`；
2. `XmlConfig.rootName`；
3. 默认 `"root"`。

```kotlin
import com.mingliqiye.document.annotation.DocumentRootName

@DocumentRootName("account")
class Account {
    var name: String = ""
}

val xml = XmlMapper().format(Account().apply { name = "小于" })
// <account type="object"><name type="string">小于</name></account>
```

流式支持：

| 路径 | 是否流式 | 说明 |
|---|---|---|
| `format(value, OutputStream)` / `formatPretty(value, output)` | ✅ | Mapper 覆写，直接写 `Writer`（`XmlWriter.writeTo`） |
| `format(value, File/Path)` | ❌ | 先构造文本再写文件 |
| `format(value, Writer)` | ❌ | 先构造完整文本再写入字符流 |

## 类型映射

| Node | XML 读取 | XML 写出 |
|---|---|---|
| `ObjectNode` | 元素（`type="object"` 或结构推断） | `<name>...</name>`（开启协议时 `type="object"`） |
| `ArrayNode` | `type="array"` 或子元素全为 `item` | `<name><item .../></name>`（开启协议时 `type="array"`） |
| `StringNode` | `type="string"` 或标量推断 | 文本元素（开启协议时 `type="string"`） |
| `NumberNode` | `type="number"`（非法数字抛 `XmlParseException`）或标量推断 | 文本为 `NumberNode.raw`（开启协议时 `type="number"`） |
| `BooleanNode` | `type="boolean"`（非 true/false 抛异常）或标量推断 | `true` / `false` 文本（开启协议时 `type="boolean"`） |
| `NullNode` | `xsi:nil="true"`；空元素 + `EMPTY_ELEMENT_AS_NULL` | `<name xsi:nil="true"/>`；关闭 `WRITE_NULLS_AS_XSI_NIL` 时 `<name/>` |
| `BinaryNode` | `type="binary"`，Base64 解码 | Base64 文本（开启协议时 `type="binary"`） |
| 空元素 `<age/>` | 默认 `""`；启用 `EMPTY_ELEMENT_AS_NULL` 时为 null | 关闭 `WRITE_NULLS_AS_XSI_NIL` 时输出 `<age/>` |

补充规则：

- 属性字段值按标量推断（`true`/`false`/数字/字符串）；
- 默认输出没有 `type` 属性；读取侧按结构/文本推断，二进制和“看起来像数字的字符串”无法严格保真，需要无损往返时开启 `typeAttributes(true)`；
- `<age/>` 默认读取为字符串空值；显式 `xsi:nil="true"` 始终为 null；
- 关闭 `WRITE_NULLS_AS_XSI_NIL` 让 NullNode 写成空元素，配合 `EMPTY_ELEMENT_AS_NULL` 可保持 null 往返；
- 数字使用 `BigDecimal` 校验，写出时原样使用 `raw`；
- 元素/属性的非法名称字符编码为 `_xHHHH_`。

## 示例

### 1. Bean ↔ XML

```kotlin
import com.mingliqiye.document.format.xml.XmlMapper

class User {
    var name: String = ""
    var age: Int = 0
}

val mapper = XmlMapper()
val user = User().apply {
    name = "小于"
    age = 18
}

val xml = mapper.format(user)
println(xml)
val restored = mapper.parse(xml, User::class.java)
```

### 2. Node 树读写

```kotlin
import com.mingliqiye.document.core.Node

val node = mapper.parse(
    """<root type="object"><name type="string">小于</name><age type="number">18</age></root>""",
    Node::class.java,
)
println(node.asObject()["name"]?.asString())
println(node.asObject()["age"]?.asNumber()?.toInt())
```

### 3. 根元素名

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig

val mapper = XmlMapper(XmlConfig.builder().rootName("user").build())
println(mapper.format(user))   // 以 <user ...> 开头
```

### 4. 属性模式

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig

val mapper = XmlMapper(XmlConfig.builder().attributeMode(true).build())
val node = com.mingliqiye.document.core.ObjectNode.of(
    "@id" to com.mingliqiye.document.core.NumberNode.of(7),
    "name" to com.mingliqiye.document.core.StringNode.of("小于"),
)
println(mapper.format(node))
```

### 5. 文档根类型限制

```kotlin
// 根节点必须是可写的对象/数组/标量；直接写 NullNode 会得到 xsi:nil="true"
val node = mapper.parse("<root nil=\"true\"/>", Node::class.java)
check(node.isNull)
```

### 6. 文件读写

```kotlin
import java.io.File
import java.nio.file.Paths

mapper.formatPretty(user, File("build/user.xml"))
val fromFile = mapper.parse(File("build/user.xml"), User::class.java)

mapper.format(user, Paths.get("build/user-compact.xml"))
val fromPath = mapper.parse(Paths.get("build/user-compact.xml"), User::class.java)
```

### 7. 流读写

```kotlin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter

val output = ByteArrayOutputStream()
mapper.formatPretty(user, output)    // 直接流式写
val restored = mapper.parse(ByteArrayInputStream(output.toByteArray()), User::class.java)

val writer = StringWriter()
mapper.format(user, writer)          // 字符流
println(writer.toString())
```

### 8. 深度限制

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlReader
import com.mingliqiye.document.format.xml.exception.XmlParseException

// 读取器级别
val error = runCatching {
    XmlReader("""<root><a><b><c/></b></a></root>""", maxDepth = 2).read()
}.exceptionOrNull()
check(error is XmlParseException)

// Mapper 级别：配置 Node 限制后回退 Node 路径
val guarded = XmlMapper(
    XmlConfig.builder().maxNodeDepth(2).build(),
)
val mapperError = runCatching {
    guarded.parse("""<root><a><b><c/></b></a></root>""", Node::class.java)
}.exceptionOrNull()
println(mapperError?.message)
```

### 9. XXE 防护

```kotlin
import com.mingliqiye.document.format.xml.exception.XmlParseException

// DOCTYPE 被禁用，含 DOCTYPE 的文档直接报错
val xxe = """
<?xml version="1.0"?>
<!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
<root>&xxe;</root>
"""

val error = runCatching { mapper.parse(xxe, Node::class.java) }.exceptionOrNull()
check(error is XmlParseException)
```

### 10. 结构推断

```kotlin
import com.mingliqiye.document.core.Node

// 没有 type 属性时按元素结构推断
val node = mapper.parse("""<root><name>小于</name><age>18</age></root>""", Node::class.java)
println(node.asObject()["name"]?.asString())    // 小于
println(node.asObject()["age"]?.asNumber()?.toInt()) // 18

// 子元素全为 item -> 数组
val array = mapper.parse("""<root><item type="number">1</item><item type="number">2</item></root>""", Node::class.java)
check(array.isArray)
```

### 11. Java 示例

```java
import com.mingliqiye.document.format.xml.XmlConfig;
import com.mingliqiye.document.format.xml.XmlMapper;

public class XmlDemo {
    public static class User {
        public String name;
        public int age;
    }

    public static void main(String[] args) {
        XmlMapper mapper = new XmlMapper(
                XmlConfig.builder().rootName("user").build());

        User user = new User();
        user.name = "小于";
        user.age = 18;

        String xml = mapper.formatPretty(user);
        User restored = mapper.parse(xml, User.class);
        System.out.println(restored.name + ":" + restored.age);
    }
}
```

### 12. 命名空间属性读取

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper

val mapper = XmlMapper(XmlConfig.builder().namespaceAware(true).build())
val node = mapper.parse(
    """<root xmlns="urn:default" type="object"><name type="string">小于</name></root>""",
    com.mingliqiye.document.core.Node::class.java,
)
println(node.asObject()["@xmlns"]?.asString())   // urn:default
```

## 深度限制

```kotlin
val mapper = XmlMapper(
    XmlConfig.builder()
        .maxDepth(512)
        .build(),
)
```

默认 `512`；超过限制抛 `XmlParseException`，Node/Token/Bean 路径都生效。
显式传 `0` 表示关闭保护（不推荐）。

## 边界、限制与常见错误

| 场景 | 行为 |
|---|---|
| 默认混合内容模式 | 文本与子元素的相对顺序不保留（需要 `PRESERVE_ORDERED`） |
| `PRESERVE_ORDERED` 写出 | 有序区域内不加缩进，避免空白污染文本 |
| Clark 模式 | 所有 `xmlns` 声明集中到根元素；写出自动分配 `ns0`、`ns1`… |
| 属性字段值为对象/数组 | 写出时抛 `IllegalArgumentException` |
| 属性字段为 null | 写出时跳过 |
| 数字 `type="number"` 非法文本 | `XmlParseException` |
| 布尔值非 `true`/`false` | `XmlParseException` |
| DOCTYPE / 外部实体 / XInclude | 解析器禁用，抛 `XmlParseException` |
| 无根元素 / 格式非法 | `XmlParseException` |
| 深度超过 `maxDepth` | `XmlParseException` |
| `textKey` 等保留键冲突或前缀冲突 | `IllegalArgumentException`（Builder 校验） |
| `CLARK` 但 `namespaceAware=false` | `IllegalArgumentException`（`XmlReader` 构造校验） |
| 写 pretty 与混合内容 | `PRESERVE` 时文本固定在子元素之前；需要顺序请用 `PRESERVE_ORDERED` + `#content` |

## 与通用 `DocumentMapper` 的差异

- 构造：`XmlConfig` 专属配置类与 `XmlMapper.builder()`；
- `parse(String)`：默认配置下走内部 `XmlTokenReader`（不构建 Node 树），非默认读取配置回退 Node 路径；
- `format(value, OutputStream)` / `formatPretty(value, OutputStream)`：覆写为直接写字符流；
- 根元素名：`@DocumentRootName` > `XmlConfig.rootName` > `"root"`；
- XML 专属：`type` / `nil` 协议、属性映射、`XmlNamespaceMode`、`XmlMixedContentPolicy`、`XmlReader`；
- 文本格式语义：`parse(ByteArray)` / `formatBytes` 按 `fileCharset` 编解码。

## 相关链接

- [格式总览](./)
- [核心概念：Node 树](../guide/core-concepts.md)
- [文件与流 IO](../guide/io.md)
- [配置](../guide/configuration.md)
- [API 参考](../reference/api.md)
- [异常参考](../reference/exceptions.md)
- [format-xml 模块 README](https://github.com/minglipro/mingli-document/blob/dev/format-xml/README.md)
