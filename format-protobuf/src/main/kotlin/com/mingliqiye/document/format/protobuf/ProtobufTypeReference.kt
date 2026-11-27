package com.mingliqiye.document.format.protobuf

/**
 * Protobuf 复杂字段的类型引用。
 *
 * 用于 [ProtobufSchemaField] 描述 message、enum 与 map 字段的真实类型：
 * - [MessageType]、[EnumType] 的 `typeName` 默认是相对当前 message 的类型名；以 `.` 开头表示
 *   全限定名，需要给 [ProtobufSchema.toDescriptor] 传入依赖 Descriptor 才能解析。
 * - [MapType] 的 key 必须是允许作为 map key 的标量类型，value 可以是标量，也可以是
 *   [MessageType]/[EnumType] 引用。
 *
 * @see ProtobufSchemaField.typeReference
 */
public sealed interface ProtobufTypeReference {

    /** message 类型引用。 */
    public data class MessageType(public val typeName: String) : ProtobufTypeReference {
        init {
            require(typeName.isNotBlank()) { "Protobuf message 类型引用不能为空" }
        }
    }

    /** enum 类型引用。 */
    public data class EnumType(public val typeName: String) : ProtobufTypeReference {
        init {
            require(typeName.isNotBlank()) { "Protobuf enum 类型引用不能为空" }
        }
    }

    /** map 字段类型。 */
    public data class MapType(
        public val keyType: ProtobufType,
        public val valueType: ProtobufType,
        public val valueReference: ProtobufTypeReference? = null,
    ) : ProtobufTypeReference

    public companion object {

        /**
         * 创建 message 类型引用。
         *
         * @param typeName 相对当前 message 的类型名，或以 `.` 开头的全限定名
         */
        @JvmStatic
        public fun messageType(typeName: String): MessageType = MessageType(typeName)

        /**
         * 创建 enum 类型引用。
         *
         * @param typeName 相对当前 message 的类型名，或以 `.` 开头的全限定名
         */
        @JvmStatic
        public fun enumType(typeName: String): EnumType = EnumType(typeName)

        /**
         * 创建 map 类型引用。
         *
         * @param keyType map key 类型，必须是标量
         * @param valueType map value 类型
         * @param valueReference value 为 message/enum 时的类型引用
         */
        @JvmStatic
        public fun mapType(
            keyType: ProtobufType,
            valueType: ProtobufType,
            valueReference: ProtobufTypeReference? = null,
        ): MapType = MapType(keyType, valueType, valueReference)
    }
}
