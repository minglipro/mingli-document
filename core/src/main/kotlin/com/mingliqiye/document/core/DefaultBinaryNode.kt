package com.mingliqiye.document.core

/**
 * 二进制节点默认实现。
 */
internal class DefaultBinaryNode(
    private val bytes: ByteArray,
) : BinaryNode {

    override val value: ByteArray
        get() = bytes.copyOf()

    override fun equals(other: Any?): Boolean = other is BinaryNode && bytes.contentEquals(other.value)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "BinaryNode(size=${bytes.size})"
}
