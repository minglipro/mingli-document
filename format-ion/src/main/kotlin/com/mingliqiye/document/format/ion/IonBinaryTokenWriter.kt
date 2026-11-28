package com.mingliqiye.document.format.ion

import com.amazon.ion.IonSystem
import com.amazon.ion.IonType
import com.amazon.ion.system.IonSystemBuilder
import com.mingliqiye.document.serialization.DocumentTokenWriter
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * 基于 ion-java 二进制写出器的流式 Token 写出器。
 *
 * 直接实现 [DocumentTokenWriter]，供
 * [com.mingliqiye.document.serialization.DocumentMapper.writeTo] 驱动：
 * 实体遍历过程直接写成二进制 Ion，不构建完整 Node 树。
 *
 * 数字复用 [writeIonNumber] 规则（int / decimal）；[writeBinary] 写出为
 * Ion blob。输出以 `$ion_1_0` 二进制版本标记开头。
 */
internal class IonBinaryTokenWriter(
    target: OutputStream = ByteArrayOutputStream(),
) : DocumentTokenWriter {

    private val output = target as? ByteArrayOutputStream
    private val writer = ION_SYSTEM.newBinaryWriter(
        object : FilterOutputStream(target) {
            override fun close() {
                flush()
            }
        },
    )
    private var closed = false

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

    /**
     * 关闭底层写出器，完成符号表与缓冲区刷新。
     *
     * 可重复调用。
     */
    fun close() {
        if (closed) {
            return
        }
        closed = true
        writer.close()
    }

    /**
     * 返回已写出的二进制 Ion 字节；未关闭时先自动 [close]。
     */
    fun toByteArray(): ByteArray {
        close()
        return output?.toByteArray()
            ?: throw IllegalStateException("toByteArray is only available for the in-memory constructor")
    }

    override fun flush() {
        close()
    }

    private companion object {

        private val ION_SYSTEM: IonSystem = IonSystemBuilder.standard().build()
    }
}
