package com.mingliqiye.document.format.smile

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.mingliqiye.document.format.smile.exception.SmileException
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Smile Token 写出器。
 *
 * 包装 Jackson Smile [JsonGenerator] 输出到内存缓冲，实现 [DocumentTokenWriter]，
 * 让 `DocumentMapper.writeTo` 直接把实体写成 Smile 字节，不构建完整
 * [com.mingliqiye.document.core.Node] 树。
 *
 * 数字分类与 [SmileWriter] 共用 [writeSmileNumber]；二进制使用 Jackson Smile 扩展的
 * embedded binary 令牌，输出与 [SmileWriter] 的 Node 路径一致。
 *
 * 使用 [toByteArray] 取结果，最后调用 [close] 释放 Jackson 生成器。
 */
internal class SmileTokenWriter(
    private val output: OutputStream = ByteArrayOutputStream(),
) : DocumentTokenWriter {

    private val inMemory = output as? ByteArrayOutputStream

    private val generator: JsonGenerator = try {
        SMILE_FACTORY.createGenerator(output)
    } catch (e: IOException) {
        throw SmileException("Cannot create Smile generator", e)
    }

    private var closed = false

    override fun startObject() {
        guard { generator.writeStartObject() }
    }

    override fun endObject() {
        guard { generator.writeEndObject() }
    }

    override fun startArray() {
        guard { generator.writeStartArray() }
    }

    override fun endArray() {
        guard { generator.writeEndArray() }
    }

    override fun fieldName(name: String) {
        guard { generator.writeFieldName(name) }
    }

    override fun writeString(value: String) {
        guard { generator.writeString(value) }
    }

    override fun writeNumber(raw: String) {
        guard { writeSmileNumber(generator, raw) }
    }

    override fun writeBoolean(value: Boolean) {
        guard { generator.writeBoolean(value) }
    }

    override fun writeNull() {
        guard { generator.writeNull() }
    }

    override fun writeBinary(value: ByteArray) {
        guard { generator.writeBinary(value) }
    }

    /**
     * 返回已写出的 Smile 字节。
     *
     * 尚未 [close] 时会先 flush 生成器；[close] 之后直接返回完整结果。
     */
    fun toByteArray(): ByteArray {
        if (!closed) {
            guard { generator.flush() }
        }
        return inMemory?.toByteArray()
            ?: throw IllegalStateException("toByteArray is only available for the in-memory constructor")
    }

    override fun flush() {
        if (!closed) {
            guard { generator.flush() }
        }
    }

    /**
     * 关闭 Jackson 生成器；重复调用无副作用。
     */
    fun close() {
        if (closed) {
            return
        }
        closed = true
        try {
            generator.close()
        } catch (_: IOException) {
            // 内容已经写出，关闭失败不影响结果。
        }
    }

    private inline fun guard(action: () -> Unit) {
        try {
            action()
        } catch (e: IOException) {
            throw SmileException("Failed to write Smile content: ${e.message}", e)
        }
    }

    private companion object {

        private val SMILE_FACTORY: SmileFactory = SmileFactory()
    }
}
