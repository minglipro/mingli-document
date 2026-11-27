package com.mingliqiye.document.format.smile

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.mingliqiye.document.format.smile.exception.SmileParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import java.io.IOException

/**
 * Smile Token 读取器。
 *
 * 包装 Jackson Smile [JsonParser]，把 Jackson Token 映射为通用
 * [DocumentTokenType]，供 [com.mingliqiye.document.serialization.DocumentMapper.fromTokens]
 * 直接解码 Bean，避免先构建完整 [com.mingliqiye.document.core.Node] 树。
 *
 * Token 映射：
 * - `START_OBJECT` / `END_OBJECT` / `START_ARRAY` / `END_ARRAY` → 同名 Token
 * - `FIELD_NAME` → [DocumentTokenType.FIELD_NAME]，[textValue] 为字段名
 * - `VALUE_STRING` → [DocumentTokenType.STRING]
 * - `VALUE_NUMBER_INT` / `VALUE_NUMBER_FLOAT` → [DocumentTokenType.NUMBER]，
 *   [textValue] 保留 Jackson 给出的原始数字文本
 * - `VALUE_TRUE` / `VALUE_FALSE` → [DocumentTokenType.BOOLEAN]
 * - `VALUE_NULL` → [DocumentTokenType.NULL]
 * - `VALUE_EMBEDDED_OBJECT` → [DocumentTokenType.BINARY]，[binaryValue] 为字节内容
 *
 * @param source Smile 二进制内容
 */
internal class SmileTokenReader(
    source: ByteArray,
) : DocumentTokenReader {

    private val parser: JsonParser = try {
        SMILE_FACTORY.createParser(source)
    } catch (e: IOException) {
        throw SmileParseException("Invalid Smile: ${e.message}", e)
    }

    private var current: DocumentTokenType? = null

    override val currentToken: DocumentTokenType?
        get() = current

    override val textValue: String?
        get() = when (current) {
            DocumentTokenType.FIELD_NAME,
            DocumentTokenType.STRING,
            DocumentTokenType.NUMBER,
            -> readText()
            else -> null
        }

    override val booleanValue: Boolean?
        get() = if (current == DocumentTokenType.BOOLEAN) readBoolean() else null

    override val binaryValue: ByteArray?
        get() = if (current == DocumentTokenType.BINARY) readBinary() else null

    override fun nextToken(): DocumentTokenType? {
        val jsonToken = try {
            parser.nextToken()
        } catch (e: IOException) {
            throw SmileParseException("Invalid Smile: ${e.message}", e)
        }
        current = if (jsonToken == null) null else toDocumentToken(jsonToken)
        return current
    }

    private fun readText(): String? = try {
        parser.text
    } catch (e: IOException) {
        throw SmileParseException("Invalid Smile: ${e.message}", e)
    }

    private fun readBoolean(): Boolean = try {
        parser.booleanValue
    } catch (e: IOException) {
        throw SmileParseException("Invalid Smile: ${e.message}", e)
    }

    private fun readBinary(): ByteArray = try {
        parser.binaryValue ?: throw SmileParseException("Smile binary value is missing")
    } catch (e: IOException) {
        throw SmileParseException("Invalid Smile: ${e.message}", e)
    }

    private fun toDocumentToken(token: JsonToken): DocumentTokenType = when (token) {
        JsonToken.START_OBJECT -> DocumentTokenType.START_OBJECT
        JsonToken.END_OBJECT -> DocumentTokenType.END_OBJECT
        JsonToken.START_ARRAY -> DocumentTokenType.START_ARRAY
        JsonToken.END_ARRAY -> DocumentTokenType.END_ARRAY
        JsonToken.FIELD_NAME -> DocumentTokenType.FIELD_NAME
        JsonToken.VALUE_STRING -> DocumentTokenType.STRING
        JsonToken.VALUE_NUMBER_INT -> DocumentTokenType.NUMBER
        JsonToken.VALUE_NUMBER_FLOAT -> DocumentTokenType.NUMBER
        JsonToken.VALUE_TRUE -> DocumentTokenType.BOOLEAN
        JsonToken.VALUE_FALSE -> DocumentTokenType.BOOLEAN
        JsonToken.VALUE_NULL -> DocumentTokenType.NULL
        JsonToken.VALUE_EMBEDDED_OBJECT -> DocumentTokenType.BINARY
        else -> throw SmileParseException("Unsupported Smile token: $token")
    }

    private companion object {

        private val SMILE_FACTORY: SmileFactory = SmileFactory()
    }
}
