package com.mingliqiye.document.core

import java.lang.reflect.Type

/**
 * 类型引用默认实现。
 */
@PublishedApi
internal class DefaultDocumentTypeReference<T>(
    override val type: Type,
) : DocumentTypeReference<T> {

    override fun equals(other: Any?): Boolean = other is DocumentTypeReference<*> && type == other.type

    override fun hashCode(): Int = type.hashCode()

    override fun toString(): String = "DocumentTypeReference($type)"
}
