package com.mingliqiye.document.annotation

import kotlin.reflect.KClass

/**
 * 指定 Java 实体字段/属性使用的文档转换器。
 *
 * 字段级转换器优先于 `DocumentMapperBuilder` 注册的全局转换器，最后才回退到内置 Bean 映射。
 * 转换器可实现序列化器、反序列化器或双向转换器；缺少的方向会继续回退到全局转换器或内置映射。
 * 转换器需提供无参构造器或声明为 Kotlin `object`，实例在 Mapper 内缓存，必须保持无状态。
 *
 * Kotlin 属性可以不写 use-site target；注解默认落在字段上。需要标在 getter/setter 时使用
 * `@get:` / `@set:`。
 *
 * @property value 转换器实现类
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FUNCTION,
)
annotation class DocumentConverter(
    val value: KClass<*>,
)
