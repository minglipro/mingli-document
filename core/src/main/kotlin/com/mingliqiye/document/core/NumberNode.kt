package com.mingliqiye.document.core

import java.math.BigDecimal
import java.math.BigInteger

/**
 * 数字节点契约。
 *
 * 数字使用 [BigDecimal] 保存精确值；[raw] 保留原始文本。
 */
public interface NumberNode : Node {

    override val type: NodeType
        get() = NodeType.NUMBER

    /** 数字原始文本。 */
    public val raw: String

    /** 数字的精确十进制值。 */
    public val value: BigDecimal

    /** 转为 Byte。 */
    public fun toByte(): Byte = value.byteValueExact()

    /** 转为 Short。 */
    public fun toShort(): Short = value.shortValueExact()

    /** 转为 Int。 */
    public fun toInt(): Int = value.intValueExact()

    /** 转为 Long。 */
    public fun toLong(): Long = value.longValueExact()

    /** 转为 BigInteger。 */
    public fun toBigInteger(): BigInteger = value.toBigIntegerExact()

    /** 转为 Float，可能丢失精度。 */
    public fun toFloat(): Float = value.toFloat()

    /** 转为 Double，可能丢失精度。 */
    public fun toDouble(): Double = value.toDouble()

    /** 转为 BigDecimal。 */
    public fun toBigDecimal(): BigDecimal = value

    override fun asNumber(): NumberNode = this

    public companion object {

        /** 从原始数字文本创建，保留原始写法。 */
        @JvmStatic
        public fun ofRaw(raw: String): NumberNode = DefaultNumberNode(raw)

        /** 从 [BigDecimal] 创建。 */
        @JvmStatic
        public fun of(value: BigDecimal): NumberNode = DefaultNumberNode(value.toPlainString(), value)

        /** 从 [Int] 创建。 */
        @JvmStatic
        public fun of(value: Int): NumberNode = of(BigDecimal.valueOf(value.toLong()))

        /** 从 [Long] 创建。 */
        @JvmStatic
        public fun of(value: Long): NumberNode = of(BigDecimal.valueOf(value))

        /** 从 [Double] 创建。 */
        @JvmStatic
        public fun of(value: Double): NumberNode {
            require(value.isFinite()) { "NaN and Infinity are not valid numbers" }
            return of(BigDecimal.valueOf(value))
        }

        /** 从 [Float] 创建。 */
        @JvmStatic
        public fun of(value: Float): NumberNode {
            require(value.isFinite()) { "NaN and Infinity are not valid numbers" }
            return of(BigDecimal.valueOf(value.toDouble()))
        }

        /** 从 [BigInteger] 创建。 */
        @JvmStatic
        public fun of(value: BigInteger): NumberNode = of(BigDecimal(value))
    }
}
