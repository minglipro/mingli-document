package com.mingliqiye.document.annotation

/**
 * 指定 Java 实体字段/属性/构造参数在文档中的名称。
 *
 * 标注在字段或 getter 上时决定序列化后写出的文档字段名；标注在
 * [DocumentCreator] 构造器的参数上时决定反序列化读取的文档字段名。
 *
 * @property value 文档字段名
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
public annotation class DocumentName(
    val value: String,
)
