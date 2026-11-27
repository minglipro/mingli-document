package com.mingliqiye.document.schema

/**
 * [DocumentSchema] 构建器。
 */
public class DocumentSchemaBuilder(
    private val name: String,
) {

    private val fields = ArrayList<SchemaField>()

    /**
     * 添加字段。
     */
    public fun field(
        name: String,
        type: SchemaType,
        required: Boolean = true,
        nullable: Boolean = false,
        description: String? = null,
    ): DocumentSchemaBuilder {
        fields.add(SchemaField(name, type, required, nullable, description))
        return this
    }

    /**
     * 构建 Schema。
     */
    public fun build(): DocumentSchema = SimpleDocumentSchema(name, fields.toList())
}
