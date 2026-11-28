package com.mingliqiye.document.format.toml

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal
import java.util.ArrayDeque

/**
 * TOML 源码中的日期时间字面量原文。
 *
 * 上游实现 TOML 在 `TomlReadFeature.PARSE_JAVA_TIME` 关闭（默认）时把日期时间原样作为
 * 字符串返回（仅做 `T`/空格分隔符与 `z`/`Z` 归一化）。本项目底层 tomlj 只给出
 * `java.time` 对象，因此这里对源码做一次轻量词法扫描，收集日期时间字面量原文，
 * 再按 `java.time` 值反查，尽量还原 上游实现 默认行为；查不到时回退 `toString()`。
 *
 * 扫描时跳过注释与字符串内容；同一值出现多次时按源码顺序依次取用。
 */
internal class TomlTemporalText private constructor(
    private val rawByValue: Map<String, ArrayDeque<String>>,
) {

    /**
     * 取得指定日期时间值对应的源码原文。
     *
     * @param value tomlj 解析出的日期时间值
     * @return 源码原文（已归一化分隔符/时区大小写）；没有匹配时返回 `null`
     */
    fun rawFor(value: Temporal): String? = rawByValue[keyOf(value)]?.pollFirst()

    internal companion object {

        private val temporalPattern = Regex(
            "(\\d{4}-\\d{2}-\\d{2})(?:[Tt ](\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)([Zz]|[+-]\\d{2}:\\d{2})?)?" +
                "|(\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)",
        )

        /** 扫描 TOML 源码，收集日期时间字面量原文。 */
        fun scan(source: String): TomlTemporalText {
            val rawByValue = LinkedHashMap<String, ArrayDeque<String>>()
            var index = 0
            val length = source.length
            while (index < length) {
                val char = source[index]
                when {
                    char == '#' -> {
                        while (index < length && source[index] != '\n') {
                            index++
                        }
                    }

                    char == '"' || char == '\'' -> index = skipString(source, index)
                    char in '0'..'9' -> {
                        val match = temporalPattern.find(source, index)
                        if (match == null || match.range.first != index || !isTokenStart(source, index) || !isTokenEnd(source, index + match.value.length)) {
                            index++
                            continue
                        }
                        val raw = normalize(match.value)
                        val temporal = parse(raw)
                        if (temporal != null) {
                            rawByValue.getOrPut(keyOf(temporal)) { ArrayDeque() }.addLast(raw)
                        }
                        index += match.value.length
                    }

                    else -> index++
                }
            }
            return TomlTemporalText(rawByValue)
        }

        private fun skipString(source: String, start: Int): Int {
            val quote = source[start]
            if (start + 2 < source.length && source[start + 1] == quote && source[start + 2] == quote) {
                var index = start + 3
                while (index < source.length) {
                    if (source[index] == '\\' && quote == '"') {
                        index += 2
                        continue
                    }
                    if (source[index] == quote) {
                        var run = 1
                        while (index + run < source.length && source[index + run] == quote) {
                            run++
                        }
                        if (run >= 3) {
                            return index + run
                        }
                        index += run
                        continue
                    }
                    index++
                }
                return source.length
            }
            var index = start + 1
            while (index < source.length) {
                when {
                    source[index] == '\\' && quote == '"' -> index += 2
                    source[index] == quote -> return index + 1
                    source[index] == '\n' -> return index
                    else -> index++
                }
            }
            return source.length
        }

        private fun isTokenStart(source: String, index: Int): Boolean {
            if (index == 0) {
                return true
            }
            val previous = source[index - 1]
            return !(previous.isLetterOrDigit() || previous == '_' || previous == '-' || previous == '+' || previous == ':')
        }

        private fun isTokenEnd(source: String, index: Int): Boolean {
            if (index >= source.length) {
                return true
            }
            val next = source[index]
            return next == ' ' ||
                next == '\t' ||
                next == '\r' ||
                next == '\n' ||
                next == ',' ||
                next == ']' ||
                next == '}' ||
                next == '#' ||
                next == ';'
        }

        private fun normalize(raw: String): String {
            var text = raw
            if (text.length > 10 && (text[10] == ' ' || text[10] == 't')) {
                text = text.substring(0, 10) + 'T' + text.substring(11)
            }
            if (text.isNotEmpty() && text[text.length - 1] == 'z') {
                text = text.substring(0, text.length - 1) + 'Z'
            }
            return text
        }

        private fun parse(text: String): Temporal? = try {
            if (text.length > 10 && (text[10] == 'T')) {
                if (text.endsWith("Z") || text.indexOf('+', 10) > 0 || text.indexOf('-', 10) > 0) {
                    OffsetDateTime.parse(text)
                } else {
                    LocalDateTime.parse(text)
                }
            } else if (text.contains(':')) {
                LocalTime.parse(text)
            } else {
                LocalDate.parse(text)
            }
        } catch (_: DateTimeParseException) {
            null
        }

        private fun keyOf(value: Temporal): String = value.javaClass.name + '\u0000' + value.toString()
    }
}
