package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Java 类型与文档树之间的自定义转换器契约。
 *
 * @param T 转换器负责的类型
 */
interface DocumentConverter<T> {

    /**
     * 将对象转换为文档树。
     *
     * @param value 非 null 对象
     * @return 文档树
     */
    fun toNode(value: T): Node

    /**
     * 将文档树还原为对象。
     *
     * @param node 文档树
     * @return 对象；无法还原时返回 `null`
     */
    fun fromNode(node: Node): T?

    /**
     * 转换器负责的类型。
     */
    fun getType(): DocumentTypeReference<T>
}

/**
 * 转换器注册表契约。
 */
interface DocumentConverterRegistryApi {

    /**
     * 注册一个转换器。
     *
     * @param converter 转换器
     * @return 当前注册表，便于链式调用
     */
    fun register(converter: DocumentConverter<*>): DocumentConverterRegistryApi

    /**
     * 按目标类型查找转换器。
     *
     * @param type 目标类型
     * @return 匹配的转换器；不存在时返回 `null`
     */
    fun find(type: Class<*>): DocumentConverter<*>?

    companion object {

        /**
         * 创建空注册表。
         */
        fun empty(): DocumentConverterRegistryApi = DefaultDocumentConverterRegistry()
    }
}

/**
 * 转换器注册表默认实现。
 */
internal class DefaultDocumentConverterRegistry(
    converters: List<DocumentConverter<*>> = emptyList(),
) : DocumentConverterRegistryApi {

    private val converters = CopyOnWriteArrayList(converters)

    override fun register(converter: DocumentConverter<*>): DocumentConverterRegistryApi {
        converters.add(converter)
        return this
    }

    override fun find(type: Class<*>): DocumentConverter<*>? = converters.firstOrNull { converter ->
        converter.getType().getRawType().isAssignableFrom(type)
    }
}
