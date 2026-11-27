# format-cbor

CBOR 二进制格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:cbor:1.1.0")
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
- canonical map key 排序

## Canonical CBOR

```kotlin
val bytes = mapper.formatCanonicalBytes(value)
val nodeBytes = node.toCanonicalCborBytes()
```

对象 key 按 UTF-8 长度升序、同长度按字节序排序，数组保持原顺序。

## 解析深度

```kotlin
CborReader(bytes, maxDepth = 64).read()
```

`CborMapper(DocumentMapperConfig.builder().maxNodeDepth(64).build())` 的 Node 回退路径也会限制深度；Token 直读路径由 Bean 映射配置控制。

## 限制

- CBOR tag 不单独保留
- NaN / Infinity 拒绝
- 非字符串 map key 支持有限
- canonical 只保证 map key 顺序与整数最短编码，不覆盖所有 RFC 8949 确定性要求

## 构建

```bash
gradle :format-cbor:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
