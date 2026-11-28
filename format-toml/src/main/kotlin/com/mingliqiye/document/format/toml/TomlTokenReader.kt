package com.mingliqiye.document.format.toml

import com.mingliqiye.document.format.toml.exception.TomlParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * TOML 原生 Token 读取器。
 *
 * 直接遍历 tomlj 的 [TomlTable] / [TomlArray] 生成 Token 流，不构建 Node 树。
 *
 * 解析深度复用 [TomlParser]：默认 [TomlMapper.DEFAULT_MAX_DEPTH]（512），超限抛
 * [TomlParseException]；[TomlParser] 已保证结果树深度不超过 [maxDepth]，因此这里的
 * 递归生成最多 [maxDepth] 层，不会 StackOverflow。
 *
 * @param source TOML 文本
 * @param maxDepth 最大嵌套深度；`0` 或负数表示不限制
 */
internal class TomlTokenReader(
    source: String,
    private val maxDepth: Int = TomlMapper.DEFAULT_MAX_DEPTH,
) : DocumentTokenReader {

    private val tokens: List<Token> = buildTokens(source, maxDepth)
    private var index = -1
    private var current: Token? = null

    override val currentToken: DocumentTokenType?
        get() = current?.type

    override val textValue: String?
        get() = current?.text

    override val booleanValue: Boolean?
        get() = current?.boolean

    override val binaryValue: ByteArray?
        get() = null

    override fun nextToken(): DocumentTokenType? {
        index++
        if (index >= tokens.size) {
            current = null
            return null
        }
        current = tokens[index]
        return current!!.type
    }

    private fun buildTokens(source: String, maxDepth: Int): List<Token> {
        val result = TomlParser.parse(source, maxDepth)
        val tokens = ArrayList<Token>()
        appendTable(result, tokens)
        return tokens
    }

    private fun appendTable(table: TomlTable, tokens: MutableList<Token>) {
        tokens.add(Token(DocumentTokenType.START_OBJECT, null))
        for (key in table.keySet()) {
            tokens.add(Token(DocumentTokenType.FIELD_NAME, key))
            appendValue(table.get(listOf(key)), tokens)
        }
        tokens.add(Token(DocumentTokenType.END_OBJECT, null))
    }

    private fun appendArray(array: TomlArray, tokens: MutableList<Token>) {
        tokens.add(Token(DocumentTokenType.START_ARRAY, null))
        for (index in 0 until array.size()) {
            appendValue(array.get(index), tokens)
        }
        tokens.add(Token(DocumentTokenType.END_ARRAY, null))
    }

    private fun appendValue(value: Any?, tokens: MutableList<Token>) {
        when (value) {
            is TomlTable -> appendTable(value, tokens)
            is TomlArray -> appendArray(value, tokens)
            is String -> tokens.add(Token(DocumentTokenType.STRING, value))
            is Boolean -> tokens.add(Token(DocumentTokenType.BOOLEAN, null, value))
            is Long -> tokens.add(Token(DocumentTokenType.NUMBER, value.toString()))
            is Double -> tokens.add(Token(DocumentTokenType.NUMBER, doubleRaw(value)))
            is OffsetDateTime -> tokens.add(Token(DocumentTokenType.STRING, value.toString()))
            is LocalDateTime -> tokens.add(Token(DocumentTokenType.STRING, value.toString()))
            is LocalDate -> tokens.add(Token(DocumentTokenType.STRING, value.toString()))
            is LocalTime -> tokens.add(Token(DocumentTokenType.STRING, value.toString()))
            else -> throw TomlParseException("Unsupported TOML value type: ${value?.javaClass?.name ?: "null"}")
        }
    }

    private fun doubleRaw(value: Double): String {
        if (!value.isFinite()) {
            throw TomlParseException("TOML NaN/Infinity numbers are not supported")
        }
        return BigDecimal.valueOf(value).toPlainString()
    }

    private data class Token(
        val type: DocumentTokenType,
        val text: String?,
        val boolean: Boolean? = null,
    )
}
