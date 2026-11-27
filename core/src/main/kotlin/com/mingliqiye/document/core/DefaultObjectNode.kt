package com.mingliqiye.document.core

import java.util.Collections
import java.util.LinkedHashMap

/**
 * 对象节点默认实现。
 */
internal class DefaultObjectNode(
    private val fields: LinkedHashMap<String, Node>,
) : ObjectNode {

    override val size: Int
        get() = fields.size

    override val keys: Set<String>
        get() = Collections.unmodifiableSet(fields.keys)

    override fun containsKey(key: String): Boolean = fields.containsKey(key)

    override fun get(key: String): Node? = fields[key]

    override fun entries(): Set<Map.Entry<String, Node>> = asMap().entries

    override fun asMap(): Map<String, Node> = Collections.unmodifiableMap(fields)

    override fun equals(other: Any?): Boolean = other is ObjectNode && fields == other.asMap()

    override fun hashCode(): Int = fields.hashCode()

    override fun toString(): String = "ObjectNode($fields)"

    companion object {

        fun of(vararg entries: Pair<String, Node>): ObjectNode {
            val fields = LinkedHashMap<String, Node>(entries.size)
            for ((key, value) in entries) {
                fields[key] = value
            }
            return DefaultObjectNode(fields)
        }

        fun from(map: Map<String, Node>): ObjectNode = DefaultObjectNode(LinkedHashMap(map))
    }
}
