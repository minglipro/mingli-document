package com.mingliqiye.document.registry.exception

/**
 * `DocumentSchema` 与 JSON Schema 节点之间转换失败。
 *
 * 例如：Schema 名为空白、字段名为空白/重复、节点结构非法、字段类型无法映射。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
public class SchemaSerializationException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : SchemaRegistryException(message, throwable)
