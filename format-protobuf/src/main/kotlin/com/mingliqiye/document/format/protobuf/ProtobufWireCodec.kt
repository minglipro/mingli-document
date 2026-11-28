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
 * - `string`：字符串节点，写出时校验字符串可编码为合法 UTF-8；`bytes`：二进制节点，
 *   编码时也接受 Base64 字符串
 * - `bool`：布尔节点；`enum`：枚举名称字符串，编码时也接受枚举数字
 * - `message`/`group`：对象节点；`repeated`：数组节点；`map`：对象节点，key 统一转为字符串
 * - `optional`/`required`/proto3 `optional`：字段存在性由对象节点是否包含该 key 表示，`null`
 *   表示未设置；写出时显式设置为默认值仍会保留 presence
 * - proto3 无 presence 的 singular 标量字段：解析时省略默认值，写出时默认值不写 wire（与官方
 *   编码一致，语义不变）
 * - extension：对象字段名按 [ProtobufExtensionRegistry.documentName] 解析；解析与写出都只处理
 *   注册表中属于当前消息的 extension
 *
 * 解析时未知字段由 [ProtobufUnknownFieldPolicy] 决定：`FAIL` 抛错，`IGNORE` 丢弃，`PRESERVE`
 * 映射为 [ProtobufUnknownFields.FIELD_NAME] 对象字段并支持再写出。编码时对象节点中出现 Descriptor
 * 与注册表都未声明的字段名默认抛出 [ProtobufException]；`ignoreUnknownFields` 为 `true`
 * （对应 [ProtobufWriteFeature.IGNORE_UNKNOWN]）时改为静默跳过。
 * [ProtobufUnknownFields.FIELD_NAME] 仅在 `PRESERVE` 策略下接受。
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
     * @param unknownFieldPolicy 未知字段处理策略，决定是否接受 [ProtobufUnknownFields.FIELD_NAME]
     * @param extensions extension 注册表
     * @param ignoreUnknownFields 是否忽略未声明的字段名（上游实现
     *   `JsonGenerator.Feature.IGNORE_UNKNOWN`，见 [ProtobufWriteFeature.IGNORE_UNKNOWN]）
     * @return Protobuf 二进制内容
     * @throws ProtobufException 节点结构与 Descriptor 不匹配时抛出
     */
    fun encode(
        descriptor: Descriptor,
        node: Node,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        extensions: ProtobufExtensionRegistry,
        ignoreUnknownFields: Boolean = false,
    ): ByteArray = encodeMessage(
        descriptor,
        node,
        descriptor.fullName,
        unknownFieldPolicy,
        extensions,
        ignoreUnknownFields,
    ).toByteArray()

    /**
     * 将 Protobuf 二进制解析为 [Node]。
     *
     * @param descriptor 目标 message 描述符
     * @param source Protobuf 二进制内容
     * @param unknownFieldPolicy 未知字段处理策略
     * @param maxDepth 最大嵌套深度，`0` 表示不限制
     * @param extensions extension 注册表
     * @return 对象节点
     * @throws ProtobufParseException 二进制内容非法、缺少必填字段或未知字段策略为 FAIL 时抛出
     */
    fun decode(
        descriptor: Descriptor,
        source: ByteArray,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        maxDepth: Int = 0,
        extensions: ProtobufExtensionRegistry = ProtobufExtensionRegistry.EMPTY,
    ): Node {
        if (descriptor.options.messageSetWireFormat) {
            ProtobufMessageSetSupport.validate(descriptor, source, extensions)
        }
        val message = try {
            DynamicMessage.parseFrom(descriptor, source, extensions.protobufRegistry)
        } catch (e: InvalidProtocolBufferException) {
            throw ProtobufParseException("解析 Protobuf 消息 ${descriptor.fullName} 失败: ${e.message}", e)
        }
        return decodeMessage(descriptor, message, unknownFieldPolicy, descriptor.fullName, 1, maxDepth, extensions)
    }

    private fun encodeMessage(
        descriptor: Descriptor,
        node: Node,
        path: String,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        extensions: ProtobufExtensionRegistry,
        ignoreUnknownFields: Boolean,
    ): DynamicMessage {
        val objectNode = requireObject(node, path)
        val builder = DynamicMessage.newBuilder(descriptor)
        val oneofFields = LinkedHashMap<OneofDescriptor, String>()
        for ((name, child) in objectNode.asMap()) {
            if (name == ProtobufUnknownFields.FIELD_NAME) {
                if (unknownFieldPolicy != ProtobufUnknownFieldPolicy.PRESERVE) {
                    throw ProtobufException(
                        "$path 出现保留字段 '${ProtobufUnknownFields.FIELD_NAME}'，只有 " +
                            "${ProtobufUnknownFieldPolicy.PRESERVE} 策略允许写出未知字段",
                    )
                }
                continue
            }
            val field = if (ignoreUnknownFields) {
                resolveFieldOrNull(descriptor, extensions, name)
            } else {
                resolveField(descriptor, extensions, name, path)
            }
            if (field == null) {
                continue
            }
            if (child.isNull) {
                if (field.isRequired) {
                    throw ProtobufException("$path.$name 是必填字段，不能为 null")
                }
                continue
            }
            val oneof = field.realContainingOneof
            if (oneof != null) {
                val previous = oneofFields.put(oneof, name)
                if (previous != null) {
                    throw ProtobufException("$path 的 oneof '${oneof.name}' 同时设置了 '$previous' 与 '$name'")
                }
            }
            val fieldPath = "$path.$name"
            when {
                field.isMapField -> encodeMapField(builder, field, child, fieldPath, unknownFieldPolicy, extensions, ignoreUnknownFields)
                field.isRepeated -> encodeRepeatedField(builder, field, child, fieldPath, unknownFieldPolicy, extensions, ignoreUnknownFields)
                else -> {
                    val value = encodeSingularValue(field, child, fieldPath, unknownFieldPolicy, extensions, ignoreUnknownFields)
                    if (shouldOmitDefaultValue(field, value)) {
                        continue
                    }
                    builder.setField(field, value)
                }
            }
        }
        for (field in descriptor.fields) {
            if (field.isRequired && !builder.hasField(field)) {
                throw ProtobufException("$path 缺少必填字段 '${field.name}'")
            }
        }
        if (unknownFieldPolicy == ProtobufUnknownFieldPolicy.PRESERVE) {
            val unknownNode = objectNode.asMap()[ProtobufUnknownFields.FIELD_NAME]
            if (unknownNode != null && !unknownNode.isNull) {
                builder.setUnknownFields(
                    ProtobufUnknownFieldSupport.encode(
                        unknownNode,
                        "$path.${ProtobufUnknownFields.FIELD_NAME}",
                        descriptor.options.messageSetWireFormat,
                    ),
                )
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

    private fun resolveField(
        descriptor: Descriptor,
        extensions: ProtobufExtensionRegistry,
        name: String,
        path: String,
    ): FieldDescriptor = resolveFieldOrNull(descriptor, extensions, name)
        ?: throw ProtobufException("$path 的字段 '$name' 未在消息 ${descriptor.fullName} 中声明")

    /**
     * 解析字段名；未声明时返回 `null`（由调用方决定报错还是忽略）。
     */
    private fun resolveFieldOrNull(
        descriptor: Descriptor,
        extensions: ProtobufExtensionRegistry,
        name: String,
    ): FieldDescriptor? {
        descriptor.findFieldByName(name)?.let { return it }
        val extension = extensions.extension(descriptor, name) ?: return null
        if (extension.containingType !== descriptor) {
            throw ProtobufException(
                "$name 的 extension 属于另一个 Descriptor 实例（${extension.containingType.fullName}）；" +
                    "extension 注册表必须与 mapper 使用同一个 Descriptor",
            )
        }
        return extension
    }

    private fun encodeRepeatedField(
        builder: DynamicMessage.Builder,
        field: FieldDescriptor,
        node: Node,
        path: String,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        extensions: ProtobufExtensionRegistry,
        ignoreUnknownFields: Boolean,
    ) {
        val arrayNode = requireArray(node, path)
        for (index in 0 until arrayNode.size) {
            val element = arrayNode[index]
            val elementPath = "$path[$index]"
            if (element.isNull) {
                throw ProtobufException("$elementPath 为 repeated 元素，不能为 null")
            }
            builder.addRepeatedField(
                field,
                encodeSingularValue(field, element, elementPath, unknownFieldPolicy, extensions, ignoreUnknownFields),
            )
        }
    }

    private fun encodeMapField(
        builder: DynamicMessage.Builder,
        field: FieldDescriptor,
        node: Node,
        path: String,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        extensions: ProtobufExtensionRegistry,
        ignoreUnknownFields: Boolean,
    ) {
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
            val keyValue = encodeMapKey(keyField, key, entryPath)
            if (keyField.hasPresence() || !isDefaultValue(keyField, keyValue)) {
                entryBuilder.setField(keyField, keyValue)
            }
            val value = encodeSingularValue(valueField, valueNode, entryPath, unknownFieldPolicy, extensions, ignoreUnknownFields)
            if (valueField.hasPresence() || !isDefaultValue(valueField, value)) {
                entryBuilder.setField(valueField, value)
            }
            builder.addRepeatedField(field, entryBuilder.build())
        }
    }

    private fun encodeSingularValue(
        field: FieldDescriptor,
        node: Node,
        path: String,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        extensions: ProtobufExtensionRegistry,
        ignoreUnknownFields: Boolean,
    ): Any = when (field.type) {
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
        FieldDescriptor.Type.MESSAGE -> encodeMessage(field.messageType, node, path, unknownFieldPolicy, extensions, ignoreUnknownFields)
        FieldDescriptor.Type.GROUP -> encodeMessage(field.messageType, node, path, unknownFieldPolicy, extensions, ignoreUnknownFields)
        else -> throw ProtobufException("$path 的类型 ${field.type} 不受支持")
    }

    private fun encodeMapKey(keyField: FieldDescriptor, key: String, path: String): Any = when (keyField.type) {
        FieldDescriptor.Type.STRING -> {
            if (!isValidUtf8(key)) {
                throw ProtobufException("$path 的 map 字符串 key 包含未配对的 UTF-16 代理项，无法编码为合法 UTF-8")
            }
            key
        }

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

    private fun requireString(node: Node, path: String): String {
        val value = node.asStringOrNull()
            ?: throw ProtobufException("$path 需要字符串节点，实际为 ${node.type}")
        if (!isValidUtf8(value)) {
            throw ProtobufException("$path 的字符串包含未配对的 UTF-16 代理项，无法编码为合法 UTF-8")
        }
        return value
    }

    private fun requireBoolean(node: Node, path: String): Boolean = node.asBooleanOrNull()
        ?: throw ProtobufException("$path 需要布尔节点，实际为 ${node.type}")

    /**
     * 校验 [value] 可以编码为合法 UTF-8：UTF-16 中不允许出现未配对的高/低代理项。
     *
     * Protobuf 的 `string` 必须是合法 UTF-8；Java 的 `String` 是 UTF-16，含未配对代理项时
     * `getBytes(UTF_8)` 会静默替换为 `?`，导致写出的数据与内存值不一致，因此提前拒绝。
     */
    private fun isValidUtf8(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                Character.isHighSurrogate(char) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) {
                        return false
                    }
                    index += 2
                }

                Character.isLowSurrogate(char) -> return false
                else -> index++
            }
        }
        return true
    }

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
        extensions: ProtobufExtensionRegistry,
    ): ObjectNode {
        checkDepth(depth, maxDepth, path)
        checkUnknownFields(descriptor, message, unknownFieldPolicy, path, extensions)
        val fieldNodes = LinkedHashMap<String, Node>()
        for (field in descriptor.fields) {
            decodeField(field, message, unknownFieldPolicy, path, depth, maxDepth, extensions)
                ?.let { fieldNodes[field.name] = it }
        }
        for (field in message.allFields.keys) {
            if (!field.isExtension) {
                continue
            }
            decodeField(field, message, unknownFieldPolicy, path, depth, maxDepth, extensions)
                ?.let { fieldNodes[extensions.documentName(field)] = it }
        }
        if (unknownFieldPolicy == ProtobufUnknownFieldPolicy.PRESERVE) {
            val unknownFields = message.unknownFields
            if (!unknownFields.asMap().isEmpty()) {
                fieldNodes[ProtobufUnknownFields.FIELD_NAME] = ProtobufUnknownFieldSupport.decode(unknownFields)
            }
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
        extensions: ProtobufExtensionRegistry,
    ): Node? {
        val fieldPath = "$path.${field.name}"
        if (field.isMapField) {
            return decodeMapField(field, message, unknownFieldPolicy, fieldPath, depth, maxDepth, extensions)
        }
        if (field.isRepeated) {
            val values = message.getField(field) as List<*>
            if (values.isEmpty()) {
                return null
            }
            val elements = values.mapIndexed { index, element ->
                val value = element
                    ?: throw ProtobufParseException("$fieldPath[$index] 的 repeated 元素为 null")
                decodeValue(field, value, unknownFieldPolicy, "$fieldPath[$index]", depth, maxDepth, extensions)
            }
            return ArrayNode.from(elements)
        }
        if (field.hasPresence()) {
            if (!message.hasField(field)) {
                return null
            }
            return decodeValue(field, message.getField(field), unknownFieldPolicy, fieldPath, depth, maxDepth, extensions)
        }
        val value = message.getField(field)
        if (isDefaultValue(field, value)) {
            return null
        }
        return decodeValue(field, value, unknownFieldPolicy, fieldPath, depth, maxDepth, extensions)
    }

    private fun decodeMapField(
        field: FieldDescriptor,
        message: DynamicMessage,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        path: String,
        depth: Int,
        maxDepth: Int,
        extensions: ProtobufExtensionRegistry,
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
            checkUnknownFields(entryDescriptor, entryMessage, unknownFieldPolicy, path, extensions)
            val key = mapKeyToString(keyField, entryMessage.getField(keyField), path)
            mapNodes[key] = decodeValue(
                valueField,
                entryMessage.getField(valueField),
                unknownFieldPolicy,
                "$path[$key]",
                depth,
                maxDepth,
                extensions,
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
        extensions: ProtobufExtensionRegistry,
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
        FieldDescriptor.Type.MESSAGE,
        FieldDescriptor.Type.GROUP,
        -> decodeMessage(
            field.messageType,
            value as DynamicMessage,
            unknownFieldPolicy,
            path,
            depth + 1,
            maxDepth,
            extensions,
        )

        else -> throw ProtobufParseException("$path 的类型 ${field.type} 不受支持")
    }

    private fun enumNode(value: EnumValueDescriptor): Node = if (value.index >= 0) {
        StringNode.of(value.name)
    } else {
        NumberNode.of(value.number)
    }

    /**
     * 字段取值是否为对应类型的默认值（proto3 无 presence 字段用于判断是否省略写出）。
     */
    private fun isDefaultValue(field: FieldDescriptor, value: Any): Boolean = when (field.javaType) {
        FieldDescriptor.JavaType.INT -> (value as Int) == 0
        FieldDescriptor.JavaType.LONG -> (value as Long) == 0L
        FieldDescriptor.JavaType.FLOAT -> (value as Float) == 0.0f
        FieldDescriptor.JavaType.DOUBLE -> (value as Double) == 0.0
        FieldDescriptor.JavaType.BOOLEAN -> value as Boolean == false
        FieldDescriptor.JavaType.STRING -> value == ""
        FieldDescriptor.JavaType.BYTE_STRING -> (value as ByteString).isEmpty
        FieldDescriptor.JavaType.ENUM ->
            (value as EnumValueDescriptor).number == (field.defaultValue as EnumValueDescriptor).number

        FieldDescriptor.JavaType.MESSAGE -> false
    }

    /**
     * proto3 无 presence 的 singular 标量字段在写默认值时省略 wire 数据，与官方编码一致。
     *
     * 有 presence 的字段（proto2 optional/required、proto3 `optional`、oneof、message/group）
     * 显式写入默认值时必须保留 presence，不能省略。
     */
    private fun shouldOmitDefaultValue(field: FieldDescriptor, value: Any): Boolean = !field.isRepeated && !field.isMapField && !field.hasPresence() && isDefaultValue(field, value)

    private fun checkUnknownFields(
        descriptor: Descriptor,
        message: DynamicMessage,
        unknownFieldPolicy: ProtobufUnknownFieldPolicy,
        path: String,
        extensions: ProtobufExtensionRegistry,
    ) {
        if (unknownFieldPolicy != ProtobufUnknownFieldPolicy.FAIL) {
            return
        }
        val unknownFieldNumbers = message.unknownFields.asMap().keys.sorted()
        if (unknownFieldNumbers.isEmpty()) {
            return
        }
        val mismatchedExtensionNumbers = unknownFieldNumbers.filter {
            extensions.extensionByTypeName(descriptor.fullName, it) != null
        }
        if (mismatchedExtensionNumbers.isNotEmpty()) {
            throw ProtobufParseException(
                "$path 包含未知字段编号 $unknownFieldNumbers（消息 ${descriptor.fullName}，策略为 FAIL）；" +
                    "其中 $mismatchedExtensionNumbers 已注册为 extension，但注册表与 mapper 使用的 Descriptor 不是同一个实例",
            )
        }
        throw ProtobufParseException(
            "$path 包含未知字段编号 $unknownFieldNumbers（消息 ${descriptor.fullName}，策略为 FAIL）",
        )
    }
}
