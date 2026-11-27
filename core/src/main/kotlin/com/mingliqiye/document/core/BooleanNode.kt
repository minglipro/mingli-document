package com.mingliqiye.document.core

/**
 * 布尔节点契约。
 */
public interface BooleanNode : Node {

    override val type: NodeType
        get() = NodeType.BOOLEAN

    /** 布尔值。 */
    public val value: Boolean

    override fun asBoolean(): Boolean = value

    public companion object {

        /** 创建布尔节点。 */
        @JvmStatic
        public fun of(value: Boolean): BooleanNode = DefaultBooleanNode(value)
    }
}
