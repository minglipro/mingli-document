package com.mingliqiye.document.format.yaml.exception

/**
 * YAML 文本解析失败。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
public class YamlParseException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : YamlException(message, throwable)
