package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.mingliqiye.document.core.ByteArrayPool
import com.mingliqiye.document.format.cbor.exception.CborException
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger

/**
 * CBOR Token 写出器。
 *
 * 包装 上游实现 的 [CBORGenerator]，把 [DocumentTokenWriter] 调用直接写成 CBOR
 * 二进制，不构建完整 Node 树。数字策略与 [CborWriter] 保持一致：
 * - 不含小数点/指数标记的 raw 写成 CBOR 整数：能放进 Int/Long 时写最短整数，
 *   更大的整数写成 bignum（tag 2/3）
 * - 其余 raw 用 [BigDecimal] 写成 CBOR 十进制分数（tag 4），保留 BigDecimal 精度
 *
 * 由于 Token 接口的 `startObject` / `startArray` 不携带元素数量，对象与数组写成
 * CBOR 不定长形式（`0xBF`/`0x9F` ... `0xFF`），与 [CborWriter] 的定长写法不同，
 * 但都是合法 CBOR，可被 [CborReader] / [CborTokenReader] 读回。
 *
 * [writeFeatures] 直接落到 上游实现 generator 特性上，其中
 * [CborWriteFeature.WRITE_MINIMAL_DOUBLES] 只在本类生效：开启时 `writeDouble`
 * 写二进制浮点（能省 4 字节则 float32），关闭时沿用模块历史的十进制分数写法
 * （`writeNumber(value.toString())`），与 Node 数字策略保持一致。
 *
 * 默认使用普通 [ByteArrayOutputStream]；使用接受 [ByteArrayPool] 的构造器创建时
 * 使用池化中间缓冲区，[close] 时把缓冲区归还池，[toByteArray] 在关闭前返回内容副本。
 */
internal class CborTokenWriter(
    private val buffer: OutputStream = ByteArrayOutputStream(),
    private val writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
) : DocumentTokenWriter {

    /**
     * 创建使用 [ByteArrayPool] 中间缓冲区的 Token 写出器。
     *
     * @param pool 缓冲区池；写出完成后（[close]）缓冲区会被归还
     * @param writeFeatures CBOR 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
     */
    internal constructor(
        pool: ByteArrayPool,
        writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
    ) : this(PooledByteArrayOutputStream(pool), writeFeatures)

    private val pooledBuffer: PooledByteArrayOutputStream? = buffer as? PooledByteArrayOutputStream

    private val generator: CBORGenerator = try {
        CborFeatureSupport.factory(CborConfig.DEFAULT_READ_FEATURES, writeFeatures).createGenerator(buffer)
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

    /**
     * 写出 `double`。
     *
     * 默认（未开启 [CborWriteFeature.WRITE_MINIMAL_DOUBLES]）沿用模块历史的
     * 十进制分数写法；开启后写最小长度二进制浮点：
     * - 能无损转成 4 字节 float 时写 float32；
     * - 否则写 8 字节 float64（上游实现 `WRITE_MINIMAL_DOUBLES` 语义）。
     *
     * 与模块读取规则一致，NaN / Infinity 仍被拒绝。
     */
    override fun writeDouble(value: Double) {
        if (CborWriteFeature.WRITE_MINIMAL_DOUBLES !in writeFeatures) {
            super<DocumentTokenWriter>.writeDouble(value)
            return
        }
        if (!value.isFinite()) {
            throw CborException("CBOR non-finite floating point value '$value' is not supported")
        }
        writing { generator.writeNumber(value) }
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
     * 使用池化缓冲区时返回内容副本，必须在 [close] 之前调用；普通内存构造器可重复
     * 调用。
     *
     * @return CBOR 二进制内容
     * @throws IllegalStateException 流式（外部 [OutputStream]）构造器调用本方法时抛出
     */
    fun toByteArray(): ByteArray {
        writing { generator.flush() }
        return when (buffer) {
            is ByteArrayOutputStream -> buffer.toByteArray()
            is PooledByteArrayOutputStream -> buffer.toByteArray()
            else -> throw IllegalStateException("toByteArray is only available for the in-memory constructor")
        }
    }

    override fun flush() {
        writing { generator.flush() }
    }

    /**
     * 关闭底层 generator，并归还池化缓冲区。
     *
     * @throws CborException 关闭失败时抛出
     */
    fun close() {
        try {
            generator.close()
        } catch (e: IOException) {
            throw CborException("Failed to close CBOR generator: ${e.message}", e)
        } finally {
            pooledBuffer?.close()
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
