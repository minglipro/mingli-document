# format-cbor

CBOR 二进制格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:cbor:2.0.0")
```

## 主要 API

```text
CborMapper
CborReader
CborWriter
CborSequenceReader
CborSequenceWriter
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

## CBOR sequence（多值）

CBOR sequence 是零个或多个自描述 CBOR 数据项首尾相接的字节流（RFC 8742）。
单值 API（`CborReader` / `CborWriter` / `formatBytes` / `parse(ByteArray)`）语义不变，
多值读写使用独立 API。

```kotlin
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.cbor.CborSequenceReader
import com.mingliqiye.document.format.cbor.CborSequenceWriter
import com.mingliqiye.document.format.cbor.toCborNodes
import com.mingliqiye.document.format.cbor.toCborSequenceBytes

val mapper = CborMapper()

// 写: 多个实体按顺序写成 CBOR sequence（Token 直写，不构建 Node 树）
val bytes = mapper.formatAll(listOf(userA, userB))

// 读: List<Node>
val nodes = mapper.parseAllToNodes(bytes)
val nodes2 = CborSequenceReader.parseAll(bytes)
val nodes3 = bytes.toCborNodes()

// 读: List<Bean>（每个数据项反序列化为一个实体）
val users = mapper.parseAll(bytes, User::class.java)
val typed = mapper.parseAll<User>(bytes) // reified 扩展

// 直接读写 Node
val nodeBytes = nodes.toCborSequenceBytes()
val more = CborSequenceWriter(preserveTags = true).writeAll(nodes)
```

选项：

- `CborSequenceReader(source, maxDepth, maxItems, preserveTags)`：`maxDepth` 限制
  单个数据项的 Node 树深度（0 = 不限制）；`maxItems` 限制数据项数量（0 = 不限制）；
  `preserveTags` 对每一项生效。
- `CborMapper.parseAllToNodes(source, maxItems)` / `parseAll(source, type, maxItems)`
  的深度限制取自 Mapper 配置的 `maxNodeDepth`。
- `CborSequenceWriter(canonical, preserveTags)` / `toCborSequenceBytes(canonical, preserveTags)`。

边界约定：

- 空输入是合法的零项 sequence：`parseAll` 返回空列表，`formatAll(emptyList())` 返回空字节数组；
- 只有一个数据项时返回长度为 1 的列表；
- 任一项格式非法或数据不完整时整体抛出 `CborParseException`，不返回部分结果；
- 每个数据项独立解析，深度从 1 重新计数。

## 特性

- CBOR 二进制
- CBOR sequence 多值读写（`parseAll` / `formatAll`，支持 `maxItems` / 深度限制）
- `BinaryNode` <-> CBOR byte string
- Token 直读/直写
- 文件/流读写
- 整数最小长度、bignum、tag 4 decimal
- canonical map key 排序
- 可选 CBOR tag 保真

## Tag 保真

默认关闭；开启后使用包装对象：

```kotlin
val mapper = CborMapper(
    CborConfig.builder()
        .preserveTags(true)
        .build(),
)
```

```json
{"$cborTag":100,"value":"abc"}
```

写出时使用 `CBORGenerator.writeTag` 还原 tag。默认模式忽略 tag，保持原行为。

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
- CBOR sequence 为整段解析：任一项失败不返回部分结果，暂未提供逐项/流式增量 API

## 构建

```bash
gradle :format-cbor:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
