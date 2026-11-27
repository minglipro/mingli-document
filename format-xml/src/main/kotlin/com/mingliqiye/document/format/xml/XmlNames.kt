package com.mingliqiye.document.format.xml

/**
 * XML 名称编码/解码。
 *
 * Java 字段名不一定满足 XML 名称规则，非法字符编码为 `_xHHHH_`。
 * 命名空间感知模式下使用 [encodeQualified] 保留 `prefix:local` 形式的冒号；
 * Clark 名称模式使用 [clark] / [splitClark] 在 `{uri}local` 与名称段之间转换。
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

    /**
     * 拼装 Clark 名称；URI 为空时返回普通本地名。
     */
    fun clark(uri: String?, localName: String): String = if (uri.isNullOrEmpty()) localName else "{$uri}$localName"

    /**
     * 拆分 Clark 名称。
     *
     * @return 第一项为 `null` 表示普通名称；否则为 URI，第二项为本地名
     * @throws IllegalArgumentException `{` 开头但缺少 URI 或本地名时抛出
     */
    fun splitClark(name: String): Pair<String?, String> {
        if (!name.startsWith("{")) {
            return null to name
        }
        val close = name.indexOf('}')
        if (close <= 1 || close == name.length - 1) {
            throw IllegalArgumentException("Invalid Clark name '$name'")
        }
        return name.substring(1, close) to name.substring(close + 1)
    }
}
