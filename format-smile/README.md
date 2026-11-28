# format-smile

Smile 二进制格式模块。

## Maven

```kotlin
implementation("com.mingliqiye.document.format:smile:2.3.0")
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
- 多消息流多值读写（`parseAll` / `formatAll`，支持 `maxItems` / 深度限制）
- Token 直读/直写
- `BinaryNode` 使用 Jackson Smile embedded binary
- 文件/流读写

## 限制

- 单值入口为单根单文档；多根值请使用 `parseAll` / `formatAll`
- 多消息流为整段解析：任一消息失败不返回部分结果，暂未提供逐项/流式增量 API
- 二进制值依赖 Jackson Smile 扩展
- 非有限浮点在 Node 层即被拒绝

## 构建

```bash
gradle :format-smile:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
