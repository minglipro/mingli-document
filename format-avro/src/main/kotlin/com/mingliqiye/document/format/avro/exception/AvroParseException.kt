package com.mingliqiye.document.format.avro.exception

/**
 * Avro 二进制解析失败。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public class AvroParseException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : AvroException(message, throwable, enableSuppression, writableStackTrace)
