package com.mingliqiye.document.registry.exception

import com.mingliqiye.document.exception.DocumentException

/**
 * Schema Registry 异常的公共基类。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public open class SchemaRegistryException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : DocumentException(message, throwable, enableSuppression, writableStackTrace)
