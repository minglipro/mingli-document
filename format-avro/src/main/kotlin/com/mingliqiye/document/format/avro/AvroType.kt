package com.mingliqiye.document.format.avro

import com.mingliqiye.document.schema.SchemaType

/**
 * Avro 字段类型名与 [SchemaType] 的映射。
 *
 * 这里只描述字段可使用的类型名，不负责嵌套结构（数组元素、子记录字段），
 * 也不依赖 Apache Avro 库。
 *
 * [SchemaType.DECIMAL] 没有独立的 Avro 类型名，按 Avro 规范基于 `bytes`
 * 表达（decimal 逻辑类型），因此映射为 [BYTES]。
 *
 * @property typeName Avro 类型名
 * @property schemaType 对应的通用 Schema 类型
 */
public enum class AvroType(
    public val typeName: String,
    public val schemaType: SchemaType,
) {
    /** UTF-8 字符串。 */
    STRING("string", SchemaType.STRING),

    /** 32 位有符号整数。 */
    INT("int", SchemaType.INT),

    /** 64 位有符号整数。 */
    LONG("long", SchemaType.LONG),

    /** 64 位双精度浮点数。 */
    DOUBLE("double", SchemaType.DOUBLE),

    /** 布尔值。 */
    BOOLEAN("boolean", SchemaType.BOOLEAN),

    /** 字节序列。 */
    BYTES("bytes", SchemaType.BYTES),

    /** 数组。 */
    ARRAY("array", SchemaType.ARRAY),

    /** 记录对象。 */
    OBJECT("object", SchemaType.OBJECT),

    /** 字符串键映射。 */
    MAP("map", SchemaType.MAP),

    /** null 值。 */
    NULL("null", SchemaType.NULL),
    ;

    public companion object {

        /**
         * 按 Avro 类型名查找类型。
         *
         * @param typeName Avro 类型名，允许首尾空白
         * @return 类型；未知类型名返回 `null`
         */
        @JvmStatic
        public fun fromTypeName(typeName: String): AvroType? {
            val normalized = typeName.trim()
            return entries.firstOrNull { it.typeName == normalized }
        }

        /**
         * 将通用 Schema 类型映射为 Avro 类型。
         *
         * @param type 通用 Schema 类型
         * @return Avro 类型
         */
        @JvmStatic
        public fun fromSchemaType(type: SchemaType): AvroType = when (type) {
            SchemaType.STRING -> STRING
            SchemaType.INT -> INT
            SchemaType.LONG -> LONG
            SchemaType.DOUBLE -> DOUBLE
            SchemaType.DECIMAL -> BYTES
            SchemaType.BOOLEAN -> BOOLEAN
            SchemaType.BYTES -> BYTES
            SchemaType.ARRAY -> ARRAY
            SchemaType.OBJECT -> OBJECT
            SchemaType.MAP -> MAP
            SchemaType.NULL -> NULL
        }
    }
}
