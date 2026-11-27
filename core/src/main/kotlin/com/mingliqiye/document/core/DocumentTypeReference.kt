package com.mingliqiye.document.core

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 运行时类型引用，用于在泛型类型被擦除后保留完整的 [Type] 信息。
 *
 * 使用匿名子类创建：
 * ```kotlin
 * val type = object : DocumentTypeReference<List<String>>() {}
 * ```
 *
 * @param T 引用的目标类型
 */
abstract class DocumentTypeReference<T> {

    /**
     * 当前引用的类型信息；子类可以覆写。
     */
    open var type: Type = Any::class.java

    /**
     * 通过匿名子类的泛型父类获取类型。
     */
    protected constructor() {
        val superType = this.javaClass.genericSuperclass
        require(superType is ParameterizedType) {
            "DocumentTypeReference must be created as an anonymous subclass"
        }
        type = superType.actualTypeArguments[0]
    }

    /**
     * 直接指定类型。
     *
     * @param type 类型信息
     */
    protected constructor(type: Type) {
        this.type = type
    }

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentTypeReference<*>) return false
        return type == other.type
    }

    override fun hashCode(): Int = type.hashCode()

    override fun toString(): String = "DocumentTypeReference($type)"

    companion object {

        /**
         * 根据 reified 泛型参数创建类型引用。
         */
        inline fun <reified T> of(): DocumentTypeReference<T> = object : DocumentTypeReference<T>() {}

        /**
         * 根据 [Class] 创建类型引用。
         *
         * @param clazz 目标类
         */
        @JvmStatic
        fun <T> of(clazz: Class<T>): DocumentTypeReference<T> = object : DocumentTypeReference<T>(clazz) {}

        /**
         * 根据 [Type] 创建类型引用。
         *
         * @param type 目标类型
         */
        @JvmStatic
        fun <T> of(type: Type): DocumentTypeReference<T> = object : DocumentTypeReference<T>(type) {}

        /**
         * 创建 List 类型引用。
         *
         * @param elementType 元素类型
         */
        @JvmStatic
        fun <T> listType(elementType: Class<T>): DocumentTypeReference<List<T>> = object : DocumentTypeReference<List<T>>(
            SimpleParameterizedType(List::class.java, arrayOf(elementType)),
        ) {}

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
        ): DocumentTypeReference<Map<K, V>> = object : DocumentTypeReference<Map<K, V>>(
            SimpleParameterizedType(Map::class.java, arrayOf(keyType, valueType)),
        ) {}
    }

    /**
     * 简单的参数化类型实现，用于工厂方法。
     */
    private class SimpleParameterizedType(
        private val raw: Type,
        private val arguments: Array<Type>,
    ) : ParameterizedType {

        override fun getActualTypeArguments(): Array<Type> = arguments

        override fun getRawType(): Type = raw

        override fun getOwnerType(): Type? = null

        override fun toString(): String = "$raw<${arguments.joinToString(",")}>"
    }
}
