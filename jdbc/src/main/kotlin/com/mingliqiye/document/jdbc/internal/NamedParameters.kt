package com.mingliqiye.document.jdbc.internal

import java.util.ArrayList

/**
 * 命名参数解析：从 SQL 文本中按出现顺序提取 `:name` 形式的参数名。
 *
 * 解析时跳过：
 * - 单引号字符串字面量（支持 `''` 转义）；
 * - 双引号 / 反引号包裹的标识符（支持双写转义）；
 * - 行注释与块注释；
 * - PostgreSQL 风格的类型转换 `::`。
 *
 * 参数名以字母或下划线开头，后续允许字母、数字与下划线。不支持 PostgreSQL 的
 * dollar-quoted 字符串（`$tag$...$tag$`）。
 */
internal object NamedParameters {

    /**
     * 解析 SQL 中的命名参数。
     *
     * @param sql SQL 文本
     * @return 按占位符出现顺序排列的参数名（允许重复）
     */
    internal fun parse(sql: String): List<String> {
        val names = ArrayList<String>()
        var index = 0
        while (index < sql.length) {
            val char = sql[index]
            when {
                char == '\'' -> index = skipQuoted(sql, index, '\'')
                char == '"' -> index = skipQuoted(sql, index, '"')
                char == '`' -> index = skipQuoted(sql, index, '`')
                char == '-' && sql.getOrNull(index + 1) == '-' -> index = skipLineComment(sql, index)
                char == '/' && sql.getOrNull(index + 1) == '*' -> index = skipBlockComment(sql, index)
                char == ':' -> {
                    if (sql.getOrNull(index + 1) == ':') {
                        index += 2
                        continue
                    }
                    val end = parameterEnd(sql, index + 1)
                    if (end > index + 1) {
                        names.add(sql.substring(index + 1, end))
                        index = end
                        continue
                    }
                    index++
                }

                else -> index++
            }
        }
        return names
    }

    private fun parameterEnd(sql: String, start: Int): Int {
        if (start >= sql.length) {
            return start
        }
        val first = sql[start]
        if (!first.isLetter() && first != '_') {
            return start
        }
        var index = start + 1
        while (index < sql.length) {
            val char = sql[index]
            if (!char.isLetterOrDigit() && char != '_') {
                break
            }
            index++
        }
        return index
    }

    private fun skipQuoted(sql: String, start: Int, quote: Char): Int {
        var index = start + 1
        while (index < sql.length) {
            if (sql[index] == quote) {
                if (sql.getOrNull(index + 1) == quote) {
                    index += 2
                    continue
                }
                return index + 1
            }
            index++
        }
        return sql.length
    }

    private fun skipLineComment(sql: String, start: Int): Int {
        var index = start + 2
        while (index < sql.length) {
            if (sql[index] == '\n' || sql[index] == '\r') {
                return index
            }
            index++
        }
        return sql.length
    }

    private fun skipBlockComment(sql: String, start: Int): Int {
        var index = start + 2
        while (index < sql.length - 1) {
            if (sql[index] == '*' && sql[index + 1] == '/') {
                return index + 2
            }
            index++
        }
        return sql.length
    }
}
