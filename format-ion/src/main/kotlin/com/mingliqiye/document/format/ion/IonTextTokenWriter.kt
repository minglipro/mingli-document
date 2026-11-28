package com.mingliqiye.document.format.ion

import com.amazon.ion.IonType
import com.amazon.ion.system.IonTextWriterBuilder
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
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset

/**
 * 直接写 Ion 文本到输出流的 Token 写出器。
 *
 * 实体遍历过程直接生成 Ion 文本，不构建完整 Node 树，也不先拼接完整 String。
 * 输出字符集由调用方指定；写出器 finish 后不关闭目标流。
 *
 * [writeFeatures] 行为与 [IonBinaryTokenWriter] 一致：
 * [IonWriteFeature.USE_NATIVE_TYPE_ID] 识别注解包装对象，
 * [IonWriteFeature.STRICT_DUPLICATE_DETECTION] 检测重复字段名。
 */
internal class IonTextTokenWriter(
    output: OutputStream,
    pretty: Boolean,
    charset: Charset,
    private val writeFeatures: Set<IonWriteFeature> = IonConfig.DEFAULT_WRITE_FEATURES,
) : DocumentTokenWriter {

    private val writer = if (pretty) {
        IonTextWriterBuilder.pretty().withMinimalSystemData().build(OutputStreamWriter(output, charset))
    } else {
        IonTextWriterBuilder.minimal().build(OutputStreamWriter(output, charset))
    }
    private val containers = ArrayDeque<Boolean>()
    private val structFieldNames = ArrayDeque<MutableSet<String>>()
    private var finished = false

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

    override fun flush() {
        if (finished) {
            return
        }
        finished = true
        writer.finish()
    }
}
