package com.mingliqiye.document.format.json

import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import java.math.BigDecimal

private const val OBJECT_FIRST_FIELD = 0
private const val OBJECT_VALUE = 2
private const val OBJECT_AFTER_VALUE = 3

private const val ARRAY_FIRST_VALUE = 0
private const val ARRAY_AFTER_VALUE = 2

/**
 * JSON 文本 Token 读取器。
 *
 * 直接按 Token 生成，不构建 Node 树。
 *
 * @param source JSON 文本
 * @param allowComments 是否允许 JSONC 注释
 * @param duplicateKeyPolicy 对象内重复 key 策略；`FIRST_WINS` 时重复字段不会产出 Token
 * @param maxDepth 对象/数组最大嵌套深度
 * @param readFeatures 显式开启的 JSON 读取特性
 */
internal class JsonTokenReader(
    private val source: String,
    private val allowComments: Boolean = true,
    private val duplicateKeyPolicy: DuplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS,
    private val maxDepth: Int = JsonConfig.DEFAULT_MAX_DEPTH,
    private val readFeatures: Set<JsonReadFeature> = JsonConfig.DEFAULT_READ_FEATURES,
) : DocumentTokenReader {

    /**
     * 兼容旧构造器。
     */
    internal constructor(source: String, allowComments: Boolean) :
        this(source, allowComments, DuplicateKeyPolicy.LAST_WINS, JsonConfig.DEFAULT_MAX_DEPTH)

    private val features = JsonReadOptions(readFeatures)
    private val commentsEnabled = allowComments || features.allowJavaComments
    private val keyPolicy = if (features.strictDuplicateDetection) DuplicateKeyPolicy.FAIL else duplicateKeyPolicy
    private var position = 0
    private var rootStarted = false
    private var rootFinished = false
    private var depth = 0
    private val trackDuplicateKeys = keyPolicy != DuplicateKeyPolicy.LAST_WINS
    private var containerArrays = BooleanArray(8)
    private var containerPhases = IntArray(8)
    private var containerKeySets: Array<MutableSet<String>?> = arrayOfNulls(8)
    private var currentType: DocumentTokenType? = null
    private var currentText: String? = null
    private var currentBoolean: Boolean? = null
    private var rawNumberText: String? = null
    private var numberStart = -1
    private var numberEnd = -1

    override val currentToken: DocumentTokenType?
        get() = currentType

    override val textValue: String?
        get() = currentText ?: rawNumberText ?: if (numberStart >= 0 && numberEnd > numberStart) {
            source.substring(numberStart, numberEnd)
        } else {
            null
        }

    override val booleanValue: Boolean?
        get() = currentBoolean

    override val binaryValue: ByteArray?
        get() = null

    override fun intValue(): Int {
        if (numberStart >= 0 && isIntegerNumber()) {
            return parseInt(numberStart, numberEnd)
        }
        return decimalValue().intValueExact()
    }

    override fun longValue(): Long {
        if (numberStart >= 0 && isIntegerNumber()) {
            return parseLong(numberStart, numberEnd)
        }
        return decimalValue().longValueExact()
    }

    override fun doubleValue(): Double = textValue?.toDouble()
        ?: throw IllegalStateException("Current token is not a valid double")

    override fun decimalValue(): BigDecimal = BigDecimal(
        textValue ?: throw IllegalStateException("Current token is not a valid decimal"),
    )

    override fun nextToken(): DocumentTokenType? {
        if (rootFinished) {
            return null
        }
        while (true) {
            if (!rootStarted) {
                rootStarted = true
                return parseValueStart()
            }
            if (depth == 0) {
                rootFinished = true
                validateEnd()
                return null
            }
            if (containerArrays[depth - 1]) {
                when (containerPhases[depth - 1]) {
                    ARRAY_FIRST_VALUE -> {
                        skipWhitespace()
                        if (features.allowMissingValues && peek() == ',') {
                            containerPhases[depth - 1] = ARRAY_AFTER_VALUE
                            return emit(DocumentTokenType.NULL)
                        }
                        if (consume(']')) {
                            depth--
                            return emit(DocumentTokenType.END_ARRAY)
                        }
                        containerPhases[depth - 1] = ARRAY_AFTER_VALUE
                        return parseValueStart()
                    }

                    else -> {
                        skipWhitespace()
                        if (consume(']')) {
                            depth--
                            return emit(DocumentTokenType.END_ARRAY)
                        }
                        expect(',')
                        skipWhitespace()
                        if (peek() == ']') {
                            if (features.allowTrailingComma) {
                                position++
                                depth--
                                return emit(DocumentTokenType.END_ARRAY)
                            }
                            if (features.allowMissingValues) {
                                containerPhases[depth - 1] = ARRAY_AFTER_VALUE
                                return emit(DocumentTokenType.NULL)
                            }
                            throw error("Trailing comma in array")
                        }
                        containerPhases[depth - 1] = ARRAY_FIRST_VALUE
                    }
                }
            } else {
                when (containerPhases[depth - 1]) {
                    OBJECT_FIRST_FIELD -> {
                        skipWhitespace()
                        if (consume('}')) {
                            depth--
                            containerKeySets[depth] = null
                            return emit(DocumentTokenType.END_OBJECT)
                        }
                        containerPhases[depth - 1] = OBJECT_VALUE
                        val field = parseFieldName()
                        if (field != null) {
                            return field
                        }
                        containerPhases[depth - 1] = OBJECT_AFTER_VALUE
                    }

                    OBJECT_VALUE -> {
                        containerPhases[depth - 1] = OBJECT_AFTER_VALUE
                        return parseValueStart()
                    }

                    else -> {
                        skipWhitespace()
                        if (consume('}')) {
                            depth--
                            containerKeySets[depth] = null
                            return emit(DocumentTokenType.END_OBJECT)
                        }
                        expect(',')
                        skipWhitespace()
                        if (peek() == '}') {
                            if (features.allowTrailingComma) {
                                position++
                                depth--
                                containerKeySets[depth] = null
                                return emit(DocumentTokenType.END_OBJECT)
                            }
                            throw error("Trailing comma in object")
                        }
                        containerPhases[depth - 1] = OBJECT_FIRST_FIELD
                    }
                }
            }
        }
    }

    private fun parseValueStart(): DocumentTokenType {
        skipWhitespace()
        if (!hasNext()) {
            throw error("Unexpected end of input")
        }
        if (features.allowNonNumericNumbers) {
            JsonReadOptions.matchNonNumeric(source, position)?.let { (literal, length) ->
                position += length
                return emitNonNumeric(literal)
            }
        }
        return when (val ch = source[position]) {
            '{' -> {
                position++
                pushContainer(array = false)
                emit(DocumentTokenType.START_OBJECT)
            }

            '[' -> {
                position++
                pushContainer(array = true)
                emit(DocumentTokenType.START_ARRAY)
            }

            '"' -> emit(DocumentTokenType.STRING, text = parseString())

            '\'' -> if (features.allowSingleQuotes) {
                emit(DocumentTokenType.STRING, text = parseString())
            } else {
                throw error("Unexpected character '$ch'")
            }

            't' -> {
                expectLiteral("true")
                emit(DocumentTokenType.BOOLEAN, booleanValue = true)
            }

            'f' -> {
                expectLiteral("false")
                emit(DocumentTokenType.BOOLEAN, booleanValue = false)
            }

            'n' -> {
                expectLiteral("null")
                emit(DocumentTokenType.NULL)
            }

            '+' -> if (features.allowLeadingPlusSignForNumbers) {
                scanNumber()
                emitNumber()
            } else {
                throw error("Unexpected character '$ch'")
            }

            '.' -> if (features.allowLeadingDecimalPointForNumbers) {
                scanNumber()
                emitNumber()
            } else {
                throw error("Unexpected character '$ch'")
            }

            '-', in '0'..'9' -> {
                scanNumber()
                emitNumber()
            }

            else -> throw error("Unexpected character '$ch'")
        }
    }

    private fun pushContainer(array: Boolean) {
        if (depth + 1 > maxDepth) {
            throw error("Maximum nesting depth of $maxDepth exceeded")
        }
        if (depth == containerArrays.size) {
            val newSize = depth * 2
            containerArrays = containerArrays.copyOf(newSize)
            containerPhases = containerPhases.copyOf(newSize)
            containerKeySets = containerKeySets.copyOf(newSize)
        }
        containerArrays[depth] = array
        containerPhases[depth] = 0
        containerKeySets[depth] = if (array || !trackDuplicateKeys) null else HashSet(8)
        depth++
    }

    /**
     * 解析对象字段名并处理重复 key。
     *
     * @return FIELD_NAME Token；`FIRST_WINS` 策略下跳过重复字段时返回 `null`
     */
    private fun parseFieldName(): DocumentTokenType? {
        if (!hasNext()) {
            throw error("Expected object key")
        }
        val key = when {
            source[position] == '"' -> parseString()
            source[position] == '\'' && features.allowSingleQuotes -> parseString()
            features.allowUnquotedFieldNames -> parseUnquotedFieldName()
            else -> throw error("Expected object key")
        }
        skipWhitespace()
        expect(':')
        val keys = containerKeySets[depth - 1]
        if (keys != null && !keys.add(key)) {
            when (keyPolicy) {
                DuplicateKeyPolicy.FAIL -> throw error("Duplicate field \"$key\"")
                DuplicateKeyPolicy.FIRST_WINS -> {
                    skipDuplicateValue()
                    return null
                }

                DuplicateKeyPolicy.LAST_WINS -> Unit
            }
        }
        return emit(DocumentTokenType.FIELD_NAME, text = key)
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
     * 跳过 `FIRST_WINS` 策略下重复字段的值。
     *
     * 复用 [JsonParser] 做完整语法、重复 key 与深度校验。
     */
    private fun skipDuplicateValue() {
        skipWhitespace()
        val parser = JsonParser(source, commentsEnabled, keyPolicy, maxDepth, depth, readFeatures)
        position = parser.skipValueAt(position)
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

    private fun scanNumber() {
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
        numberStart = start
        numberEnd = position
    }

    private fun isIntegerNumber(): Boolean {
        if (rawNumberText != null) {
            return false
        }
        if (numberStart < 0 || numberEnd <= numberStart) {
            return false
        }
        for (index in numberStart until numberEnd) {
            when (source[index]) {
                '.', 'e', 'E' -> return false
            }
        }
        return true
    }

    private fun parseInt(start: Int, end: Int): Int {
        var index = start
        var negative = false
        if (index < end && source[index] == '-') {
            negative = true
            index++
        }
        var value = 0
        while (index < end) {
            value = value * 10 + (source[index] - '0')
            index++
        }
        return if (negative) -value else value
    }

    private fun parseLong(start: Int, end: Int): Long {
        var index = start
        var negative = false
        if (index < end && source[index] == '-') {
            negative = true
            index++
        }
        var value = 0L
        while (index < end) {
            value = value * 10 + (source[index] - '0')
            index++
        }
        return if (negative) -value else value
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

    /**
     * 跳过 YAML 风格 `#` 行注释（到行尾）。
     */
    private fun skipYamlComment() {
        while (hasNext() && source[position] != '\n' && source[position] != '\r') {
            position++
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

    private fun emit(
        type: DocumentTokenType,
        text: String? = null,
        booleanValue: Boolean? = null,
    ): DocumentTokenType {
        currentType = type
        currentText = text
        currentBoolean = booleanValue
        rawNumberText = null
        numberStart = -1
        numberEnd = -1
        return type
    }

    private fun emitNumber(): DocumentTokenType {
        currentType = DocumentTokenType.NUMBER
        currentText = null
        currentBoolean = null
        rawNumberText = null
        return DocumentTokenType.NUMBER
    }

    private fun emitNonNumeric(raw: String): DocumentTokenType {
        currentType = DocumentTokenType.NUMBER
        currentText = null
        currentBoolean = null
        rawNumberText = raw
        numberStart = -1
        numberEnd = -1
        return DocumentTokenType.NUMBER
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
