package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.EnumDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.mingliqiye.document.format.protobuf.exception.ProtobufException

/**
 * 把外部 [Descriptor] 转换为 [ProtobufSchema]。
 *
 * 转换规则：
 * - 字段类型/编号/repeated/required/presence 与 oneof 归属原样保留
 * - message/enum 字段记录类型引用：根 message 嵌套范围内的类型记为相对名称（如 `Address`、
 *   `Middle.Inner`），根 message 自身记为 `.` 开头的全限定名，根之外的类型也记为全限定名
 * - map 字段由 map entry 的 key/value 字段还原为 [ProtobufTypeReference.MapType]；map entry
 *   不作为用户嵌套 message 保留
 * - 嵌套 message 与 enum 递归转换为 [ProtobufMessage]/[ProtobufEnum]
 *
 * group 字段不支持，会抛出 [ProtobufException]。
 */
internal object ProtobufSchemaConverter {

    /**
     * 转换根 message 描述符。
     *
     * @param descriptor 根 message 描述符
     * @return Protobuf Schema
     */
    fun convert(descriptor: Descriptor): ProtobufSchema {
        val packageName = descriptor.file.getPackage().takeIf { it.isNotEmpty() }
        val messageName = if (packageName == null) {
            descriptor.fullName
        } else {
            descriptor.fullName.removePrefix("$packageName.")
        }
        val context = ConversionContext(descriptor.fullName)
        val content = convertMessageContent(descriptor, context)
        return ProtobufSchema(
            messageName,
            content.fields,
            packageName,
            content.nestedMessages,
            content.enums,
        )
    }

    private class ConversionContext(private val rootFullName: String) {

        fun referenceName(typeFullName: String): String = when {
            typeFullName == rootFullName -> ".$rootFullName"
            typeFullName.startsWith("$rootFullName.") -> typeFullName.removePrefix("$rootFullName.")
            else -> ".$typeFullName"
        }
    }

    private class ConvertedMessage(
        val fields: List<ProtobufSchemaField>,
        val nestedMessages: List<ProtobufMessage>,
        val enums: List<ProtobufEnum>,
    )

    private fun convertMessageContent(descriptor: Descriptor, context: ConversionContext): ConvertedMessage {
        val fields = descriptor.fields.map { convertField(it, context) }
        val nestedMessages = descriptor.nestedTypes
            .filter { !it.options.mapEntry }
            .map { convertNestedMessage(it, context) }
        val enums = descriptor.enumTypes.map { convertEnum(it) }
        return ConvertedMessage(fields, nestedMessages, enums)
    }

    private fun convertNestedMessage(descriptor: Descriptor, context: ConversionContext): ProtobufMessage {
        val content = convertMessageContent(descriptor, context)
        return ProtobufMessage(descriptor.name, content.fields, content.nestedMessages, content.enums)
    }

    private fun convertEnum(descriptor: EnumDescriptor): ProtobufEnum = ProtobufEnum(
        descriptor.name,
        descriptor.values.map { ProtobufEnumValue(it.name, it.number) },
    )

    private fun convertField(field: FieldDescriptor, context: ConversionContext): ProtobufSchemaField {
        val protobufType = protobufTypeOf(field)
        val typeReference = when (protobufType) {
            ProtobufType.MAP -> convertMapType(field, context)
            ProtobufType.MESSAGE -> ProtobufTypeReference.MessageType(context.referenceName(field.messageType.fullName))
            ProtobufType.ENUM -> ProtobufTypeReference.EnumType(context.referenceName(field.enumType.fullName))
            else -> null
        }
        return ProtobufSchemaField(
            name = field.name,
            fieldNumber = field.number,
            protobufType = protobufType,
            repeated = field.isRepeated && !field.isMapField,
            required = field.isRequired,
            nullable = field.hasPresence() && !field.isRequired,
            description = null,
            typeReference = typeReference,
            oneofName = field.realContainingOneof?.name,
        )
    }

    private fun convertMapType(field: FieldDescriptor, context: ConversionContext): ProtobufTypeReference.MapType {
        val entryDescriptor = field.messageType
        val keyField = entryDescriptor.findFieldByName("key")
            ?: throw ProtobufException("map 字段 ${field.fullName} 的 entry 缺少 key 字段")
        val valueField = entryDescriptor.findFieldByName("value")
            ?: throw ProtobufException("map 字段 ${field.fullName} 的 entry 缺少 value 字段")
        val keyType = protobufTypeOf(keyField)
        if (keyType == ProtobufType.MAP) {
            throw ProtobufException("map 字段 ${field.fullName} 的 key 不能是 map")
        }
        val valueType = protobufTypeOf(valueField)
        val valueReference = when (valueType) {
            ProtobufType.MESSAGE -> ProtobufTypeReference.MessageType(context.referenceName(valueField.messageType.fullName))
            ProtobufType.ENUM -> ProtobufTypeReference.EnumType(context.referenceName(valueField.enumType.fullName))
            ProtobufType.MAP -> throw ProtobufException("map 字段 ${field.fullName} 的 value 不能是 map")
            else -> null
        }
        return ProtobufTypeReference.MapType(keyType, valueType, valueReference)
    }

    private fun protobufTypeOf(field: FieldDescriptor): ProtobufType {
        if (field.isMapField) {
            return ProtobufType.MAP
        }
        if (field.type == FieldDescriptor.Type.GROUP) {
            throw ProtobufException("group 字段暂不支持: ${field.fullName}")
        }
        return ProtobufType.fromTypeName(field.type.name.lowercase())
            ?: throw ProtobufException("不支持的 Protobuf 字段类型: ${field.type}")
    }
}
