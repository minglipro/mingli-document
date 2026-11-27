package com.mingliqiye.document.annotation

/**
 * 标记 Java 实体字段/属性在序列化时忽略。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
)
public annotation class DocumentIgnore
