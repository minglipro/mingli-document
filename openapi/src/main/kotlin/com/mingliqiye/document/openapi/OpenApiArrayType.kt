package com.mingliqiye.document.openapi

/**
 * 数组字段类型。
 *
 * @property items 元素类型
 */
public data class OpenApiArrayType(
    public val items: OpenApiFieldType,
) : OpenApiFieldType
