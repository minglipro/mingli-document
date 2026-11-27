package com.mingliqiye.document.format.properties

import com.mingliqiye.document.format.properties.exception.PropertiesParseException

/**
 * Properties 平铺 key 与 Node 路径之间的转换。
 *
 * 约定：
 * - 对象字段用 `.` 分隔，例如 `user.name`
 * - 数组用 `[index]`，例如 `tags[0]`、`matrix[0][1]`
 * - 字段名中的 `%`、`.`、`[`、`]` 使用百分号编码，例如 `%2E`
 */
internal object PropertiesPath {

    fun parse(path: String): List<PropertiesPathSegment> {
        if (path.isEmpty()) {
            throw PropertiesParseException("Properties key must not be empty")
        }
        val segments = ArrayList<PropertiesPathSegment>()
        val current = StringBuilder()
        var index = 0
        var lastWasIndex = false
        while (index < path.length) {
            val ch = path[index]
            when {
                ch == '%' -> {
                    if (index + 2 >= path.length) {
                        throw PropertiesParseException("Incomplete percent escape in key '$path'")
                    }
                    val code = path.substring(index + 1, index + 3).toIntOrNull(16)
                        ?: throw PropertiesParseException("Invalid percent escape in key '$path'")
                    current.append(code.toChar())
                    index += 3
                    lastWasIndex = false
                }

                ch == '.' -> {
                    if (current.isNotEmpty()) {
                        flushKey(path, current, segments)
                    } else if (!lastWasIndex) {
                        throw PropertiesParseException("Empty path segment in key '$path'")
                    }
                    index++
                    lastWasIndex = false
                    if (index >= path.length) {
                        throw PropertiesParseException("Key path must not end with '.': '$path'")
                    }
                }

                ch == '[' -> {
                    flushKeyIfNotEmpty(current, segments)
                    val close = path.indexOf(']', index + 1)
                    if (close < 0) {
                        throw PropertiesParseException("Unclosed array index in key '$path'")
                    }
                    val number = path.substring(index + 1, close).toIntOrNull()
                        ?: throw PropertiesParseException("Invalid array index in key '$path'")
                    segments.add(PropertiesPathSegment.index(number))
                    index = close + 1
                    lastWasIndex = true
                    if (index < path.length && path[index] != '.' && path[index] != '[') {
                        throw PropertiesParseException("Unexpected character after array index in key '$path'")
                    }
                }

                ch == ']' -> throw PropertiesParseException("Unexpected ']' in key '$path'")

                else -> {
                    if (lastWasIndex) {
                        throw PropertiesParseException("Unexpected character after array index in key '$path'")
                    }
                    current.append(ch)
                    index++
                }
            }
        }
        if (current.isNotEmpty()) {
            flushKey(path, current, segments)
        }
        if (segments.isEmpty()) {
            throw PropertiesParseException("Properties key must not be empty")
        }
        return segments
    }

    fun encodeKey(key: String): String = key.replace("%", "%25")
        .replace(".", "%2E")
        .replace("[", "%5B")
        .replace("]", "%5D")

    fun join(parent: String?, key: String): String {
        val encoded = encodeKey(key)
        return if (parent == null) encoded else "$parent.$encoded"
    }

    fun appendIndex(parent: String, index: Int): String = "$parent[$index]"

    fun rootIndex(index: Int): String = "[$index]"

    fun escapePropertyKey(key: String): String = buildString(key.length) {
        for (ch in key) {
            when (ch) {
                '\\' -> append("\\\\")
                ' ' -> append("\\ ")
                '=' -> append("\\=")
                ':' -> append("\\:")
                '#' -> append("\\#")
                '!' -> append("\\!")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u000C' -> append("\\f")
                else -> {
                    if (ch < ' ') {
                        append("\\u")
                        append(ch.code.toString(16).uppercase().padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
    }

    fun escapePropertyValue(value: String): String = buildString(value.length) {
        for ((index, ch) in value.withIndex()) {
            when (ch) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u000C' -> append("\\f")
                else -> {
                    if (ch < ' ' || (index == 0 && ch == ' ')) {
                        append("\\u")
                        append(ch.code.toString(16).uppercase().padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
    }

    private fun flushKey(
        path: String,
        current: StringBuilder,
        segments: MutableList<PropertiesPathSegment>,
    ) {
        if (current.isEmpty()) {
            throw PropertiesParseException("Empty path segment in key '$path'")
        }
        segments.add(PropertiesPathSegment.key(current.toString()))
        current.setLength(0)
    }

    private fun flushKeyIfNotEmpty(
        current: StringBuilder,
        segments: MutableList<PropertiesPathSegment>,
    ) {
        if (current.isNotEmpty()) {
            segments.add(PropertiesPathSegment.key(current.toString()))
            current.setLength(0)
        }
    }
}
