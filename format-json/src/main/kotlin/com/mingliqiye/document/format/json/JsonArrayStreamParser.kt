package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.exception.JsonParseException
import java.io.BufferedReader
import java.io.Reader

/** `peek()` 内部标记：当前没有预读字符（-1 表示 EOF）。 */
private const val NO_CHAR = -2

/**
 * JSON 顶层数组流式扫描器（内部实现）。
 *
 * 从 [Reader] 逐字符读取，按“数组元素边界”切分后交给 [JsonParser] 完整校验并构建
 * 单个元素的 [Node]；任意时刻内存中只有当前元素文本，不把整个数组读成 String。
 *
 * 语法规则与 [JsonParser] 保持一致：
 * - 允许任意 JSON 值作为元素（对象/数组/字符串/数字/布尔/null）；
 * - [allowComments] 为 `true` 时支持 JSONC 行注释与块注释（元素之间、元素内部均可）；
 * - 元素文本仍由 [JsonParser] 做严格语法、重复 key 与深度校验；
 * - 支持扩展空白与 UTF-8 BOM（与 [JsonParser] 的空白集合一致）。
 *
 * @param reader 字符流；调用方负责关闭
 * @param allowComments 是否允许 JSONC 注释
 * @param duplicateKeyPolicy 对象内重复 key 策略
 * @param maxDepth 对象/数组最大嵌套深度
 */
internal class JsonArrayStreamParser(
    reader: Reader,
    private val allowComments: Boolean,
    private val duplicateKeyPolicy: DuplicateKeyPolicy,
    private val maxDepth: Int,
) {

    private val reader: BufferedReader = bufferedReader(reader)
    private var current: Int = NO_CHAR
    private var line: Long = 1L
    private var column: Long = 0L

    /**
     * 解析顶层数组并逐元素回调。
     *
     * @param sizeProbe 返回已从底层输入读取的字节/字符数
     * @param checkSize 校验累计输入大小
     * @param consumer 元素回调；异常原样向上传播
     */
    fun forEachValue(
        sizeProbe: () -> Long,
        checkSize: (Long) -> Unit,
        consumer: (Node) -> Unit,
    ) {
        skipWhitespaceAndComments()
        checkSize(sizeProbe())
        val first = peek()
        if (first < 0) {
            throw error("Unexpected end of input")
        }
        if (first != '['.code) {
            throw error("Expected '[' at top level")
        }
        take()
        var count = 0L
        while (true) {
            skipWhitespaceAndComments()
            checkSize(sizeProbe())
            val next = peek()
            if (next < 0) {
                throw error("Unexpected end of input")
            }
            if (next == ']'.code) {
                take()
                break
            }
            if (count > 0) {
                if (next != ','.code) {
                    throw error("Expected ',' or ']' in array")
                }
                take()
                skipWhitespaceAndComments()
                if (peek() == ']'.code) {
                    throw error("Trailing comma in array")
                }
            }
            val elementLine = line
            val text = readValueText()
            val node = try {
                JsonParser(text, allowComments, duplicateKeyPolicy, maxDepth).parse()
            } catch (e: JsonParseException) {
                throw JsonParseException("Invalid JSON array element at line $elementLine: ${e.message}", e)
            }
            consumer(node)
            count++
        }
        skipWhitespaceAndComments()
        if (peek() >= 0) {
            throw error("Unexpected trailing content")
        }
    }

    /** 读取一个完整元素的原始文本（不含首尾分隔符）。 */
    private fun readValueText(): String {
        val ch = peek()
        return when {
            ch < 0 -> throw error("Unexpected end of input")
            ch == '"'.code -> readStringValue()
            ch == '{'.code || ch == '['.code -> readContainerValue()
            ch == 't'.code || ch == 'f'.code || ch == 'n'.code -> readLiteralValue()
            ch == '-'.code || ch in '0'.code..'9'.code -> readNumberValue()
            else -> throw error("Unexpected character '${ch.toChar()}'")
        }
    }

    /** 读取一个字符串元素，处理转义并复制原始文本。 */
    private fun readStringValue(): String {
        val builder = StringBuilder()
        val open = take()
        builder.append(open.toChar())
        while (true) {
            val ch = peek()
            if (ch < 0) {
                throw error("Unterminated string")
            }
            take()
            builder.append(ch.toChar())
            when {
                ch == '\\'.code -> {
                    val escaped = peek()
                    if (escaped < 0) {
                        throw error("Unterminated string")
                    }
                    take()
                    builder.append(escaped.toChar())
                }

                ch == '"'.code -> return builder.toString()
                ch < ' '.code -> throw error("Unescaped control character in string")
            }
        }
    }

    /** 读取对象/数组元素，按括号深度与字符串状态确定边界。 */
    private fun readContainerValue(): String {
        val builder = StringBuilder()
        val open = take()
        builder.append(open.toChar())
        var depth = 1
        var inString = false
        var escaped = false
        while (true) {
            val ch = peek()
            if (ch < 0) {
                throw error("Unexpected end of input")
            }
            take()
            builder.append(ch.toChar())
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\'.code -> escaped = true
                    ch == '"'.code -> inString = false
                }
                continue
            }
            when (ch) {
                '"'.code -> inString = true
                '{'.code, '['.code -> depth++
                '}'.code, ']'.code -> {
                    depth--
                    if (depth == 0) {
                        return builder.toString()
                    }
                }

                '/'.code -> if (allowComments) {
                    copyCommentInto(builder)
                }
            }
        }
    }

    /**
     * 把已消费的 `/` 之后的注释文本复制到元素文本中。
     *
     * `/` 后不是 `/` 或 `*` 时不做任何事（保留后续字符给元素解析器报错）。
     */
    private fun copyCommentInto(builder: StringBuilder) {
        when (peek()) {
            '/'.code -> {
                take()
                builder.append('/')
                while (true) {
                    val ch = peek()
                    if (ch < 0) {
                        return
                    }
                    take()
                    builder.append(ch.toChar())
                    if (ch == '\n'.code || ch == '\r'.code) {
                        return
                    }
                }
            }

            '*'.code -> {
                take()
                builder.append('*')
                var previousStar = false
                while (true) {
                    val ch = peek()
                    if (ch < 0) {
                        throw error("Unterminated block comment")
                    }
                    take()
                    builder.append(ch.toChar())
                    if (previousStar && ch == '/'.code) {
                        return
                    }
                    previousStar = ch == '*'.code
                }
            }

            else -> Unit
        }
    }

    /** 读取 `true` / `false` / `null` 字面量元素（由 [JsonParser] 严格校验）。 */
    private fun readLiteralValue(): String {
        val builder = StringBuilder()
        while (true) {
            val ch = peek()
            if (ch < 0 || !isAsciiLetter(ch)) {
                return builder.toString()
            }
            take()
            builder.append(ch.toChar())
        }
    }

    /** 读取数字元素（由 [JsonParser] 严格校验语法）。 */
    private fun readNumberValue(): String {
        val builder = StringBuilder()
        while (true) {
            val ch = peek()
            if (!isNumberChar(ch)) {
                return builder.toString()
            }
            take()
            builder.append(ch.toChar())
        }
    }

    /** 跳过空白与（可选）注释。 */
    private fun skipWhitespaceAndComments() {
        while (true) {
            val ch = peek()
            when {
                ch < 0 -> return
                isJsonWhitespace(ch) -> take()
                allowComments && ch == '/'.code -> {
                    take()
                    when (peek()) {
                        '/'.code -> {
                            take()
                            while (true) {
                                val next = peek()
                                if (next < 0 || next == '\n'.code || next == '\r'.code) {
                                    break
                                }
                                take()
                            }
                        }

                        '*'.code -> {
                            take()
                            var previousStar = false
                            while (true) {
                                val next = peek()
                                if (next < 0) {
                                    throw error("Unterminated block comment")
                                }
                                take()
                                if (previousStar && next == '/'.code) {
                                    break
                                }
                                previousStar = next == '*'.code
                            }
                        }

                        else -> throw error("Unexpected character '/'")
                    }
                }

                else -> return
            }
        }
    }

    /** 预读下一个字符；不消费，遇到 EOF 返回 `-1`。 */
    private fun peek(): Int {
        if (current == NO_CHAR) {
            current = reader.read()
            if (current >= 0) {
                if (current == '\n'.code) {
                    line++
                    column = 0
                } else {
                    column++
                }
            }
        }
        return current
    }

    /** 消费下一个字符；遇到 EOF 返回 `-1`。 */
    private fun take(): Int {
        val ch = peek()
        if (ch >= 0) {
            current = NO_CHAR
        }
        return ch
    }

    /** 判断是否为可忽略空白，集合与 [JsonParser] 保持一致。 */
    private fun isJsonWhitespace(ch: Int): Boolean {
        if (ch < 0) {
            return false
        }
        val value = ch.toChar()
        if (value.isWhitespace()) {
            return true
        }
        return when (value) {
            '\u000B', '\u000C', '\u00A0', '\u1680' -> true
            '\u2028', '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF' -> true
            else -> value in '\u2000'..'\u200A'
        }
    }

    private fun isAsciiLetter(ch: Int): Boolean = ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code

    private fun isNumberChar(ch: Int): Boolean = ch >= 0 &&
        (
            ch in '0'.code..'9'.code ||
                ch == '+'.code ||
                ch == '-'.code ||
                ch == '.'.code ||
                ch == 'e'.code ||
                ch == 'E'.code
            )

    private fun error(message: String): JsonParseException = JsonParseException("$message at line $line, column ${column.coerceAtLeast(1L)}")
}
