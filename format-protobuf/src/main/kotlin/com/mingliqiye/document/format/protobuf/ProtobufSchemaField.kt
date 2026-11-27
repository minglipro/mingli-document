package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

/**
 * Protobuf Schema 字段定义。
 *
 * 在格式无关 [SchemaField] 的基础上增加 Protobuf 字段编号、`.proto` 类型、repeated 标记、
 * 复杂类型引用（message/enum/map）与 oneof 归属。
 *
 * message/enum/map 字段的 [protobufType] 只是类型占位，真实类型由 [typeReference] 描述：
 * - [ProtobufType.MESSAGE] 需要 [ProtobufTypeReference.MessageType]
 * - [ProtobufType.ENUM] 需要 [ProtobufTypeReference.EnumType]
 * - [ProtobufType.MAP] 需要 [ProtobufTypeReference.MapType]
 *
 * @property name 字段名
 * @property fieldNumber 字段编号
 * @property protobufType Protobuf 类型
 * @property repeated 是否为 repeated 字段
 * @property required 是否必填
 * @property nullable 是否允许 `null`
 * @property description 字段描述
 * @property typeReference 复杂类型引用；标量字段为 `null`
 * @property oneofName 所属 oneof 名称；`null` 表示不属于 oneof
 */
public class ProtobufSchemaField(
    public val name: String,
    public val fieldNumber: Int,
    public val protobufType: ProtobufType,
    public val repeated: Boolean = false,
    public val required: Boolean = true,
    public val nullable: Boolean = false,
    public val description: String? = null,
    public val typeReference: ProtobufTypeReference? = null,
    public val oneofName: String? = null,
) {

    /** Protobuf 类型名，如 `int32`、`string`。 */
    public val protobufTypeName: String get() = protobufType.typeName

    /** 元素类型；非 repeated 字段即字段类型。 */
    public val elementType: SchemaType get() = protobufType.schemaType

    /** 映射到格式无关 Schema 的类型，repeated 字段为 [SchemaType.ARRAY]。 */
    public val schemaType: SchemaType get() = if (repeated) SchemaType.ARRAY else protobufType.schemaType

    /** message 字段引用的类型名；非 message 字段为 `null`。 */
    public val messageTypeName: String?
        get() = (typeReference as? ProtobufTypeReference.MessageType)?.typeName

    /** enum 字段引用的类型名；非 enum 字段为 `null`。 */
    public val enumTypeName: String?
        get() = (typeReference as? ProtobufTypeReference.EnumType)?.typeName

    /** map 字段的类型定义；非 map 字段为 `null`。 */
    public val mapType: ProtobufTypeReference.MapType?
        get() = typeReference as? ProtobufTypeReference.MapType

    init {
        require(fieldNamePattern.matches(name)) { "非法 Protobuf 字段名: $name" }
        require(fieldNumber in MIN_FIELD_NUMBER..MAX_FIELD_NUMBER) {
            "非法 Protobuf 字段编号: $fieldNumber，必须在 $MIN_FIELD_NUMBER..$MAX_FIELD_NUMBER 范围内"
        }
        require(fieldNumber !in reservedFieldNumbers) {
            "Protobuf 保留字段编号: $fieldNumber，不能使用 $reservedFieldNumbers 内的编号"
        }
        checkTypeReference()
        checkOneof()
    }

    private fun checkTypeReference() {
        val reference = typeReference ?: return
        when (reference) {
            is ProtobufTypeReference.MessageType ->
                require(protobufType == ProtobufType.MESSAGE) {
                    "message 类型引用只能用于 MESSAGE 字段: $name"
                }

            is ProtobufTypeReference.EnumType ->
                require(protobufType == ProtobufType.ENUM) {
                    "enum 类型引用只能用于 ENUM 字段: $name"
                }

            is ProtobufTypeReference.MapType -> {
                require(protobufType == ProtobufType.MAP) { "map 类型引用只能用于 MAP 字段: $name" }
                require(!repeated) { "map 字段不能是 repeated: $name" }
                require(reference.keyType in mapKeyTypes) {
                    "非法 Protobuf map key 类型: ${reference.keyType.typeName}，字段: $name"
                }
                require(reference.valueType != ProtobufType.MAP) { "map 字段的 value 不能是 map: $name" }
                when (reference.valueType) {
                    ProtobufType.MESSAGE -> require(reference.valueReference is ProtobufTypeReference.MessageType) {
                        "map value 为 message 时必须提供 MessageType 引用: $name"
                    }

                    ProtobufType.ENUM -> require(reference.valueReference is ProtobufTypeReference.EnumType) {
                        "map value 为 enum 时必须提供 EnumType 引用: $name"
                    }

                    else -> require(reference.valueReference == null) {
                        "map value 为标量时不能提供类型引用: $name"
                    }
                }
            }
        }
    }

    private fun checkOneof() {
        val oneofName = oneofName ?: return
        require(oneofNamePattern.matches(oneofName)) { "非法 Protobuf oneof 名称: $oneofName" }
        require(!repeated) { "oneof 字段不能是 repeated: $name" }
        require(protobufType != ProtobufType.MAP) { "map 字段不能属于 oneof: $name" }
        require(!required) { "oneof 字段不能是必填字段: $name" }
    }

    /**
     * 转换为格式无关字段。
     */
    public fun toSchemaField(): SchemaField = SchemaField(name, schemaType, required, nullable, description)

    /**
     * 转换为 JSON 风格描述。
     */
    public fun toJsonDescription(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "number" to fieldNumber,
        "type" to protobufTypeName,
        "schemaType" to schemaType.name,
        "repeated" to repeated,
        "required" to required,
        "nullable" to nullable,
        "description" to description,
        "oneof" to oneofName,
        "messageType" to messageTypeName,
        "enumType" to enumTypeName,
        "mapKeyType" to mapType?.keyType?.typeName,
        "mapValueType" to mapType?.valueType?.typeName,
        "mapValueMessageType" to (mapType?.valueReference as? ProtobufTypeReference.MessageType)?.typeName,
        "mapValueEnumType" to (mapType?.valueReference as? ProtobufTypeReference.EnumType)?.typeName,
    )

    public companion object {

        /** 最小字段编号。 */
        public const val MIN_FIELD_NUMBER: Int = 1

        /** 最大字段编号（2^29 - 1）。 */
        public const val MAX_FIELD_NUMBER: Int = 536_870_911

        private val fieldNamePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private val oneofNamePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private val reservedFieldNumbers: IntRange = 19_000..19_999

        private val mapKeyTypes: Set<ProtobufType> = setOf(
            ProtobufType.INT32,
            ProtobufType.INT64,
            ProtobufType.UINT32,
            ProtobufType.UINT64,
            ProtobufType.SINT32,
            ProtobufType.SINT64,
            ProtobufType.FIXED32,
            ProtobufType.FIXED64,
            ProtobufType.SFIXED32,
            ProtobufType.SFIXED64,
            ProtobufType.BOOL,
            ProtobufType.STRING,
        )
    }
}
