package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.exception.JsonParseException
import java.util.LinkedHashMap

/**
 * JSON/JSONC 递归下降解析器。
 *
 * 由 [JsonReader] 调用；不对外暴露。
 *
 * 语法本身保持严格（默认拒绝尾逗号、单引号、非法数字等）；在 [allowComments]
 * 为 `true` 时，`skipWhitespace` 会额外跳过多余空白、`//` 行注释和块注释。
 * [readFeatures] 中的 [JsonReadFeature] 可逐项放宽语法，语义对齐 上游实现 2.15.3。
 *
 * @param source JSON 文本
 * @param allowComments 是否允许 JSONC 注释
 * @param duplicateKeyPolicy 对象内重复 key 策略
 * @param maxDepth 对象/数组最大嵌套深度
 * @param initialDepth 起始嵌套深度，供 [JsonTokenReader] 跳过重复字段值时复用
 * @param readFeatures 显式开启的 JSON 读取特性
 */
internal class JsonParser(
    private val source: String,
    private val allowComments: Boolean,
    private val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS,
    private val maxDepth: Int = JsonConfig.DEFAULT_MAX_DEPTH,
    initialDepth: Int = 0,
    readFeatures: Set<JsonReadFeature> = JsonConfig.DEFAULT_READ_FEATURES,
) {

    private val features = JsonReadOptions(readFeatures)
    private val commentsEnabled = allowComments || features.allowJavaComments
    private val keyPolicy = if (features.strictDuplicateDetection) DuplicateKeyPolicy.FAIL else duplicateKeyPolicy
    private var position = 0
    private var depth = initialDepth

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
        val ch = source[position]
        if (features.allowNonNumericNumbers) {
            JsonReadOptions.matchNonNumeric(source, position)?.let { (literal, length) ->
                position += length
                return NumberNode.ofRaw(literal)
            }
        }
        return when {
            ch == '{' -> parseObject()
            ch == '[' -> parseArray()
            ch == '"' -> StringNode.of(parseString())
            ch == '\'' && features.allowSingleQuotes -> StringNode.of(parseString())
            ch == 't' -> {
                expectLiteral("true")
                BooleanNode.of(true)
            }

            ch == 'f' -> {
                expectLiteral("false")
                BooleanNode.of(false)
            }

            ch == 'n' -> {
                expectLiteral("null")
                NullNode.INSTANCE
            }

            ch == '+' && features.allowLeadingPlusSignForNumbers -> parseNumber()
            ch == '.' && features.allowLeadingDecimalPointForNumbers -> parseNumber()
            ch == '-' || ch in '0'..'9' -> parseNumber()
            else -> throw error("Unexpected character '$ch'")
        }
    }

    private fun parseObject(): Node {
        expect('{')
        enter()
        try {
            val fields = LinkedHashMap<String, Node>(8)
            skipWhitespace()
            if (consume('}')) {
                return ObjectNode.from(fields)
            }
            while (true) {
                skipWhitespace()
                val key = parseFieldName()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                parseFieldValue(fields, key)
                skipWhitespace()
                when {
                    consume(',') -> {
                        skipWhitespace()
                        if (peek() == '}') {
                            if (!features.allowTrailingComma) {
                                throw error("Trailing comma in object")
                            }
                            position++
                            return ObjectNode.from(fields)
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

    private fun parseFieldName(): String {
        if (!hasNext()) {
            throw error("Expected object key")
        }
        return when {
            source[position] == '"' -> parseString()
            source[position] == '\'' && features.allowSingleQuotes -> parseString()
            features.allowUnquotedFieldNames -> parseUnquotedFieldName()
            else -> throw error("Expected object key")
        }
    }

    private fun parseUnquotedFieldName(): String {
        val start = position
        while (hasNext()) {
            val ch = source[position]
            if (ch.isWhitespace() ||
                ch == ':' ||
                ch == ',' ||
                ch == '}' ||
                ch == ']' ||
                (commentsEnabled && ch == '/') ||
                (features.allowYamlComments && ch == '#')
            ) {
                break
            }
            position++
        }
        if (position == start) {
            throw error("Expected object key")
        }
        return source.substring(start, position)
    }

    /**
     * 按重复 key 策略解析并写入一个对象字段值。
     *
     * @param fields 已解析字段
     * @param key 当前字段名
     */
    private fun parseFieldValue(fields: LinkedHashMap<String, Node>, key: String) {
        when (keyPolicy) {
            DuplicateKeyPolicy.LAST_WINS -> fields[key] = parseValue()
            DuplicateKeyPolicy.FIRST_WINS -> {
                if (key in fields) {
                    parseValue()
                } else {
                    fields[key] = parseValue()
                }
            }

            DuplicateKeyPolicy.FAIL -> {
                if (key in fields) {
                    throw error("Duplicate field \"$key\"")
                }
                fields[key] = parseValue()
            }
        }
    }

    /**
     * 从指定偏移量跳过（解析并丢弃）一个 JSON 值。
     *
     * 供 [JsonTokenReader] 在 `FIRST_WINS` 策略下跳过重复字段值；仍完整校验语法与深度。
     *
     * @param offset 值在源文本中的起始偏移量
     * @return 值之后的偏移量
     */
    internal fun skipValueAt(offset: Int): Int {
        position = offset
        parseValue()
        return position
    }

    private fun parseArray(): Node {
        expect('[')
        enter()
        try {
            val elements = ArrayList<Node>(8)
            skipWhitespace()
            if (consume(']')) {
                return ArrayNode.from(elements)
            }
            while (true) {
                skipWhitespace()
                if (features.allowMissingValues && peek() == ',') {
                    elements.add(NullNode.INSTANCE)
                    position++
                    skipWhitespace()
                    continue
                }
                if (features.allowMissingValues && peek() == ']') {
                    elements.add(NullNode.INSTANCE)
                    position++
                    return ArrayNode.from(elements)
                }
                elements.add(parseValue())
                skipWhitespace()
                when {
                    consume(',') -> {
                        skipWhitespace()
                        if (peek() == ']') {
                            if (features.allowTrailingComma) {
                                position++
                                return ArrayNode.from(elements)
                            }
                            if (!features.allowMissingValues) {
                                throw error("Trailing comma in array")
                            }
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
        val quote = source[position]
        if (quote != '"' && !(quote == '\'' && features.allowSingleQuotes)) {
            throw error("Expected string")
        }
        position++
        val start = position
        var index = position
        while (index < source.length) {
            val ch = source[index]
            when {
                ch == quote -> {
                    position = index + 1
                    return source.substring(start, index)
                }

                ch == '\\' -> break
                ch < ' ' && !features.allowUnescapedControlChars -> throw error("Unescaped control character in string")
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
                quote -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> {
                    if (ch < ' ' && !features.allowUnescapedControlChars) {
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
        val escaped = source[position++]
        return when (escaped) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            '\'' -> if (features.allowSingleQuotes || features.allowBackslashEscapingAnyCharacter) {
                '\''
            } else {
                throw error("Invalid escape sequence: \\$escaped")
            }

            else -> if (features.allowBackslashEscapingAnyCharacter) {
                escaped
            } else {
                throw error("Invalid escape sequence: \\$escaped")
            }
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
        if (hasNext() && source[position] == '+') {
            if (!features.allowLeadingPlusSignForNumbers) {
                throw error("Leading plus sign is not allowed")
            }
            position++
            if (hasNext() && source[position] == '-') {
                throw error("Invalid number: unexpected '-'")
            }
        } else {
            consume('-')
        }

        if (!hasNext()) {
            throw error("Unexpected end of number")
        }

        var digits = 0
        when (val first = source[position]) {
            '0' -> {
                position++
                digits++
                if (hasNext() && source[position].isDigit()) {
                    if (!features.allowLeadingZerosForNumbers) {
                        throw error("Leading zero is not allowed")
                    }
                    consumeDigits()
                }
            }

            in '1'..'9' -> {
                consumeDigits()
                digits++
            }

            '.' -> if (!features.allowLeadingDecimalPointForNumbers) {
                throw error("Invalid number: unexpected '$first'")
            }

            else -> throw error("Invalid number: unexpected '$first'")
        }

        if (consume('.')) {
            if (hasNext() && source[position].isDigit()) {
                consumeDigits()
                digits++
            } else if (!features.allowTrailingDecimalPointForNumbers) {
                throw error("Expected digit after decimal point")
            }
        }

        if (digits == 0) {
            throw error("Invalid number")
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
            val ch = source[position]
            when {
                ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' -> position++
                commentsEnabled && ch == '/' -> {
                    if (!skipComment()) {
                        return
                    }
                }

                features.allowYamlComments && ch == '#' -> skipYamlComment()
                isWhitespace(ch) -> position++
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
     * 跳过 YAML 风格 `#` 行注释（到行尾）。
     */
    private fun skipYamlComment() {
        while (hasNext() && source[position] != '\n' && source[position] != '\r') {
            position++
        }
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
        if (depth > maxDepth) {
            throw error("Maximum nesting depth of $maxDepth exceeded")
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
