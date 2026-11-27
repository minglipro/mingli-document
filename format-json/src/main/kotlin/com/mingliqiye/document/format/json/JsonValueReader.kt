package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DocumentValueReader
import java.util.LinkedHashMap

/**
 * 事件式 JSON 读取器。
 *
 * 直接向 Mapper 提供 token，不预先构建完整 Node 树；
 * 转换器需要时通过 [readNode] 读取局部子树。
 */
internal class JsonValueReader(
    private val source: String,
) : DocumentValueReader {

    private var position = 0
    private var objectFirst = false
    private var arrayFirst = false
    private var pendingFieldName: String? = null

    override fun tryReadNull(): Boolean {
        skipWhitespace()
        if (source.startsWith("null", position)) {
            position += 4
            return true
        }
        return false
    }

    override fun readString(): String {
        skipWhitespace()
        return parseString()
    }

    override fun readNumberRaw(): String {
        skipWhitespace()
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
        return source.substring(start, position)
    }

    override fun readBoolean(): Boolean {
        skipWhitespace()
        return when {
            source.startsWith("true", position) -> {
                position += 4
                true
            }

            source.startsWith("false", position) -> {
                position += 5
                false
            }

            else -> throw error("Expected boolean")
        }
    }

    override fun beginObject() {
        skipWhitespace()
        expect('{')
        objectFirst = true
    }

    override fun hasNextField(): Boolean {
        skipWhitespace()
        if (peek() == '}') {
            return false
        }
        if (objectFirst) {
            objectFirst = false
        } else {
            expect(',')
            skipWhitespace()
            if (peek() == '}') {
                throw error("Trailing comma in object")
            }
        }
        if (!hasNext() || peek() != '"') {
            throw error("Expected object key")
        }
        pendingFieldName = parseString()
        skipWhitespace()
        expect(':')
        skipWhitespace()
        return true
    }

    override fun readFieldName(): String = pendingFieldName
        ?: throw error("No pending field name")

    override fun endObject() {
        skipWhitespace()
        expect('}')
    }

    override fun beginArray() {
        skipWhitespace()
        expect('[')
        arrayFirst = true
    }

    override fun hasNextElement(): Boolean {
        skipWhitespace()
        if (peek() == ']') {
            return false
        }
        if (arrayFirst) {
            arrayFirst = false
        } else {
            expect(',')
            skipWhitespace()
            if (peek() == ']') {
                throw error("Trailing comma in array")
            }
        }
        return true
    }

    override fun endArray() {
        skipWhitespace()
        expect(']')
    }

    override fun skipValue() {
        skipWhitespace()
        if (tryReadNull()) {
            return
        }
        when (peek()) {
            '{' -> {
                beginObject()
                while (hasNextField()) {
                    readFieldName()
                    skipValue()
                }
                endObject()
            }

            '[' -> {
                beginArray()
                while (hasNextElement()) {
                    skipValue()
                }
                endArray()
            }

            '"' -> parseString()
            't', 'f' -> readBoolean()
            '-', in '0'..'9' -> readNumberRaw()
            else -> throw error("Unexpected character '${peek()}'")
        }
    }

    override fun readNode(): Node {
        skipWhitespace()
        if (tryReadNull()) {
            return NullNode.INSTANCE
        }
        return when (peek()) {
            '{' -> {
                beginObject()
                val fields = LinkedHashMap<String, Node>(8)
                while (hasNextField()) {
                    fields[readFieldName()] = readNode()
                }
                endObject()
                ObjectNode.from(fields)
            }

            '[' -> {
                beginArray()
                val elements = ArrayList<Node>(8)
                while (hasNextElement()) {
                    elements.add(readNode())
                }
                endArray()
                ArrayNode.from(elements)
            }

            '"' -> StringNode.of(parseString())
            't', 'f' -> BooleanNode.of(readBoolean())
            '-', in '0'..'9' -> NumberNode.ofRaw(readNumberRaw())
            else -> throw error("Unexpected character '${peek()}'")
        }
    }

    private fun parseString(): String {
        if (!hasNext() || source[position] != '"') {
            throw error("Expected string")
        }
        position++
        val start = position
        var index = position
        while (index < source.length) {
            val ch = source[index]
            when {
                ch == '"' -> {
                    position = index + 1
                    return source.substring(start, index)
                }

                ch == '\\' -> break
                ch < ' ' -> throw error("Unescaped control character in string")
                else -> index++
            }
        }

        position = index
        val builder = StringBuilder(index - start + 16)
        builder.append(source, start, index)
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

    private fun skipWhitespace() {
        while (hasNext()) {
            val ch = source[position]
            when {
                ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' -> position++
                ch == '/' -> {
                    if (!skipComment()) {
                        return
                    }
                }

                isWhitespace(ch) -> position++
                else -> return
            }
        }
    }

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
                while (hasNext()) {
                    if (source[position] == '*' &&
                        position + 1 < source.length &&
                        source[position + 1] == '/'
                    ) {
                        position += 2
                        return true
                    }
                    position++
                }
                throw error("Unterminated block comment")
            }

            else -> false
        }
    }

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

    private fun expect(ch: Char) {
        if (!consume(ch)) {
            throw error("Expected '$ch'")
        }
    }

    private fun consume(ch: Char): Boolean {
        if (hasNext() && source[position] == ch) {
            position++
            return true
        }
        return false
    }

    private fun consumeDigits() {
        while (hasNext() && source[position].isDigit()) {
            position++
        }
    }

    private fun peek(): Char? = if (hasNext()) source[position] else null

    private fun hasNext(): Boolean = position < source.length

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
