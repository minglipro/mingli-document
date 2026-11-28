package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.cbor.CBORSimpleValue
import com.mingliqiye.document.format.cbor.exception.CborParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import java.io.IOException
import java.math.BigDecimal

private fun createParser(
    source: ByteArray,
    readFeatures: Set<CborReadFeature>,
): JsonParser = try {
    CborFeatureSupport.factory(readFeatures, CborConfig.DEFAULT_WRITE_FEATURES).createParser(source)
} catch (e: IOException) {
    throw CborParseException("Invalid CBOR: ${e.message}", e)
}

/**
 * CBOR Token 直读器。
 *
 * 包装 上游实现 的 CBOR [JsonParser]，把解析结果转换成与格式无关的
 * [DocumentTokenType] 流，供 [com.mingliqiye.document.serialization.DocumentMapper]
 * 直接解码 Bean，避免先构建完整 Node 树。
 *
 * 映射规则：
 * - 对象/数组的 START/END → [DocumentTokenType.START_OBJECT] 等
 * - map key → [DocumentTokenType.FIELD_NAME]，[textValue] 为 key 文本
 * - 文本字符串、tag 0 日期文本 → [DocumentTokenType.STRING]
 * - 整数（含 bignum）、浮点数、tag 4 十进制分数 → [DocumentTokenType.NUMBER]，
 *   [textValue] 保留 上游实现 给出的原始数字文本
 * - `true` / `false` → [DocumentTokenType.BOOLEAN]，`null` / `undefined` →
 *   [DocumentTokenType.NULL]
 * - CBOR byte string → [DocumentTokenType.BINARY]（[binaryValue] 为复制后的字节）
 *
 * 与 [CborReader] 一致，上游实现 虽能解码半精度/单精度/双精度浮点的 NaN 与
 * Infinity，但 Node 与 Bean 模型只支持有限数值，因此读取到 NaN/Infinity 时抛
 * [CborParseException]；无法映射的 embedded object 同样抛出该异常。
 *
 * 读取特性参数直接落到 上游实现 parser 特性上；其中
 * [CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT] /
 * [CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT] 无法用
 * [DocumentTokenType] 区分，本读取器把 `undefined` 映射为
 * [DocumentTokenType.NULL]、simple value 映射为 [DocumentTokenType.NUMBER]；
 * 需要区分时请使用 [CborReader] 的 Node 路径（[CborMapper] 在这些特性开启时会
 * 自动禁用 Token 快路径）。
 *
 * @param parser 已创建的 上游实现 CBOR 解析器
 */
internal class CborTokenReader internal constructor(
    private val parser: JsonParser,
) : DocumentTokenReader {

    /**
     * 直接使用 CBOR 字节创建 Token 读取器。
     *
     * @param source CBOR 二进制内容
     * @param readFeatures CBOR 读取特性集合，默认 [CborConfig.DEFAULT_READ_FEATURES]
     */
    internal constructor(
        source: ByteArray,
        readFeatures: Set<CborReadFeature> = CborConfig.DEFAULT_READ_FEATURES,
    ) : this(createParser(source, readFeatures))

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
        JsonToken.VALUE_EMBEDDED_OBJECT -> embeddedToken()
        else -> throw CborParseException("Unsupported CBOR token: $token")
    }

    private fun embeddedToken(): DocumentTokenType {
        val embedded = parser.embeddedObject
        if (embedded is ByteArray) {
            binaryValue = embedded.copyOf()
            return DocumentTokenType.BINARY
        }
        if (embedded == null) {
            // undefined 经 READ_UNDEFINED_AS_EMBEDDED_OBJECT 暴露；Token 接口无法区分。
            return DocumentTokenType.NULL
        }
        if (embedded is CBORSimpleValue) {
            // simple value 经 READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT 暴露；Token 接口
            // 按默认语义还原为数字。
            textValue = embedded.value.toString()
            return DocumentTokenType.NUMBER
        }
        throw CborParseException("Unsupported CBOR embedded object: ${embedded.javaClass.name}")
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

    private fun errorMessage(e: IOException): String = (e as? JsonProcessingException)?.originalMessage ?: e.message ?: "unknown error"
}
