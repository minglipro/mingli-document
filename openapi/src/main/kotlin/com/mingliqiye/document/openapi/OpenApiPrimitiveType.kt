package com.mingliqiye.document.openapi

import com.mingliqiye.document.schema.SchemaType

/**
 * 基本类型字段，映射规则与字段声明类型相同。
 *
 * @property type Schema 类型
 */
public data class OpenApiPrimitiveType(
    public val type: SchemaType,
) : OpenApiFieldType
