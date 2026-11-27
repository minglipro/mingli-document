# format-cbor

CBOR 二进制格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:cbor:1.0.0")
```

## 主要 API

```text
CborMapper
CborReader
CborWriter
CborTokenReader
CborTokenWriter
```

## 示例

```kotlin
import com.mingliqiye.document.format.cbor.CborMapper

val mapper = CborMapper()
val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
```

## 文件/流

```kotlin
mapper.format(user, file)
val restored = mapper.parse(file, User::class.java)
```

## 特性

- CBOR 二进制
- `BinaryNode` <-> CBOR byte string
- Token 直读/直写
- 文件/流读写
- 整数最小长度、bignum、tag 4 decimal

## 限制

- CBOR tag 不单独保留
- NaN / Infinity 拒绝
- 非字符串 map key 支持有限

## 构建

```bash
gradle :format-cbor:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
