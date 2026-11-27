package com.mingliqiye.document.serialization

/**
 * 字段定位键：声明类 + 属性名。
 */
public data class FieldKey(
    val declaringClass: Class<*>,
    val name: String,
)
