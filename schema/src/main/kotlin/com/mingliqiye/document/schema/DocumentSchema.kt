package com.mingliqiye.document.schema

/**
 * 格式无关的文档 Schema。
 */
public interface DocumentSchema {

    /** Schema 名称。 */
    public val name: String

    /** 字段列表，保持声明顺序。 */
    public val fields: List<SchemaField>

    /**
     * 按字段名查找字段。
     *
     * @param name 字段名
     * @return 字段；不存在时返回 `null`
     */
    public fun field(name: String): SchemaField?

    public companion object {

        /**
         * 创建简单 Schema。
         *
         * @param name Schema 名称
         * @param fields 字段列表
         * @return Schema
         */
        @JvmStatic
        public fun of(name: String, vararg fields: SchemaField): DocumentSchema = SimpleDocumentSchema(name, fields.toList())
    }
}
