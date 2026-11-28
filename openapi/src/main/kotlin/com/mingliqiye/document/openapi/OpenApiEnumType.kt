package com.mingliqiye.document.openapi

import com.mingliqiye.document.openapi.exception.OpenApiConfigException

/**
 * 字符串枚举字段类型，生成 `{"type": "string", "enum": [...]}`。
 *
 * @property values 枚举取值，保持声明顺序
 */
public class OpenApiEnumType(
    values: List<String>,
) : OpenApiFieldType {

    /** 枚举取值，保持声明顺序。 */
    public val values: List<String> = values.toList()

    init {
        if (this.values.isEmpty()) {
            throw OpenApiConfigException("Enum values must not be empty")
        }
        val duplicates = this.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw OpenApiConfigException("Enum values must be unique, duplicated: $duplicates")
        }
    }
}
