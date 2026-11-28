package com.mingliqiye.document.jpa.exception

/**
 * 实体与文档树之间的映射错误。
 *
 * 用于运行时映射失败，例如节点类型不匹配、未知属性、非空属性读到 null、
 * 循环引用、无法实例化目标类型、属性值类型不受支持等。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 */
public class JpaMappingException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
) : JpaException(message, throwable)
