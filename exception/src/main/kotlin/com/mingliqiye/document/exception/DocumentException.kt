package com.mingliqiye.document.exception

import com.mingliqiye.exception.MingLiBaseException

/**
 * 文档处理异常的公共基类。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public open class DocumentException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : MingLiBaseException(message, throwable, enableSuppression, writableStackTrace)
