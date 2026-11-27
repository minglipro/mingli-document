package com.mingliqiye.document.format.smile.exception

/**
 * Smile 二进制内容解析失败。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public class SmileParseException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : SmileException(message, throwable, enableSuppression, writableStackTrace)
