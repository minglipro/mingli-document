package com.mingliqiye.document.openapi

/**
 * 字段类型覆盖的键：`schemaName.fieldName`。
 *
 * @property schemaName Schema 名称
 * @property fieldName 字段名
 */
internal data class OpenApiFieldKey(
    val schemaName: String,
    val fieldName: String,
)
