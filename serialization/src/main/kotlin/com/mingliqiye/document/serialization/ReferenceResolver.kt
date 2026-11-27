package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

/**
 * `$ref`（JSON Pointer）解析器。
 *
 * 支持本地引用、[DocumentMapperConfig.writeReferences] 产生的结构性循环引用，
 * 以及配置了 [RemoteReferenceResolver] 时的远程引用。
 *
 * 当引用的目标位于当前解析链上（结构循环）时，按目标结构展开一次，环上的
 * 回边保留为 `$ref` 节点。Node 树是不可变树模型，无法表达真正的环，因此环回边
 * 只能以 `$ref` 标记的形式保留。
 *
 * 纯别名环（`$ref` 目标本身仍是一个 `$ref` 对象，如 `{"a": {"$ref": "#/a"}}`）
 * 无法展开，仍抛出 [DocumentSerializationException]；本地缺失引用同样报错。
 *
 * 不以 `#` 开头的引用被视为远程引用，必须配置 [RemoteReferenceResolver]。
 */
internal object ReferenceResolver {

    fun resolve(root: Node, remoteResolver: RemoteReferenceResolver?): Node = resolveNode(root, root, emptySet(), remoteResolver)

    private fun resolveNode(
        node: Node,
        root: Node,
        activeRefs: Set<String>,
        remoteResolver: RemoteReferenceResolver?,
    ): Node = when (node.type) {
        NodeType.OBJECT -> resolveObject(node.asObject(), root, activeRefs, remoteResolver)
        NodeType.ARRAY -> ArrayNode.from(
            node.asArray().asList().map { resolveNode(it, root, activeRefs, remoteResolver) },
        )

        else -> node
    }

    private fun resolveObject(
        node: ObjectNode,
        root: Node,
        activeRefs: Set<String>,
        remoteResolver: RemoteReferenceResolver?,
    ): Node {
        val reference = node[JsonReference.PROPERTY]
        if (reference != null) {
            if (reference.type != NodeType.STRING) {
                throw DocumentSerializationException("${JsonReference.PROPERTY} must be a string")
            }
            val pointer = reference.asString()
            val (target, targetRoot) = resolveReference(root, pointer, remoteResolver)
            if (pointer in activeRefs) {
                if (target is ObjectNode && target.containsKey(JsonReference.PROPERTY)) {
                    throw DocumentSerializationException(
                        "Circular ${JsonReference.PROPERTY} detected: $pointer",
                    )
                }
                return node
            }
            return resolveNode(target, targetRoot, activeRefs + pointer, remoteResolver)
        }
        return ObjectNode.from(
            node.asMap().mapValues { (_, value) -> resolveNode(value, root, activeRefs, remoteResolver) },
        )
    }

    private fun resolveReference(
        root: Node,
        pointer: String,
        remoteResolver: RemoteReferenceResolver?,
    ): Pair<Node, Node> {
        if (pointer.startsWith("#")) {
            return resolvePointer(root, pointer) to root
        }
        val separator = pointer.indexOf('#')
        val uri = if (separator >= 0) pointer.substring(0, separator) else pointer
        val fragment = if (separator >= 0) pointer.substring(separator) else "#"
        val resolver = remoteResolver
            ?: throw DocumentSerializationException("Remote \$ref is not enabled: $pointer")
        val external = resolver.resolve(uri)
        val target = if (fragment == "#") external else resolvePointer(external, fragment)
        return target to external
    }

    private fun resolvePointer(root: Node, pointer: String): Node {
        if (!pointer.startsWith("#/") && pointer != "#") {
            throw DocumentSerializationException("Invalid local \$ref: $pointer")
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
