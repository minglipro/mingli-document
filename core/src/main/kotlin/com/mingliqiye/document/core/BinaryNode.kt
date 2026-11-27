package com.mingliqiye.document.core

/**
 * 二进制节点契约。
 */
public interface BinaryNode : Node {

    override val type: NodeType
        get() = NodeType.BINARY

    /** 二进制内容；每次访问都返回副本。 */
    public val value: ByteArray

    override fun asBinary(): ByteArray = value

    public companion object {

        /** 创建二进制节点；内部会复制传入数组。 */
        @JvmStatic
        public fun of(value: ByteArray): BinaryNode = DefaultBinaryNode(value.copyOf())
    }
}
