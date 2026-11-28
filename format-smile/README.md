# format-smile

Smile 二进制格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:smile:2.5.1")
```

## 主要 API

```text
SmileMapper
SmileReader
SmileWriter
SmileSequenceReader
SmileSequenceWriter
SmileTokenReader
SmileTokenWriter
SmileConfig
SmileConfigBuilder
SmileReadFeature
SmileWriteFeature
```

## 配置（SmileConfig）

读写特性与 Smile 底层库 对齐，默认值一致；`enable` / `disable` /
`configure` 与 参考实现 同名选项：

| 读取特性 `SmileReadFeature` | 默认 | 行为 |
|---|---|---|
| `REQUIRE_HEADER` | 开 | 输入必须带 Smile 头部；关闭后读取无头部内容 |
| `STRICT_DUPLICATE_DETECTION` | 关 | 重复字段名抛解析异常（默认以后出现的值为准） |

| 写出特性 `SmileWriteFeature` | 默认 | 行为 |
|---|---|---|
| `WRITE_HEADER` | 开 | 写出 Smile 头部；关闭后输出无头部 |
| `WRITE_END_MARKER` | 关 | 关闭写出器时追加 `0xFF` 结束标记 |
| `ENCODE_BINARY_AS_7BIT` | 开 | 二进制 7-bit 编码（输出不含 `0xFF`）；关闭写原始二进制 |
| `CHECK_SHARED_NAMES` | 开 | 重复字段名使用反向引用 |
| `CHECK_SHARED_STRING_VALUES` | 关 | 重复短字符串使用反向引用 |
| `LENIENT_UTF_ENCODING` | 关 | 非法代理项替换为 U+FFFD 而不是写出失败 |
| `STRICT_DUPLICATE_DETECTION` | 关 | Token 写出重复字段名时抛异常 |

```kotlin
import com.mingliqiye.document.format.smile.SmileConfig
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.smile.SmileReadFeature
import com.mingliqiye.document.format.smile.SmileWriteFeature

val config = SmileConfig.builder()
    .disable(SmileWriteFeature.WRITE_HEADER)   // 无头部输出
    .disable(SmileReadFeature.REQUIRE_HEADER)  // 读取无头部输入
    .enable(SmileWriteFeature.CHECK_SHARED_STRING_VALUES)
    .enable(SmileReadFeature.STRICT_DUPLICATE_DETECTION)
    .build()
val mapper = SmileMapper(config)

val bytes = mapper.formatBytes(user)
val restored = mapper.parse(bytes, User::class.java)
```

`SmileConfigBuilder` 同时提供通用 `DocumentMapperConfig` 便捷方法：`field`、
`missingFieldPolicy`、`failOnUnknownFields`、`writeNulls`、`namingStrategy`、
`fileCharset`、`atomicFileWrite`、`createParentDirectories`、`maxInputSize`、
`maxStringLength`、`maxNodeDepth`、`maxNodeCount`、`resolveReferences`、
`writeReferences`、`remoteReferenceResolver`、`beanValidator`、`subtype` /
`typeProperty`、反序列化特性 `enable` / `disable` / `configure` 等。

注意：`WRITE_HEADER` 关闭时要求 `CHECK_SHARED_STRING_VALUES` 关闭且
`ENCODE_BINARY_AS_7BIT` 开启（与 参考实现 生成器一致），否则 `build()` 抛
`IllegalArgumentException`。

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

## 多消息流（多根值）

Smile 字节流允许连续出现多个根值（多消息流）。单值 API（`SmileReader` /
`SmileWriter` / `formatBytes` / `parse(ByteArray)`）语义不变，多值读写使用独立 API。

```kotlin
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.smile.SmileSequenceReader
import com.mingliqiye.document.format.smile.SmileSequenceWriter
import com.mingliqiye.document.format.smile.toSmileNodes
import com.mingliqiye.document.format.smile.toSmileSequenceBytes

val mapper = SmileMapper()

// 写: 多个实体按顺序写成多消息流（Token 直写，Smile 头部只写一次）
val bytes = mapper.formatAll(listOf(userA, userB))

// 读: List<Node>
val nodes = mapper.parseAllToNodes(bytes)
val nodes2 = SmileSequenceReader.parseAll(bytes)
val nodes3 = bytes.toSmileNodes()

// 读: List<Bean>（每个根值反序列化为一个实体）
val users = mapper.parseAll(bytes, User::class.java)
val typed = mapper.parseAll<User>(bytes) // reified 扩展

// 直接读写 Node
val nodeBytes = nodes.toSmileSequenceBytes()
val more = SmileSequenceWriter().writeAll(nodes)
```

选项：

- `SmileSequenceReader(source, maxDepth, maxItems)`：`maxDepth` 限制单个根值的
  Node 树深度（0 = 不限制）；`maxItems` 限制消息数量（0 = 不限制）。
- `SmileMapper.parseAllToNodes(source, maxItems)` / `parseAll(source, type, maxItems)`
  的深度限制取自 Mapper 配置的 `maxNodeDepth`。

边界约定：

- 空输入是合法的零消息流：`parseAll` 返回空列表，`formatAll(emptyList())` 返回空字节数组；
- 只有一个根值时返回长度为 1 的列表；
- 任一消息格式非法或数据不完整时整体抛出 `SmileParseException`，不返回部分结果；
- 每个根值独立解析，深度从 1 重新计数。

## 解析深度

```kotlin
SmileReader(bytes, maxDepth = 64).read()
SmileSequenceReader(bytes, maxDepth = 64).readAll()
```

`SmileMapper(DocumentMapperConfig.builder().maxNodeDepth(64).build())` 的 Node 回退路径也会限制深度。

## 特性

- Smile 二进制
- 读写特性对齐 Smile 底层库（`SmileConfig`，默认值一致）
- 多消息流多值读写（`parseAll` / `formatAll`，支持 `maxItems` / 深度限制）
- Token 直读/直写
- `BinaryNode` 使用 参考实现 Smile embedded binary
- 文件/流读写

## 限制

- 单值入口为单根单文档；多根值请使用 `parseAll` / `formatAll`
- 多消息流为整段解析：任一消息失败不返回部分结果，暂未提供逐项/流式增量 API
- 二进制值依赖 参考实现 Smile 扩展
- 非有限浮点在 Node 层即被拒绝
- 流式 `format(value, OutputStream)` 只 flush 不 close，`WRITE_END_MARKER` 只在
  关闭写出器的路径（`formatBytes` / `formatAll` / Node 写出）生效
- 仅暴露 Smile 专属特性及 `STRICT_DUPLICATE_DETECTION`；面向 JSON 文本的 参考实现
  通用特性（注释、引号/转义、`WRITE_NUMBERS_AS_STRINGS` 等）不适用于 Smile 二进制

## 构建

```bash
gradle :format-smile:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
