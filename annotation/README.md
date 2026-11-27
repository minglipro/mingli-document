# annotation

格式无关的文档注解读模块。

注解位于：

```text
com.mingliqiye.document.annotation
```

## 注解

| 注解 | 作用 |
|---|---|
| `@DocumentName("name")` | 指定文档字段名 |
| `@DocumentIgnore` | 忽略字段 |
| `@DocumentConverter(XxxConverter::class)` | 字段专用转换器 |
| `@DocumentDateTimeFormat(pattern, timezone)` | 日期时间格式 |
| `@DocumentNumberFormat(pattern, locale)` | 数字格式 |
| `@DocumentRootName("root")` | XML 根元素名 |

## Maven

```kotlin
implementation("com.mingliqiye.document:annotation:1.0.0")
```

## 示例

```kotlin
import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName

class User {
    @field:DocumentName("display_name")
    var name: String = ""

    @field:DocumentIgnore
    var internalId: Long = 0
}
```

注解不依赖任何具体格式；由 `serialization` 和各 `format-*` 模块读取。

## 构建

```bash
gradle :annotation:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
