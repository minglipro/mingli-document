package com.mingliqiye.document.format.smile

import com.fasterxml.jackson.core.JsonGenerator
import com.mingliqiye.document.format.smile.exception.SmileException
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Smile Token 写出器。
 *
 * 包装 上游实现 Smile [JsonGenerator] 输出到内存缓冲，实现 [DocumentTokenWriter]，
 * 让 `DocumentMapper.writeTo` 直接把实体写成 Smile 字节，不构建完整
 * [com.mingliqiye.document.core.Node] 树。
 *
 * 数字分类与 [SmileWriter] 共用 [writeSmileNumber]；二进制使用 上游实现 Smile 扩展的
 * embedded binary 令牌，输出与 [SmileWriter] 的 Node 路径一致。
 *
 * 写出特性由 [config] 提供（头部、结束标记、共享字段名/字符串值、二进制 7-bit 编码、
 * 宽松 UTF 编码、严格重复字段名检查），默认与 上游实现 一致。
 *
 * 使用 [toByteArray] 取结果，最后调用 [close] 释放 上游实现 生成器。
 *
 * @param output 输出目标，默认内存缓冲
 * @param config Smile 配置；`null` 表示使用 上游实现 默认写出特性
 */
internal class SmileTokenWriter(
    private val output: OutputStream = ByteArrayOutputStream(),
    config: SmileConfig? = null,
) : DocumentTokenWriter {

    private val inMemory = output as? ByteArrayOutputStream

    private val generator: JsonGenerator = try {
        createSmileGeneratorFactory(config).createGenerator(output)
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
     * 关闭 上游实现 生成器；重复调用无副作用。
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
}
