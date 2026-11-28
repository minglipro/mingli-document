package com.mingliqiye.document.format.properties

/**
 * Properties 写出转义工具。
 *
 * - 键：先按本模块扩展做百分号编码（可选），再按 JDK Properties 规则转义
 *   （`\\`、空格、`=`、`:`、`#`、`!` 与控制字符），对齐 上游实现 `JPropEscapes`；
 * - 值：按 JDK Properties 规则转义，额外转义行首空格以避免 `Properties.load`
 *   丢弃值前导空白（本模块安全扩展；上游实现 不转义行首空格）。
 */
internal object PropertiesPath {

    /**
     * 对象字段名 → 百分号编码后的路径段。
     *
     * 仅编码本模块保留字符 `%`、`.`、`[`、`]`；其中 `.` 与 `[]` 分别表示路径分隔符与
     * 下标标记，`%` 用于自转义。关闭后原样返回。
     */
    fun encodeSegment(key: String): String = key
        .replace("%", "%25")
        .replace(".", "%2E")
        .replace("[", "%5B")
        .replace("]", "%5D")

    /**
     * 写出对象字段段：百分号编码（可选）后再按 JDK 规则转义。
     */
    fun escapeKeySegment(key: String, percentEscape: Boolean, escapeNonAscii: Boolean): String {
        val encoded = if (percentEscape) encodeSegment(key) else key
        return buildString(encoded.length + 8) {
            for (ch in encoded) {
                when {
                    ch == '\\' -> append("\\\\")
                    ch == ' ' -> append("\\ ")
                    ch == '=' -> append("\\=")
                    ch == ':' -> append("\\:")
                    ch == '#' -> append("\\#")
                    ch == '!' -> append("\\!")
                    ch == '\n' -> append("\\n")
                    ch == '\r' -> append("\\r")
                    ch == '\t' -> append("\\t")
                    ch == '\u007F' || ch in '\u0080'..'\u009F' -> appendUnicodeEscape(ch)
                    ch < ' ' -> appendUnicodeEscape(ch)
                    escapeNonAscii && ch > '\u007F' -> appendUnicodeEscape(ch)
                    else -> append(ch)
                }
            }
        }
    }

    /**
     * 写出值转义。
     */
    fun escapeValue(value: String, escapeNonAscii: Boolean): String = buildString(value.length + 8) {
        for ((index, ch) in value.withIndex()) {
            when {
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch == '\u007F' || ch in '\u0080'..'\u009F' -> appendUnicodeEscape(ch)
                index == 0 && ch == ' ' -> appendUnicodeEscape(ch)
                ch < ' ' -> appendUnicodeEscape(ch)
                escapeNonAscii && ch > '\u007F' -> appendUnicodeEscape(ch)
                else -> append(ch)
            }
        }
    }

    private fun StringBuilder.appendUnicodeEscape(ch: Char) {
        append("\\u")
        append(ch.code.toString(16).uppercase().padStart(4, '0'))
    }
}
