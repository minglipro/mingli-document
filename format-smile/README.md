# format-smile

Smile 二进制格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:smile:1.2.0")
```

## 主要 API

```text
SmileMapper
SmileReader
SmileWriter
SmileTokenReader
SmileTokenWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.smile.SmileMapper

val mapper = SmileMapper()
val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
```

## 文件/流

```kotlin
mapper.format(user, file)
val restored = mapper.parse(file, User::class.java)
```

## 解析深度

```kotlin
SmileReader(bytes, maxDepth = 64).read()
```

`SmileMapper(DocumentMapperConfig.builder().maxNodeDepth(64).build())` 的 Node 回退路径也会限制深度。

## 特性

- Smile 二进制
- Token 直读/直写
- `BinaryNode` 使用 Jackson Smile embedded binary
- 文件/流读写

## 限制

- 单根单文档
- 二进制值依赖 Jackson Smile 扩展
- 非有限浮点在 Node 层即被拒绝

## 构建

```bash
gradle :format-smile:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
