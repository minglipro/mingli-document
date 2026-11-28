package com.mingliqiye.document.format.properties

import com.mingliqiye.document.format.properties.exception.PropertiesParseException

/**
 * 路径令牌：对象字段段或数组下标。
 */
internal sealed class PropertiesPathToken {

    /** 对象字段段；[percentEscaped] 表示该段含百分号解码字符（不参与简单下标推断）。 */
    internal class Segment(val text: String, val percentEscaped: Boolean) : PropertiesPathToken()

    /** 数组下标（来自下标标记或简单数字段）。 */
    internal class Index(val index: Int) : PropertiesPathToken()
}

/**
 * Properties 扁平 key 路径拆分器。
 *
 * 对齐 上游实现 `JPropPathSplitter` 的四种策略：
 * - 分隔符 + 下标标记（默认，`FullSplitter`）；
 * - 仅分隔符（`CharPathOnlySplitter` / `StringPathOnlySplitter`，单字符分隔符支持转义字符）；
 * - 仅下标标记（`IndexOnlySplitter`）；
 * - 完全不拆分（`NonSplitting`）。
 *
 * 本模块扩展：启用 [PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS] 时，
 * 先把 `%XX` 序列保护成占位符再拆分，最后在字段段内还原，因此百分号编码的
 * 分隔符/下标标记不会参与结构推断。
 */
internal class PropertiesPathSplitter(private val config: PropertiesConfig) {

    private val separator: String = config.pathSeparator
    private val indexMarker: PropertiesIndexMarker? = config.readIndexMarker
    private val escapeChar: Char = config.pathSeparatorEscapeChar
    private val decodePercent: Boolean = config.decodePercentEscapedSegments
    private val prefixWithSeparator: String? = config.prefix
        ?.takeIf { it.isNotEmpty() }
        ?.let { prefix -> prefix + separator }

    private val indexOnlyPattern: Regex?
    private val fullPattern: Regex?

    init {
        val marker = indexMarker
        if (marker != null) {
            indexOnlyPattern = Regex(
                "(.*)${Regex.escape(marker.start)}(\\d{1,9})${Regex.escape(marker.end)}\\z",
            )
            fullPattern = if (separator.isEmpty()) {
                null
            } else {
                Regex(
                    "(${Regex.escape(separator)})|" +
                        "(${Regex.escape(marker.start)}(\\d{1,9})${Regex.escape(marker.end)})",
                )
            }
        } else {
            indexOnlyPattern = null
            fullPattern = null
        }
    }

    /**
     * 拆分 key。
     *
     * @return 路径令牌；配置了 [PropertiesConfig.prefix] 且 key 不含该前缀时返回 `null`
     *   （对齐 上游实现：这类 key 被忽略）
     */
    fun split(key: String): List<PropertiesPathToken>? {
        val protectedKey = protectPercent(key)
        val source = protectedKey?.text ?: key
        val rawTokens = splitRaw(source) ?: return null
        return if (protectedKey == null) {
            rawTokens.map { raw -> convertRaw(raw) }
        } else {
            unprotectPercent(rawTokens, protectedKey.decoded)
        }
    }

    private fun convertRaw(raw: RawToken): PropertiesPathToken = when (raw) {
        is RawToken.Segment -> PropertiesPathToken.Segment(raw.text, percentEscaped = false)
        is RawToken.Index -> PropertiesPathToken.Index(raw.index)
    }

    private fun splitRaw(source: String): List<RawToken>? {
        val marker = indexMarker
        if (marker == null) {
            return splitWithoutMarkers(source)
        }
        if (separator.isEmpty()) {
            return splitIndexOnly(source, marker)
        }
        var key = source
        val prefix = prefixWithSeparator
        if (prefix != null) {
            if (!key.startsWith(prefix)) {
                return null
            }
            key = key.substring(prefix.length)
        }
        if (!key.contains(marker.start[0])) {
            return splitWithoutMarkers(key)
        }
        val pattern = fullPattern ?: return splitWithoutMarkers(key)
        val tokens = ArrayList<RawToken>()
        var start = 0
        for (match in pattern.findAll(key)) {
            if (match.groups[1]?.value != null) {
                if (match.range.first > start) {
                    tokens.add(RawToken.Segment(key.substring(start, match.range.first)))
                }
                start = match.range.last + 1
            } else {
                if (match.range.first > start) {
                    tokens.add(RawToken.Segment(key.substring(start, match.range.first)))
                }
                start = match.range.last + 1
                tokens.add(RawToken.Index(match.groupValues[3].toInt()))
            }
        }
        if (start < key.length) {
            tokens.add(RawToken.Segment(key.substring(start)))
        }
        return tokens
    }

    private fun splitWithoutMarkers(key: String): List<RawToken> {
        if (separator.isEmpty()) {
            return listOf(RawToken.Segment(key))
        }
        if (separator.length == 1) {
            return splitCharPathOnly(key)
        }
        return splitStringPathOnly(key)
    }

    /** 对齐 上游实现 `CharPathOnlySplitter`（含转义字符处理）。 */
    private fun splitCharPathOnly(key: String): List<RawToken> {
        val sep = separator[0]
        val tokens = ArrayList<RawToken>()
        var start = 0
        while (true) {
            val index = key.indexOf(sep, start)
            if (index < start) {
                break
            }
            if (index > start) {
                if (key[index - 1] == escapeChar) {
                    tokens.addAll(continueWithEscapes(key, start))
                    return tokens
                }
                tokens.add(RawToken.Segment(key.substring(start, index)))
            }
            start = index + 1
            if (start == key.length) {
                break
            }
        }
        if (start < key.length) {
            tokens.add(RawToken.Segment(key.substring(start)))
        }
        return tokens
    }

    /** 对齐 上游实现 `CharPathOnlySplitter._continueWithEscapes`。 */
    private fun continueWithEscapes(key: String, from: Int): List<RawToken> {
        val sep = separator[0]
        val tokens = ArrayList<RawToken>()
        val segment = StringBuilder()
        var start = from
        var escapeCount = 0
        var index = from
        while (index < key.length) {
            val ch = key[index]
            if (ch == escapeChar) {
                escapeCount++
            } else if (ch == sep) {
                if (escapeCount > 0) {
                    segment.append(key, start, index - ((escapeCount + 1) shr 1))
                    if (escapeCount % 2 == 0) {
                        tokens.add(RawToken.Segment(segment.toString()))
                        segment.setLength(0)
                        start = index + 1
                    } else {
                        segment.append(sep)
                        start = index + 1
                    }
                    escapeCount = 0
                } else {
                    segment.append(key, start, index)
                    tokens.add(RawToken.Segment(segment.toString()))
                    segment.setLength(0)
                    start = index + 1
                }
            } else {
                escapeCount = 0
            }
            index++
        }
        segment.append(key, start, key.length)
        tokens.add(RawToken.Segment(segment.toString()))
        return tokens
    }

    /** 对齐 上游实现 `StringPathOnlySplitter`（多字符分隔符，无转义字符支持）。 */
    private fun splitStringPathOnly(key: String): List<RawToken> {
        val tokens = ArrayList<RawToken>()
        var start = 0
        while (true) {
            val index = key.indexOf(separator, start)
            if (index < start) {
                break
            }
            if (index > start) {
                tokens.add(RawToken.Segment(key.substring(start, index)))
            }
            start = index + separator.length
            if (start == key.length) {
                break
            }
        }
        if (start < key.length) {
            tokens.add(RawToken.Segment(key.substring(start)))
        }
        return tokens
    }

    /** 对齐 上游实现 `IndexOnlySplitter`。 */
    private fun splitIndexOnly(key: String, marker: PropertiesIndexMarker): List<RawToken> {
        val pattern = indexOnlyPattern
            ?: Regex("(.*)${Regex.escape(marker.start)}(\\d{1,9})${Regex.escape(marker.end)}\\z")
        val indexes = ArrayList<Int>()
        var current = key
        while (true) {
            val match = pattern.matchEntire(current) ?: break
            indexes.add(match.groupValues[2].toInt())
            current = match.groupValues[1]
        }
        val tokens = ArrayList<RawToken>(indexes.size + 1)
        tokens.add(RawToken.Segment(current))
        for (index in indexes.indices.reversed()) {
            tokens.add(RawToken.Index(indexes[index]))
        }
        return tokens
    }

    // ------------------------------------------------------------------
    // 百分号编码扩展
    // ------------------------------------------------------------------

    private fun protectPercent(key: String): ProtectedKey? {
        if (!decodePercent) {
            return null
        }
        val text = StringBuilder(key.length)
        val decoded = ArrayList<Char>(0)
        var index = 0
        while (index < key.length) {
            val ch = key[index]
            when {
                ch == PERCENT -> {
                    if (index + 2 >= key.length) {
                        throw PropertiesParseException("Incomplete percent escape in key '$key'")
                    }
                    val code = hexCode(key[index + 1], key[index + 2])
                        ?: throw PropertiesParseException("Invalid percent escape in key '$key'")
                    decoded.add(code.toChar())
                    text.append(PLACEHOLDER)
                    index += 3
                }

                ch == PLACEHOLDER -> {
                    decoded.add(ch)
                    text.append(PLACEHOLDER)
                    index++
                }

                else -> {
                    text.append(ch)
                    index++
                }
            }
        }
        return ProtectedKey(text.toString(), decoded)
    }

    private fun unprotectPercent(
        rawTokens: List<RawToken>,
        decoded: List<Char>,
    ): List<PropertiesPathToken> {
        var cursor = 0
        return rawTokens.map { raw ->
            when (raw) {
                is RawToken.Index -> PropertiesPathToken.Index(raw.index)
                is RawToken.Segment -> {
                    if (raw.text.indexOf(PLACEHOLDER) < 0) {
                        PropertiesPathToken.Segment(raw.text, percentEscaped = false)
                    } else {
                        val builder = StringBuilder(raw.text.length)
                        for (ch in raw.text) {
                            if (ch == PLACEHOLDER) {
                                if (cursor >= decoded.size) {
                                    throw PropertiesParseException("Invalid percent escape in key")
                                }
                                builder.append(decoded[cursor])
                                cursor++
                            } else {
                                builder.append(ch)
                            }
                        }
                        PropertiesPathToken.Segment(builder.toString(), percentEscaped = true)
                    }
                }
            }
        }
    }

    private fun hexCode(high: Char, low: Char): Int? {
        val h = Character.digit(high, 16)
        val l = Character.digit(low, 16)
        if (h < 0 || l < 0) {
            return null
        }
        return h shl 4 or l
    }

    private sealed class RawToken {

        internal class Segment(val text: String) : RawToken()

        internal class Index(val index: Int) : RawToken()
    }

    private class ProtectedKey(val text: String, val decoded: List<Char>)

    private companion object {

        private const val PERCENT = '%'

        /** 百分号占位符：私用区字符，拆分后还原。 */
        private const val PLACEHOLDER = '\uE000'
    }
}
