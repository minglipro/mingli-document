package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

/**
 * 流式文档 Token 写出器。
 *
 * 同时兼容 [DocumentValueWriter]，可以直接接收 Mapper 的实体遍历结果，
 * 不构建完整 Node 树。
 */
public interface DocumentTokenWriter : DocumentValueWriter {

    /** 写出二进制值。 */
    public fun writeBinary(value: ByteArray)

    override fun writeInt(value: Int) {
        writeNumber(value.toString())
    }

    override fun writeLong(value: Long) {
        writeNumber(value.toString())
    }

    override fun writeDouble(value: Double) {
        writeNumber(value.toString())
    }

    override fun writeNode(node: Node) {
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
}
