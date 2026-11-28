# format-xml

XML 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:xml:2.5.1")
```

## 主要 API

```text
XmlMapper
XmlReader
XmlConfig
XmlNamespaceMode
XmlMixedContentPolicy
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

- null 默认写为 `xsi:nil="true"`，可用 `disable(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL)` 转为空元素
- `<age/>` 默认读取为空字符串，可用 `enable(XmlReadFeature.EMPTY_ELEMENT_AS_NULL)` 读取为 null
- `xsi:nil` 默认处理，可用 `disable(XmlReadFeature.PROCESS_XSI_NIL)` 忽略
- `type` 协议默认不输出，可用 `typeAttributes(true)` 开启
- XML 属性映射
- `PREFIX` / `CLARK` 命名空间
- 混合内容 `PRESERVE` / `PRESERVE_ORDERED` / `REJECT`
- `PRESERVE_ORDERED` 使用 `#content` 数组保留文本与子元素顺序
- 解析深度限制（默认 512，可配置）
- 默认配置原生 XML Token Reader
- XXE 防护
- Token 直读 Bean

## 限制

- 默认混合内容模式不保留文本与子元素相对顺序
- Clark 模式的所有声明集中到根元素

## 构建

```bash
gradle :format-xml:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
