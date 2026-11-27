package com.mingliqiye.document.core

import java.math.BigDecimal

/**
 * 数字节点默认实现。
 */
internal class DefaultNumberNode(
    override val raw: String,
    override val value: BigDecimal,
) : NumberNode {

    constructor(raw: String) : this(raw, BigDecimal(raw))

    override fun equals(other: Any?): Boolean = other is NumberNode && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = raw
}
