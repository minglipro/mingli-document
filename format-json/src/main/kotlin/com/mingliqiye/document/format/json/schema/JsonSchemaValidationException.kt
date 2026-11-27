package com.mingliqiye.document.format.json.schema

import com.mingliqiye.document.exception.DocumentException

/**
 * JSON Schema 校验失败异常。
 *
 * @property errors 所有校验错误
 */
public class JsonSchemaValidationException(
    public val errors: List<JsonSchemaValidationError>,
) : DocumentException(
    errors.joinToString(separator = "\n") { it.toString() },
)
