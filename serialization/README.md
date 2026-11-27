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
    .fileCharset(Charsets.UTF_8)
    .atomicFileWrite(true)
    .createParentDirectories(true)
    .maxInputSize(16L * 1024 * 1024)
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

文件读写选项：

```text
fileCharset             文本文件/流字符集，默认 UTF-8
atomicFileWrite         临时文件 + 原子替换
createParentDirectories 自动创建父目录
maxInputSize            单次输入最大字节数，0 表示不限制
```

## 转换器

实现 `DocumentBaseSerializer` 与/或 `DocumentBaseDeserializer`，通过：

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
