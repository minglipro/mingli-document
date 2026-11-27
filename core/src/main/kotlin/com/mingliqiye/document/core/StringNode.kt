package com.mingliqiye.document.core

/**
 * 字符串节点契约。
 */
public interface StringNode : Node {

    override val type: NodeType
        get() = NodeType.STRING

    /** 字符串值。 */
    public val value: String

    override fun asString(): String = value

    public companion object {

        /** 创建字符串节点。 */
        @JvmStatic
        public fun of(value: String): StringNode = DefaultStringNode(value)
    }
}
