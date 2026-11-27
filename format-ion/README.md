# format-ion

Amazon Ion 格式模块，支持文本 Ion 与二进制 Ion。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:ion:1.1.0")
```

## 主要 API

```text
IonMapper
IonReader
IonWriter
IonBinaryReader
IonBinaryWriter
IonTokenReader
IonBinaryTokenWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.ion.IonMapper

val mapper = IonMapper()

// 文本
val text = mapper.format(user)
val a = mapper.parse(text, User::class.java)

// 二进制
val bytes = mapper.formatBytes(user)
val b = mapper.parse(bytes, User::class.java)
```

## 类型映射

```text
BLOB / CLOB -> BinaryNode
TIMESTAMP   -> StringNode
SYMBOL      -> StringNode
LIST / SEXP -> ArrayNode
STRUCT      -> ObjectNode
```

## 特性

- 文本 Ion
- 二进制 Ion
- Token 直读 Bean
- 文件/流读写

## 限制

- timestamp/symbol 写回为字符串
- Ion 注解忽略
- CLOB 类型信息不保留

## 构建

```bash
gradle :format-ion:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
