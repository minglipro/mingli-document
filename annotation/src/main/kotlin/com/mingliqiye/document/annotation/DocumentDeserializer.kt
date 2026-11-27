package com.mingliqiye.document.annotation

import kotlin.reflect.KClass

/**
 * 指定字段/属性使用的单向反序列化器。
 *
 * 优先级高于 [DocumentConverter] 与 Mapper 全局转换器。
 * 实现类需实现 `serialization` 模块的 `DocumentBaseDeserializer`。
 *
 * @property value 反序列化器实现类
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FUNCTION,
)
public annotation class DocumentDeserializer(
    val value: KClass<out DocumentBaseDeserializer<*>>,
)
