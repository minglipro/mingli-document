package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.schema.SchemaType

/**
 * Protobuf 类型与格式无关 [SchemaType] 的映射。
 *
 * 整数类型按位宽与符号映射：有符号 32 位映射为 [SchemaType.INT]，有符号 64 位映射为 [SchemaType.LONG]；
 * 无符号 32 位超出 [SchemaType.INT] 的范围，映射为 [SchemaType.LONG]；无符号 64 位超出
 * [SchemaType.LONG] 的范围，映射为可精确表达其全部取值的 [SchemaType.DECIMAL]。
 *
 * [ENUM]、[MESSAGE]、[GROUP] 与 [MAP] 是复合类型的占位：该 Schema 适配层不保存自定义 message/
 * enum/map 的完整类型名，只保留其格式无关语义。
 *
 * @property typeName `.proto` 中的类型名
 * @property schemaType 对应的格式无关类型
 */
public enum class ProtobufType(
    public val typeName: String,
    public val schemaType: SchemaType,
) {
    DOUBLE("double", SchemaType.DOUBLE),
    FLOAT("float", SchemaType.DOUBLE),
    INT32("int32", SchemaType.INT),
    INT64("int64", SchemaType.LONG),
    UINT32("uint32", SchemaType.LONG),
    UINT64("uint64", SchemaType.DECIMAL),
    SINT32("sint32", SchemaType.INT),
    SINT64("sint64", SchemaType.LONG),
    FIXED32("fixed32", SchemaType.LONG),
    FIXED64("fixed64", SchemaType.DECIMAL),
    SFIXED32("sfixed32", SchemaType.INT),
    SFIXED64("sfixed64", SchemaType.LONG),
    BOOL("bool", SchemaType.BOOLEAN),
    STRING("string", SchemaType.STRING),
    BYTES("bytes", SchemaType.BYTES),
    ENUM("enum", SchemaType.STRING),
    MESSAGE("message", SchemaType.OBJECT),

    /**
     * proto2 group 类型。
     *
     * group 在 wire 上使用 start-group/end-group 标签包围嵌套字段，语义与 message 相同；需要
     * [ProtobufSchemaField.typeReference] 提供 [ProtobufTypeReference.MessageType] 引用。
     */
    GROUP("group", SchemaType.OBJECT),
    MAP("map", SchemaType.MAP),
    ;

    public companion object {

        /**
         * 将格式无关类型映射为推荐的 Protobuf 类型。
         *
         * 该映射不是 [SchemaType] 与 Protobuf 类型之间的一一对应，仅用于从通用 Schema 反向构造
         * Protobuf Schema；例如 [SchemaType.DECIMAL] 推荐使用字符串表达。
         *
         * @param schemaType 格式无关类型
         * @return 推荐的 Protobuf 类型
         * @throws IllegalArgumentException 当 [SchemaType.ARRAY] 或 [SchemaType.NULL] 无法映射为单个 Protobuf 类型时
         */
        @JvmStatic
        public fun fromSchemaType(schemaType: SchemaType): ProtobufType = when (schemaType) {
            SchemaType.STRING -> STRING
            SchemaType.INT -> INT32
            SchemaType.LONG -> INT64
            SchemaType.DOUBLE -> DOUBLE
            SchemaType.DECIMAL -> STRING
            SchemaType.BOOLEAN -> BOOL
            SchemaType.BYTES -> BYTES
            SchemaType.OBJECT -> MESSAGE
            SchemaType.MAP -> MAP
            SchemaType.ARRAY ->
                throw IllegalArgumentException("SchemaType.ARRAY 无法映射为单个 Protobuf 类型，请使用 repeated 字段")
            SchemaType.NULL -> throw IllegalArgumentException("SchemaType.NULL 没有对应的 Protobuf 类型")
        }

        /**
         * 按 `.proto` 类型名查找 Protobuf 类型。
         *
         * @param typeName 类型名，如 `int32`、`string`
         * @return 类型；未识别的类型名返回 `null`
         */
        @JvmStatic
        public fun fromTypeName(typeName: String): ProtobufType? = values().firstOrNull { it.typeName == typeName }
    }
}
