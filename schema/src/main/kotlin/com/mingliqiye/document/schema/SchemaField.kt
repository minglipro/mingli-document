package com.mingliqiye.document.schema

/**
 * 文档 Schema 字段定义。
 *
 * @property name 字段名
 * @property type 字段类型
 * @property required 是否必填
 * @property nullable 是否允许 null
 * @property description 字段描述
 */
public class SchemaField(
    public val name: String,
    public val type: SchemaType,
    public val required: Boolean = true,
    public val nullable: Boolean = false,
    public val description: String? = null,
) {

    public companion object {

        /**
         * 创建字段定义。
         */
        @JvmStatic
        public fun of(
            name: String,
            type: SchemaType,
            required: Boolean = true,
            nullable: Boolean = false,
        ): SchemaField = SchemaField(name, type, required, nullable)
    }
}
