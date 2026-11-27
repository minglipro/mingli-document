package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

/**
 * 本地 `$ref`（JSON Pointer）解析器。
 *
 * 除普通引用外，还支持 [DocumentMapperConfig.writeReferences] 产生的**结构性循环**
 * 引用：当引用的目标位于当前解析链上（结构循环）时，按目标结构展开一次，环上的
 * 回边保留为 `$ref` 节点。Node 树是不可变树模型，无法表达真正的环，因此环回边
 * 只能以 `$ref` 标记的形式保留。
 *
 * 纯别名环（`$ref` 目标本身仍是一个 `$ref` 对象，如 `{"a": {"$ref": "#/a"}}`）
 * 无法展开，仍抛出 [DocumentSerializationException]；远程引用与缺失引用同样报错。
 */
internal object ReferenceResolver {

    fun resolve(root: Node): Node = resolveNode(root, root, emptySet())

    private fun resolveNode(node: Node, root: Node, activeRefs: Set<String>): Node = when (node.type) {
        NodeType.OBJECT -> resolveObject(node.asObject(), root, activeRefs)
        NodeType.ARRAY -> ArrayNode.from(node.asArray().asList().map { resolveNode(it, root, activeRefs) })
        else -> node
    }

    private fun resolveObject(node: ObjectNode, root: Node, activeRefs: Set<String>): Node {
        val reference = node[JsonReference.PROPERTY]
        if (reference != null) {
            if (reference.type != NodeType.STRING) {
                throw DocumentSerializationException("${JsonReference.PROPERTY} must be a string")
            }
            val pointer = reference.asString()
            val target = resolvePointer(root, pointer)
            if (pointer in activeRefs) {
                if (target is ObjectNode && target.containsKey(JsonReference.PROPERTY)) {
                    throw DocumentSerializationException(
                        "Circular ${JsonReference.PROPERTY} detected: $pointer",
                    )
                }
                return node
            }
            return resolveNode(target, root, activeRefs + pointer)
        }
        return ObjectNode.from(
            node.asMap().mapValues { (_, value) -> resolveNode(value, root, activeRefs) },
        )
    }

    private fun resolvePointer(root: Node, pointer: String): Node {
        if (!pointer.startsWith("#/") && pointer != "#") {
            throw DocumentSerializationException("Only local \$ref is supported: $pointer")
        }
        if (pointer == "#") {
            return root
        }
        var current = root
        pointer.removePrefix("#/").split('/').forEach { rawToken ->
            val token = rawToken.replace("~1", "/").replace("~0", "~")
            current = when (current.type) {
                NodeType.OBJECT -> current.asObject()[token]
                    ?: throw DocumentSerializationException("Cannot resolve \$ref '$pointer'")

                NodeType.ARRAY -> {
                    val index = token.toIntOrNull()
                        ?: throw DocumentSerializationException("Invalid array index in \$ref '$pointer'")
                    val array = current.asArray()
                    if (index < 0 || index >= array.size) {
                        throw DocumentSerializationException("Cannot resolve \$ref '$pointer'")
                    }
                    array[index]
                }

                else -> throw DocumentSerializationException("Cannot resolve \$ref '$pointer'")
            }
        }
        return current
    }
}
