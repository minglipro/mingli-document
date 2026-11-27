package com.mingliqiye.document.annotation

import kotlin.reflect.KClass

/**
 * 指定 Java 实体字段/属性使用的文档转换器。
 *
 * 转换器类型由 serialization 模块按契约解析。
 *
 * @property value 转换器实现类
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
)
annotation class DocumentConverter(
    val value: KClass<*>,
)
