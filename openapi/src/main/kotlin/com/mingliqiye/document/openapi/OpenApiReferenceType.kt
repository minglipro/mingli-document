package com.mingliqiye.document.openapi

import com.mingliqiye.document.openapi.exception.OpenApiConfigException

/**
 * 引用 `components.schemas` 中另一个 Schema 的字段类型。
 *
 * @property schemaName 目标 Schema 名称
 */
public data class OpenApiReferenceType(
    public val schemaName: String,
) : OpenApiFieldType {

    init {
        if (schemaName.isBlank()) {
            throw OpenApiConfigException("Referenced schema name must not be blank")
        }
    }
}
