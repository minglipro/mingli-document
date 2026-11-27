package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.util.ArrayDeque
import java.util.IdentityHashMap

/**
 * Node 树深度/节点数限制。
 */
internal object NodeLimits {

    fun validate(node: Node, maxDepth: Int, maxNodeCount: Long) {
        if (maxDepth <= 0 && maxNodeCount <= 0) {
            return
        }
        val visited = IdentityHashMap<Node, Boolean>()
        val stack = ArrayDeque<Pair<Node, Int>>()
        stack.addLast(node to 1)
        var count = 0L
        while (stack.isNotEmpty()) {
            val (current, depth) = stack.removeLast()
            if (visited.put(current, true) != null) {
                throw DocumentSerializationException("Node tree contains a cycle")
            }
            if (maxDepth > 0 && depth > maxDepth) {
                throw DocumentSerializationException("Node depth $depth exceeds maxNodeDepth $maxDepth")
            }
            count++
            if (maxNodeCount > 0 && count > maxNodeCount) {
                throw DocumentSerializationException("Node count exceeds maxNodeCount $maxNodeCount")
            }
            when (current) {
                is ObjectNode -> current.entries().forEach { stack.addLast(it.value to depth + 1) }
                is ArrayNode -> current.forEach { stack.addLast(it to depth + 1) }
                else -> Unit
            }
        }
    }
}
