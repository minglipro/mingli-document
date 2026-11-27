package com.mingliqiye.document.format.json

import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DocumentToken
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType

private const val OBJECT_FIRST_FIELD = 0
private const val OBJECT_FIELD = 1
private const val OBJECT_VALUE = 2
private const val OBJECT_AFTER_VALUE = 3

private const val ARRAY_FIRST_VALUE = 0
private const val ARRAY_VALUE = 1
private const val ARRAY_AFTER_VALUE = 2

/**
 * JSON 文本 Token 读取器。
 *
 * 直接按 Token 生成，不构建 Node 树。
 */
internal class JsonTokenReader(
    private val source: String,
    private val allowComments: Boolean = true,
) : DocumentTokenReader {

    private var position = 0
    private var rootStarted = false
    private var rootFinished = false
    private val containers = ArrayList<JsonTokenContainer>()
    private var current: DocumentToken? = null

    override val currentToken: DocumentTokenType?
        get() = current?.type

    override val textValue: String?
        get() = current?.text

    override val booleanValue: Boolean?
        get() = current?.booleanValue

    override val binaryValue: ByteArray?
        get() = current?.binaryValue

    override fun nextToken(): DocumentTokenType? {
        if (rootFinished) {
            return null
        }
        while (true) {
            if (!rootStarted) {
                rootStarted = true
                return emit(parseValueStart())
            }
            val container = containers.lastOrNull()
            if (container == null) {
                rootFinished = true
                validateEnd()
                return null
            }
            if (container.array) {
                when (container.phase) {
                    ARRAY_FIRST_VALUE -> {
                        skipWhitespace()
                        if (consume(']')) {
                            containers.removeAt(containers.lastIndex)
                            return emit(DocumentToken.endArray())
                        }
                        container.phase = ARRAY_AFTER_VALUE
                        return emit(parseValueStart())
                    }

                    ARRAY_VALUE -> {
                        skipWhitespace()
                        expect(',')
                        skipWhitespace()
                        if (peek() == ']') {
                            throw error("Trailing comma in array")
                        }
                        container.phase = ARRAY_AFTER_VALUE
                        return emit(parseValueStart())
                    }

                    else -> {
                        skipWhitespace()
                        if (consume(']')) {
                            containers.removeAt(containers.lastIndex)
                            return emit(DocumentToken.endArray())
                        }
                        expect(',')
                        skipWhitespace()
                        if (peek() == ']') {
                            throw error("Trailing comma in array")
                        }
                        container.phase = ARRAY_FIRST_VALUE
                    }
                }
            } else {
                when (container.phase) {
                    OBJECT_FIRST_FIELD -> {
                        skipWhitespace()
                        if (consume('}')) {
                            containers.removeAt(containers.lastIndex)
                            return emit(DocumentToken.endObject())
                        }
                        container.phase = OBJECT_VALUE
                        return emit(parseFieldName())
                    }

                    OBJECT_FIELD -> {
                        skipWhitespace()
                        if (consume('}')) {
                            containers.removeAt(containers.lastIndex)
                            return emit(DocumentToken.endObject())
                        }
                        container.phase = OBJECT_VALUE
                        return emit(parseFieldName())
                    }

                    OBJECT_VALUE -> {
                        container.phase = OBJECT_AFTER_VALUE
                        return emit(parseValueStart())
                    }

                    else -> {
                        skipWhitespace()
                        if (consume('}')) {
                            containers.removeAt(containers.lastIndex)
                            return emit(DocumentToken.endObject())
                        }
                        expect(',')
                        skipWhitespace()
                        if (peek() == '}') {
                            throw error("Trailing comma in object")
                        }
                        container.phase = OBJECT_FIRST_FIELD
                    }
                }
            }
        }
    }

    private fun parseValueStart(): DocumentToken {
        skipWhitespace()
        if (!hasNext()) {
            throw error("Unexpected end of input")
        }
        return when (val ch = source[position]) {
            '{' -> {
                position++
                containers.add(JsonTokenContainer(array = false))
                DocumentToken.startObject()
            }

            '[' -> {
                position++
                containers.add(JsonTokenContainer(array = true))
                DocumentToken.startArray()
            }

            '"' -> DocumentToken.string(parseString())

            't' -> {
                expectLiteral("true")
                DocumentToken.boolean(true)
            }

            'f' -> {
                expectLiteral("false")
                DocumentToken.boolean(false)
            }

            'n' -> {
                expectLiteral("null")
                DocumentToken.nullValue()
            }

            '-', in '0'..'9' -> DocumentToken.number(parseNumberRaw())

            else -> throw error("Unexpected character '$ch'")
        }
    }

    private fun parseFieldName(): DocumentToken {
        if (!hasNext() || source[position] != '"') {
            throw error("Expected object key")
        }
        val key = parseString()
        skipWhitespace()
        expect(':')
        return DocumentToken.fieldName(key)
    }

    private fun parseString(): String {
        expect('"')
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

    private fun parseNumberRaw(): String {
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

    private fun consumeDigits() {
        while (hasNext() && source[position].isDigit()) {
            position++
        }
    }

    private fun skipWhitespace() {
        while (hasNext()) {
            val ch = source[position]
            when {
                ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' -> position++
                allowComments && ch == '/' -> {
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

    private fun expectLiteral(literal: String) {
        if (!source.startsWith(literal, position)) {
            throw error("Expected '$literal'")
        }
        position += literal.length
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

    private fun peek(): Char? = if (hasNext()) source[position] else null

    private fun hasNext(): Boolean = position < source.length

    private fun validateEnd() {
        skipWhitespace()
        if (hasNext()) {
            throw error("Unexpected trailing content")
        }
    }

    private fun emit(token: DocumentToken): DocumentTokenType {
        current = token
        return token.type
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
