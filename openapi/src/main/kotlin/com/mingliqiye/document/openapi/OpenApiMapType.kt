package com.mingliqiye.document.openapi

/**
 * Map 字段类型。
 *
 * @property values 值类型
 */
public data class OpenApiMapType(
    public val values: OpenApiFieldType,
) : OpenApiFieldType
