package com.mingliqiye.document.core

/**
 * null 节点契约。
 */
public interface NullNode : Node {

    override val type: NodeType
        get() = NodeType.NULL

    override val isNull: Boolean
        get() = true

    public companion object {

        /** 全局 null 节点实例。 */
        @JvmField
        public val INSTANCE: NullNode = DefaultNullNode
    }
}
