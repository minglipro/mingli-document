package com.mingliqiye.document.format.protobuf.exception

/**
 * Protobuf 二进制解析失败。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public class ProtobufParseException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : ProtobufException(message, throwable, enableSuppression, writableStackTrace)
