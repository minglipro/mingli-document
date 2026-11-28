package com.mingliqiye.document.openapi.exception

import com.mingliqiye.document.exception.DocumentException

/**
 * OpenAPI 文档生成异常的公共基类。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public open class OpenApiException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : DocumentException(message, throwable, enableSuppression, writableStackTrace)
