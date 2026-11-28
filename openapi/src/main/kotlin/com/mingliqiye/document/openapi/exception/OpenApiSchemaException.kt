package com.mingliqiye.document.openapi.exception

/**
 * OpenAPI Schema 非法或不支持。
 *
 * 例如 Schema 名为空/不符合 OpenAPI component 命名规则、字段名为空/重复、
 * 字段类型 [com.mingliqiye.document.schema.SchemaType.NULL] 无法映射、
 * `$ref` 指向未注册的 Schema、字段类型覆盖指向未知 Schema/字段。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public class OpenApiSchemaException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : OpenApiException(message, throwable, enableSuppression, writableStackTrace)
