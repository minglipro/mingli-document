package com.mingliqiye.document.core

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

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
public interface DocumentTypeReference<T> {

    /** 当前引用的类型信息。 */
    public val type: Type

    /**
     * 获取去掉泛型参数后的原始类型。
     *
     * @return 原始 Class
     */
    @Suppress("UNCHECKED_CAST")
    public fun getRawType(): Class<T> {
        val currentType = type
        val rawType = if (currentType is ParameterizedType) currentType.rawType else currentType
        require(rawType is Class<*>) { "Cannot resolve raw type from $currentType" }
        return rawType as Class<T>
    }

    public companion object {

        /**
         * 根据 reified 泛型参数创建类型引用。
         */
        public inline fun <reified T> of(): DocumentTypeReference<T> = DefaultDocumentTypeReference(object : TypeCapture<T>() {}.type)

        /**
         * 根据 [Class] 创建类型引用。
         *
         * @param clazz 目标类
         */
        @JvmStatic
        public fun <T> of(clazz: Class<T>): DocumentTypeReference<T> = DefaultDocumentTypeReference(clazz)

        /**
         * 根据 [Type] 创建类型引用。
         *
         * @param type 目标类型
         */
        @JvmStatic
        public fun <T> of(type: Type): DocumentTypeReference<T> = DefaultDocumentTypeReference(type)

        /**
         * 创建 List 类型引用。
         *
         * @param elementType 元素类型
         */
        @JvmStatic
        public fun <T> listType(elementType: Class<T>): DocumentTypeReference<List<T>> = DefaultDocumentTypeReference(SimpleParameterizedType(List::class.java, arrayOf(elementType)))

        /**
         * 创建 Map 类型引用。
         *
         * @param keyType 键类型
         * @param valueType 值类型
         */
        @JvmStatic
        public fun <K, V> mapType(
            keyType: Class<K>,
            valueType: Class<V>,
        ): DocumentTypeReference<Map<K, V>> = DefaultDocumentTypeReference(
            SimpleParameterizedType(Map::class.java, arrayOf(keyType, valueType)),
        )
    }
}
