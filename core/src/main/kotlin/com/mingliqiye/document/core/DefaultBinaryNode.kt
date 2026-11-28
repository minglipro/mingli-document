package com.mingliqiye.document.core

/**
 * 二进制节点默认实现。
 *
 * [shared] 为 `false` 时每次读取都复制内容；为 `true` 时直接暴露创建时传入的数组
 * （零拷贝），不可变约定由调用方保证。
 */
internal class DefaultBinaryNode(
    private val bytes: ByteArray,
    private val shared: Boolean,
) : BinaryNode {

    override val value: ByteArray
        get() = if (shared) bytes else bytes.copyOf()

    override fun equals(other: Any?): Boolean = other is BinaryNode && bytes.contentEquals(other.value)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "BinaryNode(size=${bytes.size})"
}
