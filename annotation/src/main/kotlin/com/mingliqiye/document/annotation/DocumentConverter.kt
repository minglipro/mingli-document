package com.mingliqiye.document.annotation

import kotlin.reflect.KClass

/**
 * 指定 Java 实体字段/属性使用的文档转换器。
 *
 * 字段级转换器优先于 `DocumentMapperBuilder` 注册的全局转换器，最后才回退到内置 Bean 映射。
 * 转换器类型由 serialization 模块按契约解析，需实现序列化器、反序列化器或双向转换器，
 * 并提供无参构造器或声明为 Kotlin `object`。
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
