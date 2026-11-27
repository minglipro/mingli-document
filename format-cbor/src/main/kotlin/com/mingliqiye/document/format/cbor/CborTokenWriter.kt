package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.mingliqiye.document.format.cbor.exception.CborException
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

private val CBOR_FACTORY: CBORFactory = CBORFactory()

/**
 * CBOR Token 写出器。
 *
 * 包装 Jackson 的 [CBORGenerator]，把 [DocumentTokenWriter] 调用直接写成 CBOR
 * 二进制，不构建完整 Node 树。数字策略与 [CborWriter] 保持一致：
 * - 不含小数点/指数标记的 raw 写成 CBOR 整数：能放进 Int/Long 时写最短整数，
 *   更大的整数写成 bignum（tag 2/3）
 * - 其余 raw 用 [BigDecimal] 写成 CBOR 十进制分数（tag 4），保留 BigDecimal 精度
 *
 * 由于 Token 接口的 `startObject` / `startArray` 不携带元素数量，对象与数组写成
 * CBOR 不定长形式（`0xBF`/`0x9F` ... `0xFF`），与 [CborWriter] 的定长写法不同，
 * 但都是合法 CBOR，可被 [CborReader] / [CborTokenReader] 读回。
 */
internal class CborTokenWriter : DocumentTokenWriter {

    private val buffer = ByteArrayOutputStream()

    private val generator: CBORGenerator = try {
        CBOR_FACTORY.createGenerator(buffer)
    } catch (e: IOException) {
        throw CborException("Failed to create CBOR generator: ${e.message}", e)
    }

    override fun startObject() {
        writing { generator.writeStartObject() }
    }

    override fun endObject() {
        writing { generator.writeEndObject() }
    }

    override fun startArray() {
        writing { generator.writeStartArray() }
    }

    override fun endArray() {
        writing { generator.writeEndArray() }
    }

    override fun fieldName(name: String) {
        writing { generator.writeFieldName(name) }
    }

    override fun writeString(value: String) {
        writing { generator.writeString(value) }
    }

    override fun writeBoolean(value: Boolean) {
        writing { generator.writeBoolean(value) }
    }

    override fun writeNull() {
        writing { generator.writeNull() }
    }

    override fun writeBinary(value: ByteArray) {
        writing { generator.writeBinary(value) }
    }

    override fun writeNumber(raw: String) {
        writing {
            try {
                if (raw.any { it == '.' || it == 'e' || it == 'E' }) {
                    generator.writeNumber(BigDecimal(raw))
                } else {
                    writeInteger(BigInteger(raw))
                }
            } catch (e: NumberFormatException) {
                throw CborException("Invalid number '$raw'", e)
            }
        }
    }

    /**
     * 取出已写出的 CBOR 字节（会先 flush generator）。
     *
     * @return CBOR 二进制内容
     */
    fun toByteArray(): ByteArray {
        writing { generator.flush() }
        return buffer.toByteArray()
    }

    /**
     * 关闭底层 generator。
     *
     * @throws CborException 关闭失败时抛出
     */
    fun close() {
        try {
            generator.close()
        } catch (e: IOException) {
            throw CborException("Failed to close CBOR generator: ${e.message}", e)
        }
    }

    private fun writeInteger(value: BigInteger) {
        when {
            value.bitLength() < 32 -> generator.writeNumber(value.toInt())
            value.bitLength() < 64 -> generator.writeNumber(value.toLong())
            else -> generator.writeNumber(value)
        }
    }

    private fun writing(block: () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            throw CborException("Failed to write CBOR: ${e.message}", e)
        }
    }
}
