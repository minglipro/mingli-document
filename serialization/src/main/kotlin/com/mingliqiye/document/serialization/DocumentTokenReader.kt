package com.mingliqiye.document.serialization

import java.math.BigDecimal

/**
 * 流式文档 Token 读取器。
 *
 * 二进制格式模块可以实现此接口，再把 Token 接入通用文档树或 Bean 映射。
 */
public interface DocumentTokenReader {

    /** 当前 Token 类型；尚未读取或已结束时为 `null`。 */
    public val currentToken: DocumentTokenType?

    /** 当前 Token 的文本值，适用于 FIELD_NAME/STRING/NUMBER。 */
    public val textValue: String?

    /** 当前 Token 的布尔值。 */
    public val booleanValue: Boolean?

    /** 当前 Token 的二进制值。 */
    public val binaryValue: ByteArray?

    /** 当前 NUMBER Token 的 int 值。 */
    public fun intValue(): Int = textValue?.toIntOrNull()
        ?: throw IllegalStateException("Current token is not a valid int")

    /** 当前 NUMBER Token 的 long 值。 */
    public fun longValue(): Long = textValue?.toLongOrNull()
        ?: throw IllegalStateException("Current token is not a valid long")

    /** 当前 NUMBER Token 的 double 值。 */
    public fun doubleValue(): Double = textValue?.toDoubleOrNull()
        ?: throw IllegalStateException("Current token is not a valid double")

    /** 当前 NUMBER Token 的 decimal 值。 */
    public fun decimalValue(): BigDecimal = textValue?.let(::BigDecimal)
        ?: throw IllegalStateException("Current token is not a valid decimal")

    /**
     * 前进到下一个 Token。
     *
     * @return 下一个 Token 类型；结束返回 `null`
     */
    public fun nextToken(): DocumentTokenType?
}
