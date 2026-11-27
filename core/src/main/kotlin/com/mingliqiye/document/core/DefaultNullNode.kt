package com.mingliqiye.document.core

/**
 * null 节点默认实现。
 */
internal object DefaultNullNode : NullNode {
    override fun toString(): String = "null"
}
