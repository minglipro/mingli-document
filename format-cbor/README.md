# format-cbor

CBOR 二进制格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:cbor:2.5.1")
```

## 主要 API

```text
CborMapper
CborConfig / CborConfigBuilder
CborReadFeature / CborWriteFeature
CborReader
CborWriter
CborSequenceReader
CborSequenceWriter
CborTokenReader
CborTokenWriter
```

## 配置与 参考实现 特性

`CborConfig` 继承通用 `DocumentMapperConfig`，在 tag 保真与输出缓冲池之外，完整提供
`CBOR 底层库` 的 parser/generator 特性，并默认对齐 参考实现 2.x：

```kotlin
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.cbor.CborReadFeature
import com.mingliqiye.document.format.cbor.CborWriteFeature

val mapper = CborMapper(
    CborConfig.builder()
        .enable(CborReadFeature.STRICT_DUPLICATE_DETECTION)          // 重复 key 报错
        .enable(CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING)
        .disable(CborWriteFeature.WRITE_MINIMAL_INTS)               // 定长整数
        .enable(CborWriteFeature.WRITE_TYPE_HEADER)                 // 自描述 tag d9d9f7
        .writeNulls(false)
        .maxNodeDepth(64)
        .build(),
)
```

读取特性（`CborReadFeature`，默认全部关闭，对齐 参考实现 2.x；参考实现 3.x 默认开启前三个）：

| 特性 | 行为 |
|---|---|
| `DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING` | tag 3 负数 bignum 按 RFC 8949 解码（默认保持 参考实现 ≤2.19 历史解码） |
| `READ_UNDEFINED_AS_EMBEDDED_OBJECT` | `undefined` 读成标记对象 `{"$cborUndefined": true}`（默认读成 `null`） |
| `READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT` | simple value 读成标记对象 `{"$cborSimpleValue": <整数>}`（默认读成数字） |
| `STRICT_DUPLICATE_DETECTION` | map 重复 key 抛 `CborParseException`（默认后者覆盖前者） |

写出特性（`CborWriteFeature`，默认只开启 `WRITE_MINIMAL_INTS`，对齐 参考实现 2.x）：

| 特性 | 行为 |
|---|---|
| `WRITE_MINIMAL_INTS` | 整数最短编码（默认开启；关闭时 int/long 按 4/8 字节定长） |
| `WRITE_TYPE_HEADER` | 文档开头写自描述 tag（`d9d9f7`） |
| `LENIENT_UTF_ENCODING` | 非法代理对写 `U+FFFD`，默认抛 `CborException` |
| `STRINGREF` | 生成 stringref 共享字符串引用（tag 256/25） |
| `WRITE_MINIMAL_DOUBLES` | Token 直写路径的 `double` 尽量写 4 字节 float；关闭时沿用十进制分数（tag 4） |
| `ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING` | tag 3 负数 bignum 按 RFC 8949 编码 |
| `STRICT_DUPLICATE_DETECTION` | Token 写出重复字段名抛 `CborException` |

`CborConfigBuilder` 同时提供通用 `DocumentMapperConfig` 便捷方法：`field`、
`missingFieldPolicy`、`failOnUnknownFields`、`writeNulls`、`maxNodeDepth`、`subtype`、
`typeProperty`、`resolveReferences`、`writeReferences`；需要完整通用配置时用
`base(DocumentMapperConfig)`（与上述便捷方法互斥）。

说明与差异：

- 开启 `READ_UNDEFINED_AS_EMBEDDED_OBJECT` / `READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT` 时，
  `parse(ByteArray)` 的 Token 快路径会自动禁用（改用 Node 路径表示标记对象）；
  标记对象没有对应的 参考实现 写出 API，不能直接写回；
- `WRITE_MINIMAL_DOUBLES` 只影响 Token 直写路径（Bean 的 `Double` 字段）；Node 数字仍统一走
  整数/十进制分数策略，因此关闭该特性时输出十进制分数而不是 参考实现 默认的 8 字节 double；
- `WRITE_TYPE_HEADER` / `STRINGREF` 基于 tag 扩展，与 `preserveTags=true` 同时使用会让读取端
  把命名空间 tag 当作普通 tag 包装，通常不应同时开启；
- 依赖版本为 `CBOR 底层库:2.20.0`（2.20 起才有上述读取特性与规范负数 bignum 特性）。

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
- 对齐 参考实现 CBOR 配置：`CBORParser.Feature` / `CBORGenerator.Feature` 全部 + `STRICT_DUPLICATE_DETECTION`
- CBOR sequence 多值读写（`parseAll` / `formatAll`，支持 `maxItems` / 深度限制）
- `BinaryNode` <-> CBOR byte string
- `BinaryNode.ofShared` / `ofUnsafe` 零拷贝输入
- 可选输出缓冲池（`ByteArrayPool`），默认不池化
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

## 输出缓冲池

默认写出使用普通临时缓冲区；可以传入 `ByteArrayPool` 复用中间缓冲区，减少高频
写出时的 `byte[]` 分配。未设置（默认 `null`）时行为与未池化完全一致。

```kotlin
import com.mingliqiye.document.core.ByteArrayPool
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.cbor.toCborBytes

val pool = ByteArrayPool()
try {
    // 1) Node -> ByteArray
    val nodeBytes = node.toCborBytes(bufferPool = pool)

    // 2) Mapper：formatBytes / formatAll / formatCanonicalBytes 的 Node 路径使用池
    val mapper = CborMapper(CborConfig.builder().bufferPool(pool).build())
    val beanBytes = mapper.formatBytes(user)

    // 3) CBOR sequence
    val seqBytes = mapper.formatAll(listOf(userA, userB))
} finally {
    pool.close()
}
```

- 池只复用中间缓冲区；返回的 `ByteArray` 始终是调用方独占的新数组（最终仍会复制一次）。
- `CborSequenceWriter(canonical, preserveTags, bufferPool)` 与
  `Collection<Node>.toCborSequenceBytes(canonical, preserveTags, bufferPool)` 同样支持池。
- 写出失败（例如非法数字）时缓冲区仍会归还池；池关闭后不可再用。
- 流式写出（`format(OutputStream)`）与读取路径不使用池。

## 零拷贝 BinaryNode

`BinaryNode.of` 会复制字节数组；`BinaryNode.ofShared` / `ofUnsafe` 不复制，节点直接
持有传入数组，写出时不会为二进制内容额外分配副本。调用方必须保证节点存活期间
不修改该数组：

```kotlin
import com.mingliqiye.document.core.BinaryNode

val payload = byteArrayOf(1, 2, 3)
val node = BinaryNode.ofShared(payload) // 零拷贝
val bytes = node.toCborBytes() // 43010203，写出过程不修改 payload
// 不要修改 payload，否则 node 内容会随之变化；需要复制时使用 BinaryNode.of(payload)
```

## 解析深度

```kotlin
CborReader(bytes, maxDepth = 64).read()
```

`CborMapper(DocumentMapperConfig.builder().maxNodeDepth(64).build())` 的 Node 回退路径也会限制深度；Token 直读路径由 Bean 映射配置控制。

## 限制

- CBOR tag 默认不单独保留（需开启 `preserveTags`）；多层叠加 tag 只能保留最外层
- NaN / Infinity 拒绝（读取与 `WRITE_MINIMAL_DOUBLES` 写出路径）
- 非字符串 map key 支持有限
- canonical 只保证 map key 顺序与整数最短编码，不覆盖所有 RFC 8949 确定性要求
- CBOR sequence 为整段解析：任一项失败不返回部分结果，暂未提供逐项/流式增量 API
- 输出缓冲池只覆盖内存写出路径（`ByteArray`），不覆盖流式写出与读取
- `BinaryNode.ofShared` / `ofUnsafe` 的安全由调用方保证（不得修改传入数组）
- `undefined` / simple value 的标记对象只读：参考实现 无对应写出 API，不能直接写回
- `WRITE_MINIMAL_DOUBLES` 只对 Token 直写路径生效；关闭时输出十进制分数而非 参考实现 默认的
  8 字节 double
- `WRITE_TYPE_HEADER` / `STRINGREF` 基于 tag 扩展，不与 `preserveTags` 组合使用

## 构建

```bash
gradle :format-cbor:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
