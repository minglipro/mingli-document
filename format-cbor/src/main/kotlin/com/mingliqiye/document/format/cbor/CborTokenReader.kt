package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.mingliqiye.document.format.cbor.exception.CborParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import java.io.IOException
import java.math.BigDecimal

private val CBOR_FACTORY: CBORFactory = CBORFactory()

private fun createParser(source: ByteArray): JsonParser = try {
    CBOR_FACTORY.createParser(source)
} catch (e: IOException) {
    throw CborParseException("Invalid CBOR: ${e.message}", e)
}

/**
 * CBOR Token 直读器。
 *
 * 包装 Jackson 的 CBOR [JsonParser]，把解析结果转换成与格式无关的
 * [DocumentTokenType] 流，供 [com.mingliqiye.document.serialization.DocumentMapper]
 * 直接解码 Bean，避免先构建完整 Node 树。
 *
 * 映射规则：
 * - 对象/数组的 START/END → [DocumentTokenType.START_OBJECT] 等
 * - map key → [DocumentTokenType.FIELD_NAME]，[textValue] 为 key 文本
 * - 文本字符串、tag 0 日期文本 → [DocumentTokenType.STRING]
 * - 整数（含 bignum）、浮点数、tag 4 十进制分数 → [DocumentTokenType.NUMBER]，
 *   [textValue] 保留 Jackson 给出的原始数字文本
 * - `true` / `false` → [DocumentTokenType.BOOLEAN]，`null` / `undefined` →
 *   [DocumentTokenType.NULL]
 * - CBOR byte string → [DocumentTokenType.BINARY]（[binaryValue] 为复制后的字节）
 *
 * 与 [CborReader] 一致，Jackson 虽能解码半精度/单精度/双精度浮点的 NaN 与
 * Infinity，但 Node 与 Bean 模型只支持有限数值，因此读取到 NaN/Infinity 时抛
 * [CborParseException]；无法映射的 embedded object 同样抛出该异常。
 *
 * @param parser 已创建的 Jackson CBOR 解析器
 */
internal class CborTokenReader internal constructor(
    private val parser: JsonParser,
) : DocumentTokenReader {

    /**
     * 直接使用 CBOR 字节创建 Token 读取器。
     *
     * @param source CBOR 二进制内容
     */
    internal constructor(source: ByteArray) : this(createParser(source))

    override var currentToken: DocumentTokenType? = null
        private set

    override var textValue: String? = null
        private set

    override var booleanValue: Boolean? = null
        private set

    override var binaryValue: ByteArray? = null
        private set

    /**
     * 前进到下一个 Token。
     *
     * @return 下一个 Token；文档结束返回 `null`
     * @throws CborParseException CBOR 格式非法、包含 NaN/Infinity，或出现无法映射的
     *   embedded object 时抛出
     */
    override fun nextToken(): DocumentTokenType? {
        val jacksonToken = try {
            parser.nextToken()
        } catch (e: IOException) {
            throw CborParseException("Invalid CBOR: ${errorMessage(e)}", e)
        }
        resetValues()
        if (jacksonToken == null) {
            return null
        }
        val token = try {
            toDocumentToken(jacksonToken)
        } catch (e: IOException) {
            throw CborParseException("Invalid CBOR: ${errorMessage(e)}", e)
        }
        currentToken = token
        return token
    }

    private fun resetValues() {
        currentToken = null
        textValue = null
        booleanValue = null
        binaryValue = null
    }

    private fun toDocumentToken(token: JsonToken): DocumentTokenType = when (token) {
        JsonToken.START_OBJECT -> DocumentTokenType.START_OBJECT
        JsonToken.END_OBJECT -> DocumentTokenType.END_OBJECT
        JsonToken.START_ARRAY -> DocumentTokenType.START_ARRAY
        JsonToken.END_ARRAY -> DocumentTokenType.END_ARRAY
        JsonToken.FIELD_NAME -> {
            textValue = parser.currentName()
            DocumentTokenType.FIELD_NAME
        }

        JsonToken.VALUE_STRING -> {
            textValue = parser.text
            DocumentTokenType.STRING
        }

        JsonToken.VALUE_NUMBER_INT -> {
            textValue = parser.text
            DocumentTokenType.NUMBER
        }

        JsonToken.VALUE_NUMBER_FLOAT -> {
            textValue = finiteNumberText()
            DocumentTokenType.NUMBER
        }

        JsonToken.VALUE_TRUE -> {
            booleanValue = true
            DocumentTokenType.BOOLEAN
        }

        JsonToken.VALUE_FALSE -> {
            booleanValue = false
            DocumentTokenType.BOOLEAN
        }

        JsonToken.VALUE_NULL -> DocumentTokenType.NULL
        JsonToken.VALUE_EMBEDDED_OBJECT -> {
            binaryValue = embeddedBytes()
            DocumentTokenType.BINARY
        }

        else -> throw CborParseException("Unsupported CBOR token: $token")
    }

    private fun finiteNumberText(): String {
        val text = parser.text
        try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            throw CborParseException("CBOR non-finite floating point value '$text' is not supported", e)
        }
        return text
    }

    private fun embeddedBytes(): ByteArray {
        val embedded = parser.embeddedObject
        if (embedded is ByteArray) {
            return embedded.copyOf()
        }
        throw CborParseException("Unsupported CBOR embedded object: ${embedded?.javaClass?.name}")
    }

    private fun errorMessage(e: IOException): String = (e as? JsonProcessingException)?.originalMessage ?: e.message ?: "unknown error"
}
