package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import java.util.LinkedHashMap

private const val MAX_JSON_DEPTH = 512

/**
 * JSON/JSONC 递归下降解析器。
 *
 * 由 [JsonReader] 调用；不对外暴露。
 *
 * 语法本身保持严格（拒绝尾逗号、单引号、非法数字等）；在 [allowComments]
 * 为 `true` 时，`skipWhitespace` 会额外跳过多余空白、`//` 行注释和块注释。
 */
internal class JsonParser(
    private val source: String,
    private val allowComments: Boolean,
) {

    private var position = 0
    private var depth = 0

    /**
     * 解析一个完整 JSON 值，并确保之后只剩空白字符。
     */
    fun parse(): Node {
        skipWhitespace()
        if (!hasNext()) {
            throw error("Unexpected end of input")
        }
        val node = parseValue()
        skipWhitespace()
        if (hasNext()) {
            throw error("Unexpected trailing content")
        }
        return node
    }

    private fun parseValue(): Node {
        if (!hasNext()) {
            throw error("Unexpected end of input")
        }
        return when (val ch = source[position]) {
            '{' -> parseObject()

            '[' -> parseArray()

            '"' -> StringNode(parseString())

            't' -> {
                expectLiteral("true")
                BooleanNode(true)
            }

            'f' -> {
                expectLiteral("false")
                BooleanNode(false)
            }

            'n' -> {
                expectLiteral("null")
                NullNode
            }

            '-', in '0'..'9' -> parseNumber()

            else -> throw error("Unexpected character '$ch'")
        }
    }

    private fun parseObject(): Node {
        expect('{')
        enter()
        try {
            val fields = LinkedHashMap<String, Node>()
            skipWhitespace()
            if (consume('}')) {
                return ObjectNode.from(fields)
            }
            while (true) {
                skipWhitespace()
                if (!hasNext() || source[position] != '"') {
                    throw error("Expected object key")
                }
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                fields[key] = parseValue()
                skipWhitespace()
                when {
                    consume(',') -> {
                        skipWhitespace()
                        if (peek() == '}') {
                            throw error("Trailing comma in object")
                        }
                    }

                    consume('}') -> return ObjectNode.from(fields)

                    else -> throw error("Expected ',' or '}' in object")
                }
            }
        } finally {
            depth--
        }
    }

    private fun parseArray(): Node {
        expect('[')
        enter()
        try {
            val elements = ArrayList<Node>()
            skipWhitespace()
            if (consume(']')) {
                return ArrayNode.from(elements)
            }
            while (true) {
                skipWhitespace()
                elements.add(parseValue())
                skipWhitespace()
                when {
                    consume(',') -> {
                        skipWhitespace()
                        if (peek() == ']') {
                            throw error("Trailing comma in array")
                        }
                    }

                    consume(']') -> return ArrayNode.from(elements)

                    else -> throw error("Expected ',' or ']' in array")
                }
            }
        } finally {
            depth--
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            if (!hasNext()) {
                throw error("Unterminated string")
            }
            when (val ch = source[position++]) {
                '"' -> return builder.toString()

                '\\' -> builder.append(parseEscape())

                else -> {
                    if (ch < ' ') {
                        throw error("Unescaped control character in string")
                    }
                    builder.append(ch)
                }
            }
        }
    }

    private fun parseEscape(): Char {
        if (!hasNext()) {
            throw error("Unterminated escape sequence")
        }
        return when (val escaped = source[position++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> throw error("Invalid escape sequence: \\$escaped")
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (position + 4 > source.length) {
            throw error("Incomplete unicode escape")
        }
        var code = 0
        repeat(4) {
            val digit = Character.digit(source[position++], 16)
            if (digit < 0) {
                throw error("Invalid unicode escape")
            }
            code = code * 16 + digit
        }
        return code.toChar()
    }

    private fun parseNumber(): NumberNode {
        val start = position
        consume('-')

        if (!hasNext()) {
            throw error("Unexpected end of number")
        }

        when (val first = source[position]) {
            '0' -> {
                position++
                if (hasNext() && source[position].isDigit()) {
                    throw error("Leading zero is not allowed")
                }
            }

            in '1'..'9' -> consumeDigits()

            else -> throw error("Invalid number: unexpected '$first'")
        }

        if (consume('.')) {
            if (!hasNext() || !source[position].isDigit()) {
                throw error("Expected digit after decimal point")
            }
            consumeDigits()
        }

        if (consume('e') || consume('E')) {
            if (hasNext() && (source[position] == '+' || source[position] == '-')) {
                position++
            }
            if (!hasNext() || !source[position].isDigit()) {
                throw error("Expected exponent digits")
            }
            consumeDigits()
        }

        return NumberNode.ofRaw(source.substring(start, position))
    }

    private fun consumeDigits() {
        while (hasNext() && source[position].isDigit()) {
            position++
        }
    }

    private fun expect(ch: Char) {
        if (!consume(ch)) {
            throw error("Expected '$ch'")
        }
    }

    private fun expectLiteral(literal: String) {
        if (!source.startsWith(literal, position)) {
            throw error("Expected '$literal'")
        }
        position += literal.length
    }

    private fun consume(ch: Char): Boolean {
        if (hasNext() && source[position] == ch) {
            position++
            return true
        }
        return false
    }

    /**
     * 跳过空白和（可选）注释。
     */
    private fun skipWhitespace() {
        while (hasNext()) {
            when {
                isWhitespace(source[position]) -> position++
                allowComments && source[position] == '/' -> {
                    if (!skipComment()) {
                        return
                    }
                }

                else -> return
            }
        }
    }

    /**
     * 跳过一条注释。
     *
     * @return 是否识别到注释；`/` 后面不是 `/` 或 `*` 时返回 `false`
     */
    private fun skipComment(): Boolean {
        if (position + 1 >= source.length) {
            return false
        }
        return when (source[position + 1]) {
            '/' -> {
                position += 2
                while (hasNext() && source[position] != '\n' && source[position] != '\r') {
                    position++
                }
                true
            }

            '*' -> {
                position += 2
                skipBlockComment()
                true
            }

            else -> false
        }
    }

    /**
     * 跳过块注释，未闭合时抛错。
     */
    private fun skipBlockComment() {
        while (hasNext()) {
            if (source[position] == '*' &&
                position + 1 < source.length &&
                source[position + 1] == '/'
            ) {
                position += 2
                return
            }
            position++
        }
        throw error("Unterminated block comment")
    }

    /**
     * 判断是否为可忽略空白。
     *
     * 除标准 JSON 的 space、tab、CR、LF 外，还兼容常见 Unicode 空白和 BOM。
     */
    private fun isWhitespace(ch: Char): Boolean {
        if (ch.isWhitespace()) {
            return true
        }
        return when (ch) {
            '\u000B', '\u000C', '\u00A0', '\u1680' -> true
            '\u2028', '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF' -> true
            else -> ch in '\u2000'..'\u200A'
        }
    }

    private fun peek(): Char? = if (hasNext()) source[position] else null

    private fun hasNext(): Boolean = position < source.length

    private fun enter() {
        depth++
        if (depth > MAX_JSON_DEPTH) {
            throw error("Maximum nesting depth of $MAX_JSON_DEPTH exceeded")
        }
    }

    private fun error(message: String): JsonParseException {
        var line = 1
        var column = 1
        for (index in 0 until position.coerceAtMost(source.length)) {
            if (source[index] == '\n') {
                line++
                column = 1
            } else {
                column++
            }
        }
        return JsonParseException("$message at line $line, column $column")
    }
}
