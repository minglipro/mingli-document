# format-xml

XML 格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:xml:1.3.0")
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

## 特性

- `type` / `nil` 协议
- XML 属性映射
- `PREFIX` / `CLARK` 命名空间
- 混合内容 `PRESERVE` / `PRESERVE_ORDERED` / `REJECT`
- `PRESERVE_ORDERED` 使用 `#content` 数组保留文本与子元素顺序
- 解析深度限制
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
