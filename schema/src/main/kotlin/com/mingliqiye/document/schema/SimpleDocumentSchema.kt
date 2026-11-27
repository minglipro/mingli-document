package com.mingliqiye.document.schema

/**
 * [DocumentSchema] 简单实现。
 */
internal class SimpleDocumentSchema(
    override val name: String,
    override val fields: List<SchemaField>,
) : DocumentSchema {

    private val byName: Map<String, SchemaField> = fields.associateBy { it.name }

    override fun field(name: String): SchemaField? = byName[name]
}
