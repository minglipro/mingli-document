package com.mingliqiye.document.format.xml

/**
 * XML 名称编码/解码。
 *
 * Java 字段名不一定满足 XML 名称规则，非法字符编码为 `_xHHHH_`。
 * 命名空间感知模式下使用 [encodeQualified] 保留 `prefix:local` 形式的冒号。
 */
internal object XmlNames {

    private val escapeRegex = Regex("_x([0-9A-Fa-f]{4})_")

    /**
     * 编码普通 XML 名称；`:` 不是合法字符，会被转义。
     */
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

    /**
     * 编码命名空间感知的限定名。
     *
     * 保留唯一的 `prefix:local` 冒号，前缀与本地名分别按 [encode] 规则编码。
     *
     * @throws IllegalArgumentException 名称为空、冒号数量非法或段为空时抛出
     */
    fun encodeQualified(name: String): String {
        val colon = name.indexOf(':')
        if (colon < 0) {
            return encode(name)
        }
        val prefix = name.substring(0, colon)
        val localName = name.substring(colon + 1)
        if (prefix.isEmpty() || localName.isEmpty() || localName.indexOf(':') >= 0) {
            throw IllegalArgumentException("Invalid qualified XML name '$name'")
        }
        return encode(prefix) + ":" + encode(localName)
    }

    /**
     * 解码 `_xHHHH_` 转义；不处理冒号，限定名解码后保持 `prefix:local`。
     */
    fun decode(name: String): String = escapeRegex.replace(name) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }
}
