package com.mingliqiye.document.core

import java.util.Collections

/**
 * 数组节点默认实现。
 */
internal class DefaultArrayNode(
    private val elements: List<Node>,
) : ArrayNode {

    override val size: Int
        get() = elements.size

    override fun get(index: Int): Node = elements[index]

    override fun asList(): List<Node> = Collections.unmodifiableList(elements)

    override fun iterator(): Iterator<Node> = asList().iterator()

    override fun equals(other: Any?): Boolean = other is ArrayNode && elements == other.asList()

    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String = "ArrayNode($elements)"
}
