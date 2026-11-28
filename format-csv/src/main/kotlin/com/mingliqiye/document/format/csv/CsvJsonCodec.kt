package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import java.util.Base64

/**
 * 嵌套单元格使用的精简 JSON 编解码。
 *
 * 只服务于 CSV 嵌套单元格：读取时把 `{...}` / `[...]` 文本还原为节点树，写出时把节点树
 * 编码为紧凑 JSON 文本。二进制节点按 Base64 字符串编码。
 *
 * 解析失败抛出 [CsvParseException]，写出失败抛出 [CsvException]，与 CSV 其它错误保持一致。
 */
internal object CsvJsonCodec {

    /** 嵌套 JSON 最大深度。 */
    private const val MAX_DEPTH = 128

    private const val HEX = "0123456789abcdef"

    /**
     * 解析 JSON 文本。
     *
     * @param text JSON 文本
     * @return 文档树节点
     * @throws CsvParseException 文本不是合法 JSON 时抛出
     */
    fun parse(text: String): Node {
        val parser = Parser(text)
        val node = parser.parseValue(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) {
            throw CsvParseException("Invalid nested JSON: unexpected trailing content at index ${parser.index}")
        }
        return node
    }

    /**
     * 将节点编码为紧凑 JSON 文本。
     *
     * @param node 文档树节点
     * @return JSON 文本
     * @throws CsvException 节点深度超过上限时抛出
     */
    fun write(node: Node): String {
        val builder = StringBuilder()
        writeNode(node, builder, 0)
        return builder.toString()
    }

    private fun writeNode(node: Node, builder: StringBuilder, depth: Int) {
        if (depth > MAX_DEPTH) {
            throw CsvException("Nested CSV value exceeds max depth $MAX_DEPTH")
        }
        when (node) {
            is NullNode -> builder.append("null")
            is BooleanNode -> builder.append(if (node.value) "true" else "false")
            is NumberNode -> builder.append(node.raw)
            is StringNode -> writeString(node.value, builder)
            is BinaryNode -> writeString(Base64.getEncoder().encodeToString(node.value), builder)
            is ObjectNode -> {
                builder.append('{')
                var first = true
                for (entry in node.entries()) {
                    if (!first) {
                        builder.append(',')
                    }
                    first = false
                    writeString(entry.key, builder)
                    builder.append(':')
                    writeNode(entry.value, builder, depth + 1)
                }
                builder.append('}')
            }

            is ArrayNode -> {
                builder.append('[')
                var first = true
                for (element in node) {
                    if (!first) {
                        builder.append(',')
                    }
                    first = false
                    writeNode(element, builder, depth + 1)
                }
                builder.append(']')
            }
        }
    }

    private fun writeString(value: String, builder: StringBuilder) {
        builder.append('"')
        for (char in value) {
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (char < ' ') {
                    builder.append("\\u")
                    val code = char.code
                    builder.append(HEX[(code shr 12) and 0x0F])
                    builder.append(HEX[(code shr 8) and 0x0F])
                    builder.append(HEX[(code shr 4) and 0x0F])
                    builder.append(HEX[code and 0x0F])
                } else {
                    builder.append(char)
                }
            }
        }
        builder.append('"')
    }

    /** 递归下降 JSON 解析器。 */
    private class Parser(private val text: String) {

        var index: Int = 0
            private set

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isJsonWhitespace()) {
                index++
            }
        }

        fun parseValue(depth: Int): Node {
            if (depth > MAX_DEPTH) {
                throw fail("nesting exceeds max depth $MAX_DEPTH")
            }
            skipWhitespace()
            if (atEnd()) {
                throw fail("unexpected end of input")
            }
            return when (val char = text[index]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> StringNode.of(parseString())
                't' -> {
                    expectLiteral("true")
                    BooleanNode.of(true)
                }

                'f' -> {
                    expectLiteral("false")
                    BooleanNode.of(false)
                }

                'n' -> {
                    expectLiteral("null")
                    NullNode.INSTANCE
                }

                else -> if (char == '-' || char in '0'..'9') parseNumber() else throw fail("unexpected character '$char'")
            }
        }

        private fun parseObject(depth: Int): Node {
            index++
            val fields = LinkedHashMap<String, Node>()
            skipWhitespace()
            if (!atEnd() && text[index] == '}') {
                index++
                return ObjectNode.from(fields)
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[index] != '"') {
                    throw fail("expected object key string")
                }
                val key = parseString()
                skipWhitespace()
                if (atEnd() || text[index] != ':') {
                    throw fail("expected ':' after object key")
                }
                index++
                fields[key] = parseValue(depth + 1)
                skipWhitespace()
                if (atEnd()) {
                    throw fail("unterminated object")
                }
                when (text[index]) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return ObjectNode.from(fields)
                    }

                    else -> throw fail("expected ',' or '}'")
                }
            }
        }

        private fun parseArray(depth: Int): Node {
            index++
            val elements = ArrayList<Node>()
            skipWhitespace()
            if (!atEnd() && text[index] == ']') {
                index++
                return ArrayNode.from(elements)
            }
            while (true) {
                elements.add(parseValue(depth + 1))
                skipWhitespace()
                if (atEnd()) {
                    throw fail("unterminated array")
                }
                when (text[index]) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return ArrayNode.from(elements)
                    }

                    else -> throw fail("expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            index++
            val builder = StringBuilder()
            while (true) {
                if (atEnd()) {
                    throw fail("unterminated string")
                }
                val char = text[index++]
                when {
                    char == '"' -> return builder.toString()
                    char == '\\' -> parseEscape(builder)
                    char < ' ' -> throw fail("unescaped control character in string")
                    else -> builder.append(char)
                }
            }
        }

        private fun parseEscape(builder: StringBuilder) {
            if (atEnd()) {
                throw fail("unterminated escape sequence")
            }
            when (val escaped = text[index++]) {
                '"' -> builder.append('"')
                '\\' -> builder.append('\\')
                '/' -> builder.append('/')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> builder.append(parseUnicodeEscape())
                else -> throw fail("invalid escape '\\$escaped'")
            }
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > text.length) {
                throw fail("incomplete unicode escape")
            }
            var value = 0
            for (offset in 0 until 4) {
                val digit = hexValue(text[index + offset])
                value = (value shl 4) or digit
            }
            index += 4
            return value.toChar()
        }

        private fun hexValue(char: Char): Int = when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> throw fail("invalid unicode escape digit '$char'")
        }

        private fun parseNumber(): Node {
            val start = index
            if (index < text.length && text[index] == '-') {
                index++
            }
            if (atEnd()) {
                throw fail("incomplete number")
            }
            when {
                text[index] == '0' -> index++
                text[index] in '1'..'9' -> {
                    while (index < text.length && text[index] in '0'..'9') {
                        index++
                    }
                }

                else -> throw fail("invalid number")
            }
            if (index < text.length && text[index] == '.') {
                index++
                if (atEnd() || text[index] !in '0'..'9') {
                    throw fail("invalid number fraction")
                }
                while (index < text.length && text[index] in '0'..'9') {
                    index++
                }
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) {
                    index++
                }
                if (atEnd() || text[index] !in '0'..'9') {
                    throw fail("invalid number exponent")
                }
                while (index < text.length && text[index] in '0'..'9') {
                    index++
                }
            }
            return NumberNode.ofRaw(text.substring(start, index))
        }

        private fun expectLiteral(literal: String) {
            if (!text.startsWith(literal, index)) {
                throw fail("invalid literal")
            }
            index += literal.length
        }

        private fun fail(message: String): CsvParseException = CsvParseException("Invalid nested JSON at index $index: $message")
    }
}

private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'
