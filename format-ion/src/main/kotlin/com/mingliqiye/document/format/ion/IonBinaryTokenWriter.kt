package com.mingliqiye.document.format.ion

import com.amazon.ion.IonSystem
import com.amazon.ion.IonType
import com.amazon.ion.system.IonSystemBuilder
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonException
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
 *
 * [writeFeatures] 行为：
 * - [IonWriteFeature.USE_NATIVE_TYPE_ID] 开启时，`$ionAnnotations` 注解包装对象
 *   写成原生 Ion annotation；
 * - [IonWriteFeature.STRICT_DUPLICATE_DETECTION] 开启时，struct 重复字段名抛
 *   [IonException]。
 *
 * @param target 目标输出流；默认内存缓冲（可用 [toByteArray] 取字节）
 * @param writeFeatures Ion 写出特性
 */
internal class IonBinaryTokenWriter(
    target: OutputStream = ByteArrayOutputStream(),
    private val writeFeatures: Set<IonWriteFeature> = IonConfig.DEFAULT_WRITE_FEATURES,
) : DocumentTokenWriter {

    private val output = target as? ByteArrayOutputStream
    private val writer = ION_SYSTEM.newBinaryWriter(
        object : FilterOutputStream(target) {
            override fun close() {
                flush()
            }
        },
    )
    private val containers = ArrayDeque<Boolean>()
    private val structFieldNames = ArrayDeque<MutableSet<String>>()
    private var closed = false

    override fun startObject() {
        writer.stepIn(IonType.STRUCT)
        containers.addLast(true)
        structFieldNames.addLast(HashSet())
    }

    override fun endObject() {
        writer.stepOut()
        containers.removeLast()
        structFieldNames.removeLast()
    }

    override fun startArray() {
        writer.stepIn(IonType.LIST)
        containers.addLast(false)
    }

    override fun endArray() {
        writer.stepOut()
        containers.removeLast()
    }

    override fun fieldName(name: String) {
        if (IonWriteFeature.STRICT_DUPLICATE_DETECTION in writeFeatures && containers.lastOrNull() == true) {
            val fieldNames = structFieldNames.last()
            if (!fieldNames.add(name)) {
                throw IonException("Duplicate Ion struct field '$name'")
            }
        }
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

    override fun writeNode(node: Node) {
        if (IonWriteFeature.USE_NATIVE_TYPE_ID in writeFeatures) {
            val annotationWrapper = IonTypeFidelity.annotationWrapper(node)
            if (annotationWrapper != null) {
                writer.setTypeAnnotations(*annotationWrapper.first.toTypedArray())
                writeNode(annotationWrapper.second)
                return
            }
        }
        when (node) {
            is NullNode -> writeNull()
            is BooleanNode -> writeBoolean(node.value)
            is StringNode -> writeString(node.value)
            is NumberNode -> writeNumber(node.raw)
            is BinaryNode -> writeBinary(node.value)
            is ObjectNode -> {
                startObject()
                for ((name, value) in node.entries()) {
                    fieldName(name)
                    writeNode(value)
                }
                endObject()
            }

            is ArrayNode -> {
                startArray()
                for (element in node) {
                    writeNode(element)
                }
                endArray()
            }
        }
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
