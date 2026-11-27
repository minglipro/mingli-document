package com.mingliqiye.document.serialization.exception

import com.mingliqiye.document.exception.DocumentException

/**
 * Java 实体与文档树之间的序列化/反序列化异常。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
public class DocumentSerializationException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : DocumentException(message, throwable)
