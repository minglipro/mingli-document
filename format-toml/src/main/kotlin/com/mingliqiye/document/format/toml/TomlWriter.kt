package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.toml.exception.TomlException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64

private const val MAX_INTEGER_DIGITS = 18

private const val HEX_DIGITS = "0123456789ABCDEF"

/** TOML 0.5 裸 key：只允许 ASCII 字母、数字、下划线和连字符。 */
private val bareKey = Regex("[A-Za-z0-9_-]+")

/** TOML 0.5 数字字面量：十进制整数/浮点数、十六进制/八进制/二进制整数、inf/nan。 */
private val tomlNumber = Regex(
    "[+-]?(?:0|[1-9](?:_?[0-9])*)(?:\\.[0-9](?:_?[0-9])*)?(?:[eE][+-]?[0-9](?:_?[0-9])*)?" +
        "|0x[0-9A-Fa-f](?:_?[0-9A-Fa-f])*" +
        "|0o[0-7](?:_?[0-7])*" +
        "|0b[01](?:_?[01])*" +
        "|[+-]?(?:inf|nan)",
)

/** 十进制整数（可带正负号和数字间下划线）。 */
private val decimalInteger = Regex("[+-]?(?:0|[1-9](?:_?[0-9])*)")

private val longMin = BigInteger.valueOf(Long.MIN_VALUE)

private val longMax = BigInteger.valueOf(Long.MAX_VALUE)

/**
 * 将文档树写成 TOML 文本。
 *
 * 为简化实现，对象统一写成内联 table、数组统一写成内联数组：
 * `user = { name = "小于", age = 18 }`。根节点必须是 [ObjectNode]。
 *
 * `null` 处理对齐 上游实现 TOML 的 `TomlWriteFeature.FAIL_ON_NULL_WRITE`：
 * - 关闭（默认）：`null` 写成空字符串 `""`（TOML 规范不允许 null）；
 * - 开启：任何 `null` 值写出都抛 [TomlException]。
 *
 * 注意：tomlj 1.0.0 的解析器无法回读内联 table / 数组中的空数组
 * （`{ a = [] }`、`[ [], [] ]`），因此嵌套空数组只能写出、无法用该解析器读回。
 *
 * @param writeFeatures TOML 写出特性集合，默认 [TomlConfig.DEFAULT_WRITE_FEATURES]
 */
internal class TomlWriter @JvmOverloads constructor(
    private val writeFeatures: Set<TomlWriteFeature> = TomlConfig.DEFAULT_WRITE_FEATURES,
) {

    private val failOnNullWrite: Boolean = TomlWriteFeature.FAIL_ON_NULL_WRITE in writeFeatures

    private var out: Appendable = StringBuilder()

    /**
     * 写出节点并返回 TOML 文本。
     *
     * @throws TomlException 根不是对象、数字无法写成合法 TOML 或开启
     *   `FAIL_ON_NULL_WRITE` 后写出 null 时抛出
     */
    fun write(node: Node): String {
        val builder = StringBuilder()
        writeTo(node, builder)
        return builder.toString()
    }

    /**
     * 直接写出到 [Appendable]，不先构造完整 String。
     */
    fun writeTo(node: Node, target: Appendable) {
        val root = node as? ObjectNode
            ?: throw TomlException("TOML root must be an object but was ${node.type}")
        this.out = target
        writeObjectFields(root)
    }

    /**
     * 把单个值写成 TOML 字面量文本（不输出键与换行）。
     *
     * 供保留注释的写出器复用，行为与 [write] 中的值写出完全一致：
     * 默认把 null 写成 `""`，开启 `FAIL_ON_NULL_WRITE` 时抛出 [TomlException]。
     */
    fun formatValueText(node: Node): String {
        val builder = StringBuilder()
        val previous = out
        out = builder
        try {
            writeValue(node)
        } finally {
            out = previous
        }
        return builder.toString()
    }

    /** 把 key 写成合法的 TOML key（裸 key 或双引号字符串）。 */
    fun formatKeyText(key: String): String = formatKey(key)

    private fun writeObjectFields(node: ObjectNode) {
        for ((key, value) in node.asMap()) {
            out.append(formatKey(key)).append(" = ")
            writeValue(value)
            out.append('\n')
        }
    }

    private fun writeValue(node: Node) {
        when (node) {
            is ObjectNode -> writeInlineTable(node)
            is ArrayNode -> writeInlineArray(node)
            is StringNode -> out.append(formatString(node.value))
            is NumberNode -> out.append(formatNumber(node))
            is BooleanNode -> out.append(node.value.toString())
            is BinaryNode -> out.append(formatString(Base64.getEncoder().encodeToString(node.value)))
            is NullNode -> writeNull()
        }
    }

    private fun writeNull() {
        if (failOnNullWrite) {
            throw TomlException("TOML null writing disabled (TomlWriteFeature.FAIL_ON_NULL_WRITE)")
        }
        out.append("\"\"")
    }

    private fun writeInlineTable(node: ObjectNode) {
        if (node.size == 0) {
            out.append("{}")
            return
        }
        out.append("{ ")
        node.asMap().entries.forEachIndexed { index, entry ->
            if (index > 0) {
                out.append(", ")
            }
            out.append(formatKey(entry.key)).append(" = ")
            writeValue(entry.value)
        }
        out.append(" }")
    }

    private fun writeInlineArray(node: ArrayNode) {
        if (node.size == 0) {
            out.append("[]")
            return
        }
        out.append("[ ")
        for (index in 0 until node.size) {
            if (index > 0) {
                out.append(", ")
            }
            writeValue(node[index])
        }
        out.append(" ]")
    }

    private fun formatKey(key: String): String = if (bareKey.matches(key)) key else formatString(key)

    private fun formatString(value: String): String {
        val escaped = StringBuilder(value.length + 2)
        escaped.append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> escaped.append("\\\\")
                '"' -> escaped.append("\\\"")
                '\b' -> escaped.append("\\b")
                '\t' -> escaped.append("\\t")
                '\n' -> escaped.append("\\n")
                '\u000C' -> escaped.append("\\f")
                '\r' -> escaped.append("\\r")
                else -> {
                    if (ch < ' ' || ch == '\u007F') {
                        appendUnicodeEscape(escaped, ch)
                    } else {
                        escaped.append(ch)
                    }
                }
            }
        }
        escaped.append('"')
        return escaped.toString()
    }

    private fun appendUnicodeEscape(builder: StringBuilder, ch: Char) {
        val code = ch.code
        builder.append("\\u")
        for (shift in intArrayOf(12, 8, 4, 0)) {
            builder.append(HEX_DIGITS[(code shr shift) and 0xF])
        }
    }

    private fun formatNumber(node: NumberNode): String {
        val raw = node.raw
        if (tomlNumber.matches(raw) && isWritableInteger(raw)) {
            return raw
        }
        val value = try {
            node.value
        } catch (e: NumberFormatException) {
            throw TomlException("Invalid TOML number '${node.raw}'", e)
        }
        return formatDecimal(value)
    }

    /**
     * 十进制整数超出 TOML（64 位有符号）范围时不能原样写出。
     */
    private fun isWritableInteger(raw: String): Boolean {
        if (!decimalInteger.matches(raw)) {
            return true
        }
        return try {
            val value = BigInteger(raw.replace("_", ""))
            value >= longMin && value <= longMax
        } catch (_: NumberFormatException) {
            false
        }
    }

    private fun formatDecimal(value: BigDecimal): String {
        if (value.scale() > 0) {
            return value.toPlainString()
        }
        val integerDigits = value.precision() - value.scale()
        return if (integerDigits > MAX_INTEGER_DIGITS) {
            formatScientific(value)
        } else {
            value.toPlainString()
        }
    }

    /**
     * 把超出安全整数位数的值写成科学计数法，保证仍是合法的 TOML 浮点数。
     */
    private fun formatScientific(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val exponent = stripped.precision() - stripped.scale() - 1
        val mantissa = stripped.movePointLeft(exponent).toPlainString()
        val sign = if (exponent >= 0) "+" else ""
        return mantissa + "E" + sign + exponent
    }
}

/**
 * 将文档树写成 TOML 字符串。
 *
 * 根节点必须是对象；输出使用内联 table / 内联数组。`null` 默认写成 `""`，
 * 开启 [TomlWriteFeature.FAIL_ON_NULL_WRITE] 后写出 null 抛 [TomlException]。
 *
 * @param writeFeatures TOML 写出特性集合，默认 [TomlConfig.DEFAULT_WRITE_FEATURES]
 * @return TOML 文本
 * @throws TomlException 根不是对象、数字无法写成合法 TOML 或 null 写出被禁止时抛出
 */
@JvmOverloads
public fun Node.toTomlString(
    writeFeatures: Set<TomlWriteFeature> = TomlConfig.DEFAULT_WRITE_FEATURES,
): String = TomlWriter(writeFeatures).write(this)
