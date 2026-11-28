package com.mingliqiye.document.format.ion

import com.amazon.ion.IonType
import com.amazon.ion.system.IonTextWriterBuilder
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

/**
 * 直接写 Ion 文本到输出流的 Token 写出器。
 *
 * 实体遍历过程直接生成 Ion 文本，不构建完整 Node 树，也不先拼接完整 String。
 * 输出字符集由调用方指定；写出器 finish 后不关闭目标流。
 */
internal class IonTextTokenWriter(
    output: OutputStream,
    pretty: Boolean,
    charset: Charset,
) : DocumentTokenWriter {

    private val writer = if (pretty) {
        IonTextWriterBuilder.pretty().withMinimalSystemData().build(OutputStreamWriter(output, charset))
    } else {
        IonTextWriterBuilder.minimal().build(OutputStreamWriter(output, charset))
    }
    private var finished = false

    override fun startObject() {
        writer.stepIn(IonType.STRUCT)
    }

    override fun endObject() {
        writer.stepOut()
    }

    override fun startArray() {
        writer.stepIn(IonType.LIST)
    }

    override fun endArray() {
        writer.stepOut()
    }

    override fun fieldName(name: String) {
        writer.setFieldName(name)
    }

    override fun writeString(value: String) {
        writer.writeString(value)
    }

    override fun writeNumber(raw: String) {
        writeIonNumber(writer, raw)
    }

    override fun writeBoolean(value: Boolean) {
        writer.writeBool(value)
    }

    override fun writeNull() {
        writer.writeNull()
    }

    override fun writeBinary(value: ByteArray) {
        writer.writeBlob(value)
    }

    override fun flush() {
        if (finished) {
            return
        }
        finished = true
        writer.finish()
    }
}
