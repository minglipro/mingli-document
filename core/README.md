# core

格式无关的文档树核心模块。

## 主要类型

```text
Node
ObjectNode
ArrayNode
StringNode
NumberNode
BooleanNode
BinaryNode
NullNode
DocumentTypeReference
ByteArrayPool
```

`Node` 树是各格式与 Bean 映射之间唯一的中间模型。

## Maven

```kotlin
implementation("com.mingliqiye.document:core:2.2.0")
```

## 示例

```kotlin
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.core.ArrayNode

val node = ObjectNode.of(
    "name" to StringNode.of("小于"),
    "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
)

val obj = node.asObject()
println(obj["name"]?.asString()) // 小于
```

## 对象池（ByteArrayPool）

`ByteArrayPool` 复用 `byte[]` 缓冲区，减少高频编解码路径上的临时分配：

```kotlin
import com.mingliqiye.document.core.ByteArrayPool

val pool = ByteArrayPool(maxIdleBuffers = 16, maxBufferSize = 1 shl 20)
try {
    val buffer = pool.borrow(4096) // 返回长度 >= 4096 的数组
    // 只使用前 4096 个字节；内容不保证已清零
    pool.release(buffer) // 每个借出的缓冲区必须且只能归还一次
} finally {
    pool.close()
}
```

约定与能力：

- `borrow(size)` 优先复用“能装下 `size` 的最小空闲缓冲区”，否则新分配；返回长度可能大于 `size`；
- `release(buffer)` 按引用身份校验：重复释放、归还非本池数组都会抛 `IllegalStateException`；
- 空闲数量上限 `maxIdleBuffers`（默认 16），超过 `maxBufferSize`（默认 1 MiB）的缓冲区在归还时丢弃；
- `clear()` 只丢弃空闲缓冲区，池可继续使用；`close()` 关闭池（幂等），之后借出/归还都会抛异常；
- 线程安全：多线程可同时 `borrow`/`release`；
- 便捷写法 `pool.withBorrowedBuffer(size) { buffer -> ... }` 会在退出（含异常）时自动归还；
- 统计属性：`idleBufferCount`、`borrowedBufferCount`、`totalBorrowed`、`totalReleased`、`totalAllocated`、`totalReused`、`totalDiscarded`。

## 零拷贝 BinaryNode

`BinaryNode.of(byteArray)` 复制输入，`BinaryNode.ofShared(byteArray)`（别名 `ofUnsafe`）不复制、直接持有数组：

```kotlin
import com.mingliqiye.document.core.BinaryNode

val payload = byteArrayOf(1, 2, 3)

val copied = BinaryNode.of(payload) // 复制：之后修改 payload 不影响节点
payload[0] = 9
check(copied.asBinary()[0].toInt() == 1)

val shared = BinaryNode.ofShared(payload) // 零拷贝：节点直接持有 payload
check(shared.value === payload) // 同一个数组实例
check(shared.asBinary() === payload)

// 不可变约定：节点存活期间调用方不得修改 payload，否则节点内容、equals/hashCode 都会变化。
// 需要防御性复制时使用 BinaryNode.of。
```

由 `of` 创建的节点每次访问 `value`/`asBinary()` 都返回新副本；由 `ofShared`/`ofUnsafe`
创建的节点返回同一个数组实例，调用方必须把它视为只读。

## 特性

- 节点不可变
- 集合字段只读
- `BinaryNode.of` 复制字节数组；`ofShared` / `ofUnsafe` 零拷贝（由调用方保证不修改）
- `ByteArrayPool` 可复用缓冲区池（容量上限、重复释放检测、线程安全）
- 支持泛型类型引用 `DocumentTypeReference`

## 构建

```bash
gradle :core:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
