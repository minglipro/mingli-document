# exception

文档库异常基类模块。

## 主要类型

```text
DocumentException
DocumentTypeException
```

## Maven

```kotlin
implementation("com.mingliqiye.document:exception:1.4.0")
```

## 说明

- `DocumentException` 继承 `com.mingliqiye.exception` 的异常体系
- 各格式模块的异常继承 `DocumentException`
- `DocumentTypeException` 用于类型不匹配场景

## 示例

```kotlin
import com.mingliqiye.document.exception.DocumentException

try {
    // 文档处理
} catch (e: DocumentException) {
    println(e.message)
}
```

## 构建

```bash
gradle :exception:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
