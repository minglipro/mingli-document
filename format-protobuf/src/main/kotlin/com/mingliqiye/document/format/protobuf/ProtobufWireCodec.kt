package com.mingliqiye.document.format.protobuf

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.EnumValueDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.OneofDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.UninitializedMessageException
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.protobuf.exception.ProtobufException
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException
import java.math.BigInteger
import java.util.Base64
import java.util.LinkedHashMap

/**
 * [Node] 与 [DynamicMessage] 之间的双向映射器。
 *
 * 映射规则：
 * - 标量类型：数字节点；无符号 32 位取值用 [Long] 表示，无符号 64 位取值用无符号十进制字符串表示
 * - `string`：字符串节点；`bytes`：二进制节点，编码时也接受 Base64 字符串
 * - `bool`：布尔节点；`enum`：枚举名称字符串，编码时也接受枚举数字
 * - `message`：对象节点；`repeated`：数组节点；`map`：对象节点，key 统一转为字符串
 * - `optional`/`required`：字段存在性由对象节点是否包含该 key 表示，`null` 表示未设置
 *
 * 解析时未知字段由 [ProtobufUnknownFieldPolicy] 决定；编码时对象节点中出现 Descriptor 未声明的
 * 字段名一律抛出 [ProtobufException]。
 */
internal object ProtobufWireCodec {

    private val INT32_MIN: BigInteger = BigInteger.valueOf(Int.MIN_VALUE.toLong())
    private val INT32_MAX: BigInteger = BigInteger.valueOf(Int.MAX_VALUE.toLong())
    private val INT64_MIN: BigInteger = BigInteger.valueOf(Long.MIN_VALUE)
    private val INT64_MAX: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)
    private val UINT32_MAX: BigInteger = BigInteger("4294967295")
    private val UINT64_MAX: BigInteger = BigInteger("18446744073709551615")

    /**
     * 将 [Node] 编码为 Protobuf 二进制。
     *
     * @param descriptor 目标 message 描述符
     * @param node 对象节点
     * @return Protobuf 二进制内容
     * @throws ProtobufException 节点结构与 Descriptor 不匹配时抛出
     */
    fun encode(descriptor: Descriptor, node: Node): ByteArray = encodeMessage(descriptor, node, descriptor.fullName).toByteArray()

    /**
     * 将 Protobuf 二进制解析为 [Node]。
     *
     * @param descriptor 目标 message 描述符
     * @param source Protobuf 二进制内容
     * @param unknownFieldPolicy 未知字段处理策略
     * @return 对象节点
     * @throws ProtobufParseException 二进制内容非法、缺少必填字段或未知字段策略为 FAIL 时抛出
     */
    fun decode(
        descriptor: Descriptor,
        source: ByteArray,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        maxDepth: Int = 0,
    ): Node {
        val message = try {
            DynamicMessage.parseFrom(descriptor, source)
        } catch (e: InvalidProtocolBufferException) {
            throw ProtobufParseException("解析 Protobuf 消息 ${descriptor.fullName} 失败: ${e.message}", e)
        }
        return decodeMessage(descriptor, message, unknownFieldPolicy, descriptor.fullName, 1, maxDepth)
    }

    private fun encodeMessage(descriptor: Descriptor, node: Node, path: String): DynamicMessage {
        val objectNode = requireObject(node, path)
        val builder = DynamicMessage.newBuilder(descriptor)
        val oneofFields = LinkedHashMap<OneofDescriptor, String>()
        for ((name, child) in objectNode.asMap()) {
            val field = descriptor.findFieldByName(name)
                ?: throw ProtobufException("$path 的字段 '$name' 未在消息 ${descriptor.fullName} 中声明")
            if (child.isNull) {
                if (field.isRequired) {
                    throw ProtobufException("$path.$name 是必填字段，不能为 null")
                }
                continue
            }
            if (field.type == FieldDescriptor.Type.GROUP) {
                throw ProtobufException("$path.$name 为 group 字段，暂不支持")
            }
            val oneof = field.containingOneof
            if (oneof != null) {
                val previous = oneofFields.put(oneof, name)
                if (previous != null) {
                    throw ProtobufException("$path 的 oneof '${oneof.name}' 同时设置了 '$previous' 与 '$name'")
                }
            }
            val fieldPath = "$path.$name"
            when {
                field.isMapField -> encodeMapField(builder, field, child, fieldPath)
                field.isRepeated -> encodeRepeatedField(builder, field, child, fieldPath)
                else -> builder.setField(field, encodeSingularValue(field, child, fieldPath))
            }
        }
        for (field in descriptor.fields) {
            if (field.isRequired && !builder.hasField(field)) {
                throw ProtobufException("$path 缺少必填字段 '${field.name}'")
            }
        }
        return try {
            builder.build()
        } catch (e: UninitializedMessageException) {
            throw ProtobufException("$path 的 Protobuf 消息未初始化: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw ProtobufException("$path 的 Protobuf 消息编码失败: ${e.message}", e)
        }
    }

    private fun encodeRepeatedField(builder: DynamicMessage.Builder, field: FieldDescriptor, node: Node, path: String) {
        val arrayNode = requireArray(node, path)
        for (index in 0 until arrayNode.size) {
            val element = arrayNode[index]
            val elementPath = "$path[$index]"
            if (element.isNull) {
                throw ProtobufException("$elementPath 为 repeated 元素，不能为 null")
            }
            builder.addRepeatedField(field, encodeSingularValue(field, element, elementPath))
        }
    }

    private fun encodeMapField(builder: DynamicMessage.Builder, field: FieldDescriptor, node: Node, path: String) {
        val objectNode = requireObject(node, path)
        val entryDescriptor = field.messageType
        val keyField = entryDescriptor.findFieldByName("key")
            ?: throw ProtobufException("$path 的 map entry 缺少 key 字段")
        val valueField = entryDescriptor.findFieldByName("value")
            ?: throw ProtobufException("$path 的 map entry 缺少 value 字段")
        for ((key, valueNode) in objectNode.asMap()) {
            val entryPath = "$path[$key]"
            if (valueNode.isNull) {
                throw ProtobufException("$entryPath 的 map 值不能为 null")
            }
            val entryBuilder = DynamicMessage.newBuilder(entryDescriptor)
            entryBuilder.setField(keyField, encodeMapKey(keyField, key, entryPath))
            entryBuilder.setField(valueField, encodeSingularValue(valueField, valueNode, entryPath))
            builder.addRepeatedField(field, entryBuilder.build())
        }
    }

    private fun encodeSingularValue(field: FieldDescriptor, node: Node, path: String): Any = when (field.type) {
        FieldDescriptor.Type.DOUBLE -> doubleValue(node, path)
        FieldDescriptor.Type.FLOAT -> floatValue(node, path)
        FieldDescriptor.Type.INT64,
        FieldDescriptor.Type.SINT64,
        FieldDescriptor.Type.SFIXED64,
        -> int64Value(node, path)

        FieldDescriptor.Type.UINT64,
        FieldDescriptor.Type.FIXED64,
        -> uint64Value(node, path)

        FieldDescriptor.Type.INT32,
        FieldDescriptor.Type.SINT32,
        FieldDescriptor.Type.SFIXED32,
        -> int32Value(node, path)

        FieldDescriptor.Type.UINT32,
        FieldDescriptor.Type.FIXED32,
        -> uint32Value(node, path)

        FieldDescriptor.Type.BOOL -> requireBoolean(node, path)
        FieldDescriptor.Type.STRING -> requireString(node, path)
        FieldDescriptor.Type.BYTES -> byteStringValue(node, path)
        FieldDescriptor.Type.ENUM -> enumValue(field, node, path)
        FieldDescriptor.Type.MESSAGE -> encodeMessage(field.messageType, node, path)
        FieldDescriptor.Type.GROUP -> throw ProtobufException("$path 为 group 字段，暂不支持")
        else -> throw ProtobufException("$path 的类型 ${field.type} 不受支持")
    }

    private fun encodeMapKey(keyField: FieldDescriptor, key: String, path: String): Any = when (keyField.type) {
        FieldDescriptor.Type.STRING -> key
        FieldDescriptor.Type.BOOL -> when (key.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw ProtobufException("$path 的 map 布尔 key 必须是 'true' 或 'false'，实际为 '$key'")
        }

        FieldDescriptor.Type.INT32,
        FieldDescriptor.Type.SINT32,
        FieldDescriptor.Type.SFIXED32,
        -> int32Value(NumberNode.ofRaw(key), path)

        FieldDescriptor.Type.UINT32,
        FieldDescriptor.Type.FIXED32,
        -> uint32Value(NumberNode.ofRaw(key), path)

        FieldDescriptor.Type.INT64,
        FieldDescriptor.Type.SINT64,
        FieldDescriptor.Type.SFIXED64,
        -> int64Value(NumberNode.ofRaw(key), path)

        FieldDescriptor.Type.UINT64,
        FieldDescriptor.Type.FIXED64,
        -> uint64Value(NumberNode.ofRaw(key), path)

        else -> throw ProtobufException("$path 的 map key 类型 ${keyField.type} 不受支持")
    }

    private fun requireObject(node: Node, path: String): ObjectNode = node.asObjectOrNull()
        ?: throw ProtobufException("$path 需要对象节点，实际为 ${node.type}")

    private fun requireArray(node: Node, path: String): ArrayNode = node.asArrayOrNull()
        ?: throw ProtobufException("$path 需要数组节点，实际为 ${node.type}")

    private fun requireNumber(node: Node, path: String): NumberNode = node.asNumberOrNull()
        ?: throw ProtobufException("$path 需要数字节点，实际为 ${node.type}")

    private fun requireString(node: Node, path: String): String = node.asStringOrNull()
        ?: throw ProtobufException("$path 需要字符串节点，实际为 ${node.type}")

    private fun requireBoolean(node: Node, path: String): Boolean = node.asBooleanOrNull()
        ?: throw ProtobufException("$path 需要布尔节点，实际为 ${node.type}")

    private fun integerValue(node: Node, path: String): BigInteger {
        val number = requireNumber(node, path)
        return try {
            number.toBigInteger()
        } catch (e: NumberFormatException) {
            throw ProtobufException("$path 需要整数，实际为 '${number.raw}'", e)
        } catch (e: ArithmeticException) {
            throw ProtobufException("$path 需要整数，实际为 '${number.raw}'", e)
        }
    }

    private fun int32Value(node: Node, path: String): Int {
        val value = integerValue(node, path)
        if (value < INT32_MIN || value > INT32_MAX) {
            throw ProtobufException("$path 取值 $value 超出 int32 范围")
        }
        return value.toInt()
    }

    private fun uint32Value(node: Node, path: String): Int {
        val value = integerValue(node, path)
        if (value.signum() < 0 || value > UINT32_MAX) {
            throw ProtobufException("$path 取值 $value 超出 uint32 范围")
        }
        return value.toLong().toInt()
    }

    private fun int64Value(node: Node, path: String): Long {
        val value = integerValue(node, path)
        if (value < INT64_MIN || value > INT64_MAX) {
            throw ProtobufException("$path 取值 $value 超出 int64 范围")
        }
        return value.toLong()
    }

    private fun uint64Value(node: Node, path: String): Long {
        val value = integerValue(node, path)
        if (value.signum() < 0 || value > UINT64_MAX) {
            throw ProtobufException("$path 取值 $value 超出 uint64 范围")
        }
        return value.toLong()
    }

    private fun doubleValue(node: Node, path: String): Double {
        val value = requireNumber(node, path).toDouble()
        if (!value.isFinite()) {
            throw ProtobufException("$path 的 double 取值必须是有限数")
        }
        return value
    }

    private fun floatValue(node: Node, path: String): Float {
        val value = requireNumber(node, path).toFloat()
        if (!value.isFinite()) {
            throw ProtobufException("$path 的 float 取值必须是有限数")
        }
        return value
    }

    private fun byteStringValue(node: Node, path: String): ByteString = when (node) {
        is BinaryNode -> ByteString.copyFrom(node.value)
        is StringNode -> try {
            ByteString.copyFrom(Base64.getDecoder().decode(node.value))
        } catch (e: IllegalArgumentException) {
            throw ProtobufException("$path 的字符串不是合法的 Base64 二进制内容", e)
        }

        else -> throw ProtobufException("$path 需要二进制节点或 Base64 字符串，实际为 ${node.type}")
    }

    private fun enumValue(field: FieldDescriptor, node: Node, path: String): EnumValueDescriptor {
        val enumType = field.enumType
        val number = node.asNumberOrNull()
        if (number != null) {
            val enumNumber = try {
                number.toInt()
            } catch (e: ArithmeticException) {
                throw ProtobufException("$path 的枚举数字需要 int32 范围，实际为 '${number.raw}'", e)
            } catch (e: NumberFormatException) {
                throw ProtobufException("$path 的枚举数字需要 int32 范围，实际为 '${number.raw}'", e)
            }
            return enumType.findValueByNumberCreatingIfUnknown(enumNumber)
        }
        val name = node.asStringOrNull()
            ?: throw ProtobufException("$path 需要枚举名称字符串或枚举数字，实际为 ${node.type}")
        return enumType.findValueByName(name)
            ?: throw ProtobufException("$path 的枚举值 '$name' 不存在于枚举 ${enumType.fullName}")
    }

    private fun checkDepth(depth: Int, maxDepth: Int, path: String) {
        if (maxDepth > 0 && depth > maxDepth) {
            throw ProtobufParseException("$path 的嵌套深度 $depth 超过 maxDepth $maxDepth")
        }
    }

    private fun decodeMessage(
        descriptor: Descriptor,
        message: DynamicMessage,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        path: String,
        depth: Int,
        maxDepth: Int,
    ): ObjectNode {
        checkDepth(depth, maxDepth, path)
        checkUnknownFields(descriptor, message, unknownFieldPolicy, path)
        val fieldNodes = LinkedHashMap<String, Node>()
        for (field in descriptor.fields) {
            decodeField(field, message, unknownFieldPolicy, path, depth, maxDepth)?.let { fieldNodes[field.name] = it }
        }
        return ObjectNode.from(fieldNodes)
    }

    private fun decodeField(
        field: FieldDescriptor,
        message: DynamicMessage,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        path: String,
        depth: Int,
        maxDepth: Int,
    ): Node? {
        if (field.type == FieldDescriptor.Type.GROUP) {
            throw ProtobufParseException("$path.${field.name} 为 group 字段，暂不支持")
        }
        val fieldPath = "$path.${field.name}"
        if (field.isMapField) {
            return decodeMapField(field, message, unknownFieldPolicy, fieldPath, depth, maxDepth)
        }
        if (field.isRepeated) {
            val values = message.getField(field) as List<*>
            if (values.isEmpty()) {
                return null
            }
            val elements = values.mapIndexed { index, element ->
                val value = element
                    ?: throw ProtobufParseException("$fieldPath[$index] 的 repeated 元素为 null")
                decodeValue(field, value, unknownFieldPolicy, "$fieldPath[$index]", depth, maxDepth)
            }
            return ArrayNode.from(elements)
        }
        if (field.hasPresence()) {
            if (!message.hasField(field)) {
                return null
            }
            return decodeValue(field, message.getField(field), unknownFieldPolicy, fieldPath, depth, maxDepth)
        }
        val value = message.getField(field)
        if (isDefaultValue(field, value)) {
            return null
        }
        return decodeValue(field, value, unknownFieldPolicy, fieldPath, depth, maxDepth)
    }

    private fun decodeMapField(
        field: FieldDescriptor,
        message: DynamicMessage,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        path: String,
        depth: Int,
        maxDepth: Int,
    ): Node? {
        val entries = message.getField(field) as List<*>
        if (entries.isEmpty()) {
            return null
        }
        val entryDescriptor = field.messageType
        val keyField = entryDescriptor.findFieldByName("key")
            ?: throw ProtobufParseException("$path 的 map entry 缺少 key 字段")
        val valueField = entryDescriptor.findFieldByName("value")
            ?: throw ProtobufParseException("$path 的 map entry 缺少 value 字段")
        val mapNodes = LinkedHashMap<String, Node>()
        for (entry in entries) {
            val entryMessage = entry as? DynamicMessage
                ?: throw ProtobufParseException("$path 的 map entry 不是动态消息")
            checkUnknownFields(entryDescriptor, entryMessage, unknownFieldPolicy, path)
            val key = mapKeyToString(keyField, entryMessage.getField(keyField), path)
            mapNodes[key] = decodeValue(
                valueField,
                entryMessage.getField(valueField),
                unknownFieldPolicy,
                "$path[$key]",
                depth,
                maxDepth,
            )
        }
        return ObjectNode.from(mapNodes)
    }

    private fun mapKeyToString(keyField: FieldDescriptor, value: Any, path: String): String = when (keyField.type) {
        FieldDescriptor.Type.STRING -> value as String
        FieldDescriptor.Type.BOOL -> if (value as Boolean) "true" else "false"
        FieldDescriptor.Type.INT32,
        FieldDescriptor.Type.SINT32,
        FieldDescriptor.Type.SFIXED32,
        -> (value as Int).toString()

        FieldDescriptor.Type.UINT32,
        FieldDescriptor.Type.FIXED32,
        -> Integer.toUnsignedString(value as Int)

        FieldDescriptor.Type.INT64,
        FieldDescriptor.Type.SINT64,
        FieldDescriptor.Type.SFIXED64,
        -> (value as Long).toString()

        FieldDescriptor.Type.UINT64,
        FieldDescriptor.Type.FIXED64,
        -> java.lang.Long.toUnsignedString(value as Long)

        else -> throw ProtobufParseException("$path 的 map key 类型 ${keyField.type} 不受支持")
    }

    private fun decodeValue(
        field: FieldDescriptor,
        value: Any,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        path: String,
        depth: Int,
        maxDepth: Int,
    ): Node = when (field.type) {
        FieldDescriptor.Type.DOUBLE -> {
            val doubleValue = value as Double
            if (!doubleValue.isFinite()) {
                throw ProtobufParseException("$path 的 double 值为 NaN/Infinity，无法映射为文档数字节点")
            }
            NumberNode.of(doubleValue)
        }

        FieldDescriptor.Type.FLOAT -> {
            val floatValue = value as Float
            if (!floatValue.isFinite()) {
                throw ProtobufParseException("$path 的 float 值为 NaN/Infinity，无法映射为文档数字节点")
            }
            NumberNode.of(floatValue)
        }

        FieldDescriptor.Type.INT64,
        FieldDescriptor.Type.SINT64,
        FieldDescriptor.Type.SFIXED64,
        -> NumberNode.of(value as Long)

        FieldDescriptor.Type.UINT64,
        FieldDescriptor.Type.FIXED64,
        -> NumberNode.ofRaw(java.lang.Long.toUnsignedString(value as Long))

        FieldDescriptor.Type.INT32,
        FieldDescriptor.Type.SINT32,
        FieldDescriptor.Type.SFIXED32,
        -> NumberNode.of(value as Int)

        FieldDescriptor.Type.UINT32,
        FieldDescriptor.Type.FIXED32,
        -> NumberNode.of(Integer.toUnsignedLong(value as Int))

        FieldDescriptor.Type.BOOL -> BooleanNode.of(value as Boolean)
        FieldDescriptor.Type.STRING -> StringNode.of(value as String)
        FieldDescriptor.Type.BYTES -> BinaryNode.of((value as ByteString).toByteArray())
        FieldDescriptor.Type.ENUM -> enumNode(value as EnumValueDescriptor)
        FieldDescriptor.Type.MESSAGE -> decodeMessage(
            field.messageType,
            value as DynamicMessage,
            unknownFieldPolicy,
            path,
            depth + 1,
            maxDepth,
        )
        FieldDescriptor.Type.GROUP -> throw ProtobufParseException("$path 为 group 字段，暂不支持")
        else -> throw ProtobufParseException("$path 的类型 ${field.type} 不受支持")
    }

    private fun enumNode(value: EnumValueDescriptor): Node = if (value.index >= 0) {
        StringNode.of(value.name)
    } else {
        NumberNode.of(value.number)
    }

    private fun isDefaultValue(field: FieldDescriptor, value: Any): Boolean = when (field.javaType) {
        FieldDescriptor.JavaType.INT -> (value as Int) == 0
        FieldDescriptor.JavaType.LONG -> (value as Long) == 0L
        FieldDescriptor.JavaType.FLOAT -> (value as Float) == 0.0f
        FieldDescriptor.JavaType.DOUBLE -> (value as Double) == 0.0
        FieldDescriptor.JavaType.BOOLEAN -> value as Boolean == false
        FieldDescriptor.JavaType.STRING -> value == ""
        FieldDescriptor.JavaType.BYTE_STRING -> value == ByteString.EMPTY
        FieldDescriptor.JavaType.ENUM ->
            (value as EnumValueDescriptor).number == (field.defaultValue as EnumValueDescriptor).number

        FieldDescriptor.JavaType.MESSAGE -> false
    }

    private fun checkUnknownFields(
        descriptor: Descriptor,
        message: DynamicMessage,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        path: String,
    ) {
        if (unknownFieldPolicy != ProtobufUnknownFieldPolicy.FAIL) {
            return
        }
        val unknownFieldNumbers = message.unknownFields.asMap().keys.sorted()
        if (unknownFieldNumbers.isNotEmpty()) {
            throw ProtobufParseException(
                "$path 包含未知字段编号 $unknownFieldNumbers（消息 ${descriptor.fullName}，策略为 FAIL）",
            )
        }
    }
}
