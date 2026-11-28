# format-xml

XML 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:xml:2.5.1")
```

## 主要 API

```text
XmlMapper / XmlMapperBuilder
XmlReader / XmlTokenReader（内部，默认配置 Token 直读）
XmlConfig / XmlConfigBuilder
XmlReadFeature / XmlWriteFeature（对齐 参考实现 XML）
XmlNamespaceMode
XmlMixedContentPolicy
XmlNameProcessor / XmlNameProcessors
```

## 示例

```kotlin
import com.mingliqiye.document.format.xml.XmlMapper

val mapper = XmlMapper()
val xml = mapper.format(user)
val restored = mapper.parse(xml, User::class.java)
```

## 属性与命名空间

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlNamespaceMode

val mapper = XmlMapper(
    XmlConfig.builder()
        .attributeMode(true)
        .namespaceMode(XmlNamespaceMode.CLARK)
        .build(),
)
```

## 参考实现 XML 特性（默认对齐 参考实现 XML 3.x）

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.XmlReadFeature
import com.mingliqiye.document.format.xml.XmlWriteFeature

// 写出 XML 声明（编码取自 fileCharset，默认 UTF-8）
val declared = XmlMapper(
    XmlConfig.builder()
        .enable(XmlWriteFeature.WRITE_XML_DECLARATION)
        .build(),
)

// 切换为 参考实现 XML 2.x 默认值（关闭 3.x 相比 2.x 默认开启的 5 个特性）
val 参考实现2 = XmlMapper(XmlConfig.builder().legacy2Defaults().build())
```

| 特性 | 默认（3.x） | 说明 |
|---|---|---|
| `XmlReadFeature.AUTO_DETECT_XSI_TYPE` | 开 | 按 `xsi:type` 映射 XSD 标量类型 |
| `XmlReadFeature.EMPTY_ELEMENT_AS_NULL` | 关 | `<age/>` 读为 null |
| `XmlReadFeature.PROCESS_XSI_NIL` | 开 | 识别 `xsi:nil="true"` |
| `XmlWriteFeature.WRITE_XML_DECLARATION` | 关 | 输出 XML 声明 |
| `XmlWriteFeature.WRITE_XML_1_1` | 关 | XML 1.1 声明与控制字符字符引用 |
| `XmlWriteFeature.WRITE_STANDALONE_YES_TO_XML_DECLARATION` | 关 | 声明中加 `standalone="yes"` |
| `XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL` | 开 | null 写 `xsi:nil="true"` |
| `XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE` | 开 | 单字段根对象用字段名作根元素（仅 Node 树写出） |
| `XmlWriteFeature.AUTO_DETECT_XSI_TYPE` | 开 | 标量类型推断会失真时写 `xsi:type` |
| `XmlWriteFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS` | 开 | `INF` / `-INF` / `NaN` |

## Builder 选项

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlNameProcessors

val mapper = XmlConfig.builder()
    .defaultUseWrapper(false)          // 数组字段不使用包装元素
    .nameForTextElement("value")       // 文本伪字段名（等价 textKey）
    .nameProcessor(XmlNameProcessors.base64())   // XML 名称处理
    .rootName("user")
    .build()
```

- `defaultUseWrapper(boolean)`：默认 `true`；`false` 时集合写成 `<tags>a</tags><tags>b</tags>`。
- `nameForTextElement(String)`：对齐 参考实现 Builder 的文本元素名，本库等价 `textKey`。
- `nameProcessor(XmlNameProcessor)`：`passthrough`（默认）/`replacement`/`base64`/`alwaysOnBase64`。
- `rootName(String)` / 类上 `@DocumentRootName`。
- 通用配置便捷方法：`namingStrategy`、`fileCharset`、`atomicFileWrite`、`createParentDirectories`、
  `maxInputSize`、`maxStringLength`、`maxNodeDepth`、`maxNodeCount`、`resolveReferences`、
  `writeReferences`、`remoteReferenceResolver`、`beanValidator`、`failOnNullForPrimitives`。

## 深度限制（maxDepth）

默认解析上限为 512 层（根元素深度为 `1`），防止恶意超深 XML 耗尽调用栈：

```kotlin
import com.mingliqiye.document.format.xml.XmlConfig
import com.mingliqiye.document.format.xml.XmlMapper

// 默认 XmlConfig.DEFAULT_MAX_DEPTH = 512
val strict = XmlMapper(XmlConfig.builder().maxDepth(64).build())

// 独立读取器入口，默认同样是 512
val node = com.mingliqiye.document.format.xml.XmlReader(source, maxDepth = 64).read()
```

- `XmlConfig.maxDepth`（必须为正数，默认 `XmlConfig.DEFAULT_MAX_DEPTH = 512`）与通用
  `DocumentMapperConfig.maxNodeDepth` 同时设置时取较小值；
- Node / Token / Bean 三条解析路径都会在超限时抛出
  `com.mingliqiye.document.format.xml.exception.XmlParseException`；
- 深度校验使用显式栈，超深输入不会 `StackOverflowError`；
- `XmlReader` / `XmlTokenReader` 的 `maxDepth` 显式传 `0` 或负数表示关闭保护。

## 特性

- 参考实现 XML 读/写特性：XML 声明、XML 1.1、standalone、xsi:nil、xsi:type、单字段根展开、
  XML Schema 浮点词法（默认对齐 参考实现 3.x，`legacy2Defaults()` 切 2.x）
- null 默认写为 `xsi:nil="true"`，可用 `disable(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL)` 转为空元素
- `<age/>` 默认读取为空字符串，可用 `enable(XmlReadFeature.EMPTY_ELEMENT_AS_NULL)` 读取为 null
- `xsi:nil` 默认处理，可用 `disable(XmlReadFeature.PROCESS_XSI_NIL)` 忽略
- `xsi:type` 默认按 XSD 标量类型自动识别/按需写出（Node 模型无多态类信息，见文档）
- `type` 协议默认不输出，可用 `typeAttributes(true)` 开启
- XML 属性映射
- `PREFIX` / `CLARK` 命名空间
- 混合内容 `PRESERVE` / `PRESERVE_ORDERED` / `REJECT`
- `PRESERVE_ORDERED` 使用 `#content` 数组保留文本与子元素顺序
- 集合包装开关 `defaultUseWrapper`、文本元素名 `nameForTextElement`
- XML 名称处理 `XmlNameProcessor`（passthrough / replacement / base64）
- 解析深度限制（默认 512，可配置）
- 默认配置原生 XML Token Reader
- XXE 防护
- Token 直读 Bean

## 限制

- 默认混合内容模式不保留文本与子元素相对顺序
- Clark 模式的所有声明集中到根元素
- `UNWRAP_ROOT_OBJECT_NODE` 只对“以 Node 树显式写出”生效（Bean 序列化保持根元素）；
  直接写 `ObjectNode` 单字段时根元素名会变成该字段名
- `defaultUseWrapper(false)` 时单元素数组与标量在 XML 上不可区分（Bean 反序列化按目标类型还原）
- `xsi:type` 只做 XSD 标量类型映射，不做 参考实现 多态子类解析；XML 1.1 的控制字符字符引用
  不能被 JDK 默认 XML 1.0 解析器回读

## 构建

```bash
gradle :format-xml:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
