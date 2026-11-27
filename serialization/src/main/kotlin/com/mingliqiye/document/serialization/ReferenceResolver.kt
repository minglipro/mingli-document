package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

/**
 * 本地 `$ref`（JSON Pointer）解析器。
 */
internal object ReferenceResolver {

    private const val REF_KEY = "\$ref"

    fun resolve(root: Node): Node = resolveNode(root, root, emptySet())

    private fun resolveNode(node: Node, root: Node, activeRefs: Set<String>): Node = when (node.type) {
        NodeType.OBJECT -> resolveObject(node.asObject(), root, activeRefs)
        NodeType.ARRAY -> ArrayNode.from(node.asArray().asList().map { resolveNode(it, root, activeRefs) })
        else -> node
    }

    private fun resolveObject(node: ObjectNode, root: Node, activeRefs: Set<String>): Node {
        val reference = node[REF_KEY]
        if (reference != null) {
            if (reference.type != NodeType.STRING) {
                throw DocumentSerializationException("$REF_KEY must be a string")
            }
            val pointer = reference.asString()
            if (pointer in activeRefs) {
                throw DocumentSerializationException("Circular \$ref detected: $pointer")
            }
            val target = resolvePointer(root, pointer)
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
