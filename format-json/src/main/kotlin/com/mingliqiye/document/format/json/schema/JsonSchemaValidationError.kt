package com.mingliqiye.document.format.json.schema

/**
 * JSON Schema 校验错误。
 *
 * @property instancePath 实例的 JSON Pointer 路径
 * @property message 错误描述
 */
public data class JsonSchemaValidationError(
    public val instancePath: String,
    public val message: String,
) {

    override fun toString(): String = "$instancePath: $message"
}
