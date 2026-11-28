package com.mingliqiye.document.format.json

import java.math.BigDecimal

private const val LOWER_HEX_DIGITS = "0123456789abcdef"
private const val UPPER_HEX_DIGITS = "0123456789ABCDEF"

/**
 * 非有限数字的规范化文本。
 */
internal const val NAN_LITERAL: String = "NaN"

/**
 * 正无穷的规范化文本。
 */
internal const val POSITIVE_INFINITY_LITERAL: String = "Infinity"

/**
 * 负无穷的规范化文本。
 */
internal const val NEGATIVE_INFINITY_LITERAL: String = "-Infinity"

/**
 * JSON 写出特性解析结果。
 *
 * 从 [JsonWriteFeature] 集合预计算布尔开关，并集中实现字符串转义、非有限数字与
 * `BigDecimal` 普通表示等与写出目标无关的逻辑，供三个写出器共用。
 *
 * @param features 显式开启的写出特性集合
 */
internal class JsonWriteOptions(features: Set<JsonWriteFeature> = JsonConfig.DEFAULT_WRITE_FEATURES) {

    /** 是否给字段名加双引号。 */
    val quoteFieldNames: Boolean = JsonWriteFeature.QUOTE_FIELD_NAMES in features

    /** 是否把所有数字写成 JSON 字符串。 */
    val writeNumbersAsStrings: Boolean = JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS in features

    /** 是否把非有限数字写成 JSON 字符串。 */
    val writeNanAsStrings: Boolean = JsonWriteFeature.WRITE_NAN_AS_STRINGS in features

    /** 是否把指数写法数字转换为普通十进制表示。 */
    val writeBigDecimalAsPlain: Boolean = JsonWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN in features

    /** 是否转义 7 位 ASCII 之外的字符。 */
    val escapeNonAscii: Boolean = JsonWriteFeature.ESCAPE_NON_ASCII in features

    private val upperCaseHex: Boolean = JsonWriteFeature.WRITE_HEX_UPPER_CASE in features

    /**
     * 字符串内容是否需要转义。
     *
     * @param value 字符串内容
     * @return 存在需要转义的字符时返回 `true`
     */
    fun needsEscape(value: String): Boolean {
        for (ch in value) {
            if (needsEscape(ch)) {
                return true
            }
        }
        return false
    }

    /**
     * 字符串中单个字符是否需要转义。
     *
     * @param ch 字符
     * @return 是否需要转义
     */
    fun needsEscape(ch: Char): Boolean = ch == '"' || ch == '\\' || ch < ' ' || (escapeNonAscii && ch > '\u007F')

    /**
     * 转义字符串内容（不含首尾双引号）。
     *
     * @param value 原始字符串
     * @return 转义后的内容
     */
    fun escape(value: String): String {
        val builder = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch < ' ' || (escapeNonAscii && ch > '\u007F')) {
                        appendUnicodeEscape(builder, ch.code)
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        return builder.toString()
    }

    /**
     * 追加一个 `\uXXXX` 转义。
     *
     * @param builder 目标缓冲
     * @param code UTF-16 码元
     */
    fun appendUnicodeEscape(builder: StringBuilder, code: Int) {
        builder.append("\\u")
        val digits = if (upperCaseHex) UPPER_HEX_DIGITS else LOWER_HEX_DIGITS
        for (shift in 12 downTo 0 step 4) {
            builder.append(digits[(code shr shift) and 0xF])
        }
    }

    /**
     * 数字原始文本按输出特性转换。
     *
     * 只应用普通表示转换；是否加引号由调用方根据 [writeNumbersAsStrings] /
     * [writeNanAsStrings] 决定。
     *
     * @param raw 数字原始文本
     * @return 转换后的数字文本
     */
    fun numberLiteral(raw: String): String {
        if (!writeBigDecimalAsPlain || isNonFiniteLiteral(raw)) {
            return raw
        }
        if (raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
            return raw
        }
        return try {
            BigDecimal(raw).toPlainString()
        } catch (e: NumberFormatException) {
            raw
        }
    }

    /**
     * 数字文本是否需要按 JSON 字符串输出。
     *
     * [JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS] 对所有数字生效；
     * [JsonWriteFeature.WRITE_NAN_AS_STRINGS] 只对非有限数字生效。
     *
     * @param text 数字文本
     * @return 需要加引号返回 `true`
     */
    fun shouldQuoteNumber(text: String): Boolean = writeNumbersAsStrings || (writeNanAsStrings && isNonFiniteLiteral(text))

    public companion object {

        /** 默认写出特性（与 [JsonConfig.DEFAULT_WRITE_FEATURES] 一致）。 */
        @JvmField
        val DEFAULT: JsonWriteOptions = JsonWriteOptions()
    }
}

/**
 * 判断是否为非有限数字字面量文本。
 *
 * @param raw 数字原始文本
 * @return 是 `NaN` / `Infinity` / `-Infinity` 时返回 `true`
 */
internal fun isNonFiniteLiteral(raw: String): Boolean = raw == NAN_LITERAL || raw == POSITIVE_INFINITY_LITERAL || raw == NEGATIVE_INFINITY_LITERAL

/**
 * 非有限 `Double` 的 JSON 文本。
 *
 * @param value 非有限 double
 * @return `NaN` / `Infinity` / `-Infinity`
 */
internal fun nonFiniteDoubleText(value: Double): String = when {
    value.isNaN() -> NAN_LITERAL
    value > 0 -> POSITIVE_INFINITY_LITERAL
    else -> NEGATIVE_INFINITY_LITERAL
}
