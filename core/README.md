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
```

`Node` 树是各格式与 Bean 映射之间唯一的中间模型。

## Maven

```kotlin
implementation("com.mingliqiye.document:core:2.0.0")
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

## 特性

- 节点不可变
- 集合字段只读
- `BinaryNode` 输入/输出复制字节数组
- 支持泛型类型引用 `DocumentTypeReference`

## 构建

```bash
gradle :core:test
```

文档：[根 README](../README.md) · [贡献指南](../CONTRIBUTING.md)
