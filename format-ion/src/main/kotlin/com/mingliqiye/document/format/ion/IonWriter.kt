package com.mingliqiye.document.format.ion

import com.amazon.ion.IonType
import com.amazon.ion.system.IonTextWriterBuilder
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonException
import java.math.BigDecimal
import java.math.BigInteger

/**
 * 将文档树写成 Ion 文本。
 *
 * 使用 ion-java 的文本写出器（最小系统数据模式，不输出 Ion 版本标记）。
 * 数字按 [NumberNode.raw] 区分 int / decimal：不含小数点或指数的写为 int，
 * 其余写为 decimal。Node 模型不区分 symbol、blob、clob 与注解，这些信息
 * 不会写出。
 *
 * @param pretty 是否格式化输出
 */
internal class IonWriter(
    private val pretty: Boolean,
) {

    /**
     * 写出节点并返回 Ion 文本。
     */
    fun write(node: Node): String {
        val builder = StringBuilder()
        val writer = createWriter(builder)
        writeValue(writer, node)
        writer.finish()
        return builder.toString()
    }

    private fun createWriter(builder: StringBuilder): com.amazon.ion.IonWriter = if (pretty) {
        IonTextWriterBuilder.pretty().withMinimalSystemData().build(builder)
    } else {
        IonTextWriterBuilder.minimal().build(builder)
    }

    private fun writeValue(writer: com.amazon.ion.IonWriter, node: Node) {
        when (node) {
            is ObjectNode -> writeObject(writer, node)
            is ArrayNode -> writeArray(writer, node)
            is StringNode -> writer.writeString(node.value)
            is NumberNode -> writeNumber(writer, node)
            is BooleanNode -> writer.writeBool(node.value)
            is NullNode -> writer.writeNull()
        }
    }

    private fun writeObject(writer: com.amazon.ion.IonWriter, node: ObjectNode) {
        writer.stepIn(IonType.STRUCT)
        for ((name, child) in node.asMap()) {
            writer.setFieldName(name)
            writeValue(writer, child)
        }
        writer.stepOut()
    }

    private fun writeArray(writer: com.amazon.ion.IonWriter, node: ArrayNode) {
        writer.stepIn(IonType.LIST)
        for (child in node) {
            writeValue(writer, child)
        }
        writer.stepOut()
    }

    private fun writeNumber(writer: com.amazon.ion.IonWriter, node: NumberNode) {
        val raw = node.raw
        val isDecimal = raw.any { it == '.' || it == 'e' || it == 'E' || it == 'd' || it == 'D' }
        try {
            if (isDecimal) {
                writer.writeDecimal(BigDecimal(raw))
            } else {
                writer.writeInt(BigInteger(raw))
            }
        } catch (e: NumberFormatException) {
            throw IonException("Invalid number '$raw'", e)
        }
    }
}

/**
 * 将文档树写成 Ion 字符串。
 *
 * @param pretty 是否格式化输出
 * @return Ion 文本
 */
public fun Node.toIonString(pretty: Boolean = false): String = IonWriter(pretty).write(this)
