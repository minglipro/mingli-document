# serialization

Java Bean / Kotlin 实体与文档树之间的通用映射模块。

格式无关，具体格式由 `format-*` 模块注入。

## 主要能力

- `DocumentMapper` / `DocumentMapperApi`
- `DocumentMapperBuilder`
- `DocumentMapperConfig`
- 字段转换器、日期格式、数字格式
- Bean 元数据缓存与访问器
- `DocumentTokenReader` / `DocumentTokenWriter`
- `BinaryDocumentMapper`
- `NodeTokenReader`

## Maven

```kotlin
implementation("com.mingliqiye.document:serialization:1.0.0")
```

## 主要 API

```kotlin
mapper.toNode(value)
mapper.fromNode(node, User::class.java)
mapper.parse(text, User::class.java)
mapper.parse(bytes, User::class.java)
mapper.format(value)
mapper.formatBytes(value)
mapper.parse(file, User::class.java)
mapper.format(value, outputStream)
```

## 配置示例

```kotlin
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy

val config = DocumentMapperConfig.builder()
    .missingFieldPolicy(MissingFieldPolicy.FAIL)
    .failOnUnknownFields(true)
    .writeNulls(false)
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .build()
```

`PropertyNamingStrategy` 支持：

```text
AS_IS（默认）
SNAKE_CASE
KEBAB_CASE
UPPER_CAMEL
LOWER_CAMEL
```

`@DocumentName` 显式注解优先于命名策略。

## 转换器

实现 `JsonSerializer` 与/或 `JsonDeserializer`，通过：

- `DocumentMapperBuilder.addSerializer(...)`
- `DocumentMapperBuilder.addDeserializer(...)`
- `@DocumentConverter`
- 字段级配置

注册。

## 构建

```bash
gradle :serialization:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
