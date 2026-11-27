package com.mingliqiye.document.core

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * 泛型捕获辅助类，供 reified 工厂使用。
 */
@PublishedApi
internal abstract class TypeCapture<T> {

    /** 捕获到的完整类型。 */
    val type: Type = normalizeType(
        (javaClass.genericSuperclass as? ParameterizedType)
            ?.actualTypeArguments
            ?.firstOrNull()
            ?: throw IllegalArgumentException("Cannot capture generic type"),
    )
}

/**
 * 将 Kotlin 捕获到的通配符类型归一化为具体类型。
 */
private fun normalizeType(type: Type): Type = when (type) {
    is WildcardType -> normalizeType(type.upperBounds.firstOrNull() ?: Any::class.java)
    is ParameterizedType -> normalizeParameterizedType(type)
    else -> type
}

private fun normalizeParameterizedType(type: ParameterizedType): Type = SimpleParameterizedType(
    type.rawType,
    type.actualTypeArguments.map(::normalizeType).toTypedArray(),
    type.ownerType?.let(::normalizeType),
)
