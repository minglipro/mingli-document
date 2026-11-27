package com.mingliqiye.document.core

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * 文档目标类型引用接口，用于在泛型被擦除后保留完整的 [Type] 信息。
 *
 * 使用工厂方法创建：
 * ```kotlin
 * val type = DocumentTypeReference.of<List<String>>()
 * ```
 *
 * @param T 目标类型
 */
interface DocumentTypeReference<T> {

    /** 当前引用的类型信息。 */
    val type: Type

    /**
     * 获取去掉泛型参数后的原始类型。
     *
     * @return 原始 Class
     */
    @Suppress("UNCHECKED_CAST")
    fun getRawType(): Class<T> {
        val currentType = type
        val rawType = if (currentType is ParameterizedType) currentType.rawType else currentType
        require(rawType is Class<*>) { "Cannot resolve raw type from $currentType" }
        return rawType as Class<T>
    }

    companion object {

        /**
         * 根据 reified 泛型参数创建类型引用。
         */
        inline fun <reified T> of(): DocumentTypeReference<T> = DefaultDocumentTypeReference(object : TypeCapture<T>() {}.type)

        /**
         * 根据 [Class] 创建类型引用。
         *
         * @param clazz 目标类
         */
        @JvmStatic
        fun <T> of(clazz: Class<T>): DocumentTypeReference<T> = DefaultDocumentTypeReference(clazz)

        /**
         * 根据 [Type] 创建类型引用。
         *
         * @param type 目标类型
         */
        @JvmStatic
        fun <T> of(type: Type): DocumentTypeReference<T> = DefaultDocumentTypeReference(type)

        /**
         * 创建 List 类型引用。
         *
         * @param elementType 元素类型
         */
        @JvmStatic
        fun <T> listType(elementType: Class<T>): DocumentTypeReference<List<T>> = DefaultDocumentTypeReference(SimpleParameterizedType(List::class.java, arrayOf(elementType)))

        /**
         * 创建 Map 类型引用。
         *
         * @param keyType 键类型
         * @param valueType 值类型
         */
        @JvmStatic
        fun <K, V> mapType(
            keyType: Class<K>,
            valueType: Class<V>,
        ): DocumentTypeReference<Map<K, V>> = DefaultDocumentTypeReference(
            SimpleParameterizedType(Map::class.java, arrayOf(keyType, valueType)),
        )
    }
}

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

/**
 * 简单参数化类型实现。
 */
internal class SimpleParameterizedType(
    private val raw: Type,
    private val arguments: Array<Type>,
    private val owner: Type? = null,
) : ParameterizedType {

    override fun getActualTypeArguments(): Array<Type> = arguments.clone()

    override fun getRawType(): Type = raw

    override fun getOwnerType(): Type? = owner

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ParameterizedType) {
            return false
        }
        return raw == other.rawType &&
            owner == other.ownerType &&
            arguments.contentEquals(other.actualTypeArguments)
    }

    override fun hashCode(): Int {
        var result = raw.hashCode()
        result = 31 * result + arguments.contentHashCode()
        result = 31 * result + (owner?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = if (owner == null) {
        "$raw<${arguments.joinToString(",")}>"
    } else {
        "$owner.$raw<${arguments.joinToString(",")}>"
    }
}
