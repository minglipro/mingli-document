package com.mingliqiye.document.format.protobuf

import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.protobuf.exception.ProtobufException
import java.math.BigInteger
import java.util.Base64
import java.util.LinkedHashMap

/**
 * [UnknownFieldSet] 与 [Node] 之间的双向转换。
 *
 * 文档结构见 [ProtobufUnknownFields]；仅在 [ProtobufUnknownFieldPolicy.PRESERVE] 模式下使用。
 */
internal object ProtobufUnknownFieldSupport {

    private val UINT64_MAX: BigInteger = BigInteger("18446744073709551615")
    private val UINT32_MAX: BigInteger = BigInteger("4294967295")

    /**
     * 把未知字段集合映射为对象节点。
     */
    fun decode(unknownFields: UnknownFieldSet): ObjectNode {
        val fields = LinkedHashMap<String, Node>()
        for ((number, field) in unknownFields.asMap()) {
            val entry = LinkedHashMap<String, Node>()
            if (field.varintList.isNotEmpty()) {
                entry[ProtobufUnknownFields.VARINT] = singleOrArray(field.varintList.map { NumberNode.ofRaw(java.lang.Long.toUnsignedString(it)) })
            }
            if (field.fixed32List.isNotEmpty()) {
                entry[ProtobufUnknownFields.FIXED32] = singleOrArray(field.fixed32List.map { NumberNode.of(Integer.toUnsignedLong(it)) })
            }
            if (field.fixed64List.isNotEmpty()) {
                entry[ProtobufUnknownFields.FIXED64] = singleOrArray(field.fixed64List.map { NumberNode.ofRaw(java.lang.Long.toUnsignedString(it)) })
            }
            if (field.lengthDelimitedList.isNotEmpty()) {
                entry[ProtobufUnknownFields.LENGTH_DELIMITED] = singleOrArray(field.lengthDelimitedList.map { BinaryNode.of(it.toByteArray()) })
            }
            if (field.groupList.isNotEmpty()) {
                entry[ProtobufUnknownFields.GROUPS] = singleOrArray(field.groupList.map { decode(it) })
            }
            fields[number.toString()] = ObjectNode.from(entry)
        }
        return ObjectNode.from(fields)
    }

    /**
     * 把 [ProtobufUnknownFields.FIELD_NAME] 对象节点还原为未知字段集合。
     *
     * @param node [ProtobufUnknownFields.FIELD_NAME] 对应的节点
     * @param path 错误路径
     * @param messageSetWireFormat 目标消息是否为 MessageSet wire format
     */
    fun encode(node: Node, path: String, messageSetWireFormat: Boolean): UnknownFieldSet {
        val objectNode = node.asObjectOrNull()
            ?: throw ProtobufException("$path 需要对象节点，实际为 ${node.type}")
        val builder = UnknownFieldSet.newBuilder()
        for ((numberText, entryNode) in objectNode.asMap()) {
            val number = parseFieldNumber(numberText, path)
            val entryPath = "$path.$numberText"
            builder.mergeField(number, encodeField(entryNode, entryPath, messageSetWireFormat))
        }
        return builder.build()
    }

    private fun parseFieldNumber(numberText: String, path: String): Int {
        val number = numberText.toIntOrNull()
            ?: throw ProtobufException("$path 的字段编号 '$numberText' 不是合法整数")
        if (number < 1 || number > ProtobufSchemaField.MAX_FIELD_NUMBER) {
            throw ProtobufException("$path 的字段编号 $number 超出 1..${ProtobufSchemaField.MAX_FIELD_NUMBER} 范围")
        }
        return number
    }

    private fun encodeField(entryNode: Node, path: String, messageSetWireFormat: Boolean): UnknownFieldSet.Field {
        val entry = entryNode.asObjectOrNull()
            ?: throw ProtobufException("$path 需要对象节点，实际为 ${entryNode.type}")
        val fieldBuilder = UnknownFieldSet.Field.newBuilder()
        var valueCount = 0
        for ((wireType, valueNode) in entry.asMap()) {
            val valuePath = "$path.$wireType"
            when (wireType) {
                ProtobufUnknownFields.VARINT -> {
                    ensureNotMessageSet(wireType, messageSetWireFormat, valuePath)
                    for (value in scalarValues(valueNode, valuePath)) {
                        fieldBuilder.addVarint(unsignedLong(value, UINT64_MAX, valuePath))
                        valueCount++
                    }
                }

                ProtobufUnknownFields.FIXED32 -> {
                    ensureNotMessageSet(wireType, messageSetWireFormat, valuePath)
                    for (value in scalarValues(valueNode, valuePath)) {
                        fieldBuilder.addFixed32(unsignedLong(value, UINT32_MAX, valuePath).toInt())
                        valueCount++
                    }
                }

                ProtobufUnknownFields.FIXED64 -> {
                    ensureNotMessageSet(wireType, messageSetWireFormat, valuePath)
                    for (value in scalarValues(valueNode, valuePath)) {
                        fieldBuilder.addFixed64(unsignedLong(value, UINT64_MAX, valuePath))
                        valueCount++
                    }
                }

                ProtobufUnknownFields.LENGTH_DELIMITED -> {
                    for (value in scalarValues(valueNode, valuePath)) {
                        fieldBuilder.addLengthDelimited(binaryValue(value, valuePath))
                        valueCount++
                    }
                }

                ProtobufUnknownFields.GROUPS -> {
                    ensureNotMessageSet(wireType, messageSetWireFormat, valuePath)
                    for (value in groupValues(valueNode, valuePath)) {
                        fieldBuilder.addGroup(encode(value, valuePath, messageSetWireFormat = false))
                        valueCount++
                    }
                }

                else -> throw ProtobufException(
                    "$valuePath 不是支持的未知字段 wire 类型，可用值为 " +
                        "${ProtobufUnknownFields.VARINT}/${ProtobufUnknownFields.FIXED32}/" +
                        "${ProtobufUnknownFields.FIXED64}/${ProtobufUnknownFields.LENGTH_DELIMITED}/" +
                        ProtobufUnknownFields.GROUPS,
                )
            }
        }
        if (valueCount == 0) {
            throw ProtobufException("$path 至少需要一个未知字段 wire 类型值")
        }
        return fieldBuilder.build()
    }

    private fun ensureNotMessageSet(wireType: String, messageSetWireFormat: Boolean, path: String) {
        if (messageSetWireFormat) {
            throw ProtobufException("$path 的 MessageSet wire format 未知字段只支持 ${ProtobufUnknownFields.LENGTH_DELIMITED}，实际为 $wireType")
        }
    }

    private fun scalarValues(node: Node, path: String): List<Node> = when (node) {
        is ArrayNode -> (0 until node.size).map { node[it] }
        else -> listOf(node)
    }.onEach { value ->
        if (value.isNull) {
            throw ProtobufException("$path 的未知字段值不能为 null")
        }
    }

    private fun groupValues(node: Node, path: String): List<Node> = when (node) {
        is ArrayNode -> (0 until node.size).map { node[it] }
        else -> listOf(node)
    }.onEach { value ->
        if (value !is ObjectNode) {
            throw ProtobufException("$path 的 group 未知字段值需要对象节点，实际为 ${value.type}")
        }
    }

    private fun unsignedLong(node: Node, max: BigInteger, path: String): Long {
        val number = node.asNumberOrNull()
            ?: throw ProtobufException("$path 需要数字节点，实际为 ${node.type}")
        val value = try {
            number.toBigInteger()
        } catch (e: NumberFormatException) {
            throw ProtobufException("$path 需要整数，实际为 '${number.raw}'", e)
        } catch (e: ArithmeticException) {
            throw ProtobufException("$path 需要整数，实际为 '${number.raw}'", e)
        }
        if (value.signum() < 0 || value > max) {
            throw ProtobufException("$path 取值 $value 超出无符号范围 0..$max")
        }
        return value.toLong()
    }

    private fun binaryValue(node: Node, path: String): ByteString = when (node) {
        is BinaryNode -> ByteString.copyFrom(node.value)
        is StringNode -> try {
            ByteString.copyFrom(Base64.getDecoder().decode(node.value))
        } catch (e: IllegalArgumentException) {
            throw ProtobufException("$path 的字符串不是合法的 Base64 二进制内容", e)
        }

        else -> throw ProtobufException("$path 需要二进制节点或 Base64 字符串，实际为 ${node.type}")
    }

    private fun singleOrArray(nodes: List<Node>): Node = if (nodes.size == 1) {
        nodes[0]
    } else {
        ArrayNode.from(nodes)
    }
}
