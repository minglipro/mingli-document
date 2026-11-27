package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node

/**
 * Java 实体与文档树之间的映射 API。
 *
 * 对应 `mingli-utils` 的 `JsonApi`，区别是：
 * - 这里不直接绑定 JSON，而是面向统一的 [Node] 树；
 * - 文本解析/格式化通过可选的 `DocumentCodec` 注入；
 * - 提供实体与实体、实体与树之间的转换。
 */
interface DocumentMapperApi {

    /**
     * 将 Java 对象序列化为文档树。
     *
     * @param value Java 对象
     * @return 文档树
     */
    fun toNode(value: Any?): Node

    /**
     * 将文档树反序列化为指定 Java 类型。
     *
     * @param node 文档树
     * @param targetType 目标类型
     * @return Java 对象
     */
    fun <T> fromNode(node: Node, targetType: Class<T>): T

    /**
     * 将文档树反序列化为指定泛型类型。
     *
     * @param node 文档树
     * @param targetType 目标类型引用
     * @return Java 对象
     */
    fun <T> fromNode(node: Node, targetType: DocumentTypeReference<T>): T

    /**
     * 将文本解析为 Java 实体。
     *
     * @param source 格式文本
     * @param targetType 目标类型
     * @return Java 实体
     */
    fun <T> parse(source: String, targetType: Class<T>): T

    /**
     * 将文本解析为指定泛型类型。
     *
     * @param source 格式文本
     * @param targetType 目标类型引用
     * @return Java 实体
     */
    fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T

    /**
     * 将 Java 实体序列化为文本。
     *
     * @param value Java 实体
     * @return 格式文本
     */
    fun format(value: Any?): String

    /**
     * 将 Java 实体序列化为格式化文本。
     *
     * @param value Java 实体
     * @return 格式化文本
     */
    fun formatPretty(value: Any?): String

    /**
     * 将任意对象转换为指定 Java 类型。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationClass 目标类型
     * @return 目标对象
     */
    fun <D> convert(source: Any?, destinationClass: Class<D>): D

    /**
     * 将任意对象转换为指定泛型类型。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationType 目标类型引用
     * @return 目标对象
     */
    fun <D> convert(source: Any?, destinationType: DocumentTypeReference<D>): D

    companion object {

        /**
         * 按 reified 泛型参数把文档树反序列化为 Java 实体。
         */
        inline fun <reified T> DocumentMapperApi.fromNode(node: Node): T = fromNode(node, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数把文本解析为 Java 实体。
         */
        inline fun <reified T> DocumentMapperApi.parse(source: String): T = parse(source, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数把任意对象转换为 Java 实体。
         */
        inline fun <reified T> DocumentMapperApi.convert(source: Any?): T = convert(source, DocumentTypeReference.of<T>())
    }
}
