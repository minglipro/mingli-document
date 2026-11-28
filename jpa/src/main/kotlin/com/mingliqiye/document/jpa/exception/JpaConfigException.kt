package com.mingliqiye.document.jpa.exception

/**
 * JPA 映射配置/元数据错误。
 *
 * 用于实体元数据无法建立的情况，例如字段与列名冲突、不支持的关联类型、
 * 集合关联缺少元素类型等；这些错误与具体文档内容无关。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
public class JpaConfigException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : JpaException(message, throwable)
