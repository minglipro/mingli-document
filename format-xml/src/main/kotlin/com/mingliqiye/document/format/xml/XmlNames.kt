package com.mingliqiye.document.format.xml

/**
 * XML 元素名编码/解码。
 *
 * Java 字段名不一定满足 XML 名称规则，非法字符编码为 `_xHHHH_`。
 */
internal object XmlNames {

    private val escapeRegex = Regex("_x([0-9A-Fa-f]{4})_")

    fun encode(name: String): String {
        if (name.isEmpty()) {
            throw IllegalArgumentException("XML element name must not be empty")
        }
        val builder = StringBuilder(name.length)
        for ((index, ch) in name.withIndex()) {
            val valid = if (index == 0) {
                ch == '_' || ch.isLetter()
            } else {
                ch == '_' || ch == '-' || ch == '.' || ch.isLetterOrDigit()
            }
            if (valid) {
                builder.append(ch)
            } else {
                builder.append("_x")
                builder.append(ch.code.toString(16).uppercase().padStart(4, '0'))
                builder.append('_')
            }
        }
        return builder.toString()
    }

    fun decode(name: String): String = escapeRegex.replace(name) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }
}
