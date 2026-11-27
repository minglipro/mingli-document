package com.mingliqiye.document.core

import java.math.BigDecimal
import java.math.BigInteger

/**
 * 数字节点默认实现。
 *
 * [value] 惰性解析，树解析阶段只保留 [raw]，避免为每个数字立即创建 [BigDecimal]。
 */
internal class DefaultNumberNode(
    override val raw: String,
    initialValue: BigDecimal? = null,
) : NumberNode {

    @Volatile
    private var cachedValue: BigDecimal? = initialValue

    override val value: BigDecimal
        get() {
            val cached = cachedValue
            if (cached != null) {
                return cached
            }
            val parsed = BigDecimal(raw)
            cachedValue = parsed
            return parsed
        }

    override fun equals(other: Any?): Boolean = other is NumberNode && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = raw

    override fun toByte(): Byte = raw.toByteOrNull() ?: value.byteValueExact()

    override fun toShort(): Short = raw.toShortOrNull() ?: value.shortValueExact()

    override fun toInt(): Int = raw.toIntOrNull() ?: value.intValueExact()

    override fun toLong(): Long = raw.toLongOrNull() ?: value.longValueExact()

    override fun toBigInteger(): BigInteger = raw.toBigIntegerOrNull() ?: value.toBigIntegerExact()

    override fun toFloat(): Float = raw.toFloatOrNull() ?: value.toFloat()

    override fun toDouble(): Double = raw.toDoubleOrNull() ?: value.toDouble()
}
