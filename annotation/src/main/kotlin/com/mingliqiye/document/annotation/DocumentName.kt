package com.mingliqiye.document.annotation

/**
 * 指定 Java 实体字段/属性在文档中的名称。
 *
 * @property value 文档字段名
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
)
annotation class DocumentName(
    val value: String,
)
