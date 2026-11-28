package com.mingliqiye.document.registry.exception

/**
 * 待注册 Schema 与 subject 最新版本不兼容。
 *
 * 触发条件由 `SchemaCompatibilityPolicy` 决定；异常信息中包含全部不兼容项。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
public class SchemaCompatibilityException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : SchemaRegistryException(message, throwable)
