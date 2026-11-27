package com.mingliqiye.document.exception

/**
 * 节点类型与预期不符。
 *
 * @param message 异常信息
 */
public class DocumentTypeException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : DocumentException(message, throwable)
