package com.mingliqiye.document.openapi.exception

/**
 * OpenAPI 配置非法。
 *
 * 例如标题/版本为空、server url 为空、operation 路径或 HTTP 方法非法、
 * operation 重复、字段类型覆盖重复或枚举取值为空。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public class OpenApiConfigException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : OpenApiException(message, throwable, enableSuppression, writableStackTrace)
