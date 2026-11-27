package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

/**
 * Protobuf Schema 字段定义。
 *
 * 在格式无关 [SchemaField] 的基础上增加 Protobuf 字段编号、`.proto` 类型与 repeated 标记。
 *
 * @property name 字段名
 * @property fieldNumber 字段编号
 * @property protobufType Protobuf 类型
 * @property repeated 是否为 repeated 字段
 * @property required 是否必填
 * @property nullable 是否允许 `null`
 * @property description 字段描述
 */
public class ProtobufSchemaField(
    public val name: String,
    public val fieldNumber: Int,
    public val protobufType: ProtobufType,
    public val repeated: Boolean = false,
    public val required: Boolean = true,
    public val nullable: Boolean = false,
    public val description: String? = null,
) {

    /** Protobuf 类型名，如 `int32`、`string`。 */
    public val protobufTypeName: String get() = protobufType.typeName

    /** 元素类型；非 repeated 字段即字段类型。 */
    public val elementType: SchemaType get() = protobufType.schemaType

    /** 映射到格式无关 Schema 的类型，repeated 字段为 [SchemaType.ARRAY]。 */
    public val schemaType: SchemaType get() = if (repeated) SchemaType.ARRAY else protobufType.schemaType

    init {
        require(fieldNamePattern.matches(name)) { "非法 Protobuf 字段名: $name" }
        require(fieldNumber in MIN_FIELD_NUMBER..MAX_FIELD_NUMBER) {
            "非法 Protobuf 字段编号: $fieldNumber，必须在 $MIN_FIELD_NUMBER..$MAX_FIELD_NUMBER 范围内"
        }
        require(fieldNumber !in reservedFieldNumbers) {
            "Protobuf 保留字段编号: $fieldNumber，不能使用 $reservedFieldNumbers 内的编号"
        }
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
    )

    public companion object {

        /** 最小字段编号。 */
        public const val MIN_FIELD_NUMBER: Int = 1

        /** 最大字段编号（2^29 - 1）。 */
        public const val MAX_FIELD_NUMBER: Int = 536_870_911

        private val fieldNamePattern: Regex = Regex("[A-Za-z_][A-Za-z0-9_]*")

        private val reservedFieldNumbers: IntRange = 19_000..19_999
    }
}
