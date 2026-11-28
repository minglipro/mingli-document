package com.mingliqiye.document.registry.exception

/**
 * 指定的 subject、版本或 id 在 registry 中不存在。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
public class SchemaNotFoundException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : SchemaRegistryException(message, throwable)
