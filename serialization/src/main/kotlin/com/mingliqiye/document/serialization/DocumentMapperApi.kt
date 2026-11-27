package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.nio.file.Path

/**
 * Java 实体与文档树之间的映射 API。
 *
 * 对应 Jackson 的 `ObjectMapper` 抽象，区别是：
 * - 这里不直接绑定 JSON，而是面向统一的 [Node] 树；
 * - 文本解析/格式化由 `JsonMapper`、`YamlMapper` 等格式模块子类注入；
 * - 提供实体与实体、实体与树之间的转换。
 */
public interface DocumentMapperApi {

    /**
     * 将 Java 对象序列化为文档树。
     *
     * @param value Java 对象
     * @return 文档树
     */
    public fun toNode(value: Any?): Node

    /**
     * 将文档树反序列化为指定 Java 类型。
     *
     * @param node 文档树
     * @param targetType 目标类型
     * @return Java 对象
     */
    public fun <T> fromNode(node: Node, targetType: Class<T>): T

    /**
     * 将文档树反序列化为指定泛型类型。
     *
     * @param node 文档树
     * @param targetType 目标类型引用
     * @return Java 对象
     */
    public fun <T> fromNode(node: Node, targetType: DocumentTypeReference<T>): T

    /**
     * 将文本解析为 Java 实体。
     *
     * @param source 格式文本
     * @param targetType 目标类型
     * @return Java 实体
     */
    public fun <T> parse(source: String, targetType: Class<T>): T

    /**
     * 将文本解析为指定泛型类型。
     *
     * @param source 格式文本
     * @param targetType 目标类型引用
     * @return Java 实体
     */
    public fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T

    /**
     * 将 Java 实体序列化为文本。
     *
     * @param value Java 实体
     * @return 格式文本
     */
    public fun format(value: Any?): String

    /**
     * 将字节数组解析为 Java 实体。
     *
     * 只有二进制格式的 Mapper 支持；文本格式默认抛出异常。
     *
     * @param source 二进制内容
     * @param targetType 目标类型
     * @return Java 实体
     */
    public fun <T> parse(source: ByteArray, targetType: Class<T>): T = throw DocumentSerializationException("${this.javaClass.name} does not support binary parsing")

    /**
     * 将字节数组解析为指定泛型类型。
     *
     * @param source 二进制内容
     * @param targetType 目标类型引用
     * @return Java 实体
     */
    public fun <T> parse(source: ByteArray, targetType: DocumentTypeReference<T>): T = throw DocumentSerializationException("${this.javaClass.name} does not support binary parsing")

    /**
     * 将 Java 实体序列化为字节数组。
     *
     * 只有二进制格式的 Mapper 支持；文本格式默认抛出异常。
     *
     * @param value Java 实体
     * @return 二进制内容
     */
    public fun formatBytes(value: Any?): ByteArray = throw DocumentSerializationException("${this.javaClass.name} does not support binary writing")

    /**
     * 将 Java 实体序列化为格式化文本。
     *
     * @param value Java 实体
     * @return 格式化文本
     */
    public fun formatPretty(value: Any?): String

    /**
     * 从文件解析 Java 实体。
     *
     * 文本格式按 UTF-8 读取，二进制格式按字节读取。
     */
    public fun <T> parse(file: File, targetType: Class<T>): T

    /**
     * 从文件解析指定泛型类型。
     */
    public fun <T> parse(file: File, targetType: DocumentTypeReference<T>): T

    /**
     * 从路径解析 Java 实体。
     */
    public fun <T> parse(path: Path, targetType: Class<T>): T

    /**
     * 从路径解析指定泛型类型。
     */
    public fun <T> parse(path: Path, targetType: DocumentTypeReference<T>): T

    /**
     * 从输入流解析 Java 实体。
     *
     * 文本格式按 UTF-8 解码；调用方负责关闭流。
     */
    public fun <T> parse(input: InputStream, targetType: Class<T>): T

    /**
     * 从输入流解析指定泛型类型。
     */
    public fun <T> parse(input: InputStream, targetType: DocumentTypeReference<T>): T

    /**
     * 从字符流解析 Java 实体。
     *
     * 只支持文本格式；调用方负责关闭 Reader。
     */
    public fun <T> parse(reader: Reader, targetType: Class<T>): T

    /**
     * 从字符流解析指定泛型类型。
     */
    public fun <T> parse(reader: Reader, targetType: DocumentTypeReference<T>): T

    /**
     * 将 Java 实体写入文件。
     */
    public fun format(value: Any?, file: File)

    /**
     * 将 Java 实体写入路径。
     */
    public fun format(value: Any?, path: Path)

    /**
     * 将 Java 实体写入输出流。
     *
     * 调用方负责关闭流；本方法只 flush。
     */
    public fun format(value: Any?, output: OutputStream)

    /**
     * 将 Java 实体写入字符流。
     *
     * 只支持文本格式；调用方负责关闭 Writer。
     */
    public fun format(value: Any?, writer: Writer)

    /**
     * 将任意对象转换为指定 Java 类型。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationClass 目标类型
     * @return 目标对象
     */
    public fun <D> convert(source: Any?, destinationClass: Class<D>): D

    /**
     * 将任意对象转换为指定泛型类型。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationType 目标类型引用
     * @return 目标对象
     */
    public fun <D> convert(source: Any?, destinationType: DocumentTypeReference<D>): D

    public companion object {

        /**
         * 按 reified 泛型参数从文件解析 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.parse(file: File): T = parse(file, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数从路径解析 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.parse(path: Path): T = parse(path, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数从输入流解析 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.parse(input: InputStream): T = parse(input, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数从字符流解析 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.parse(reader: Reader): T = parse(reader, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数把文档树反序列化为 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.fromNode(node: Node): T = fromNode(node, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数把文本解析为 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.parse(source: String): T = parse(source, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数把字节数组解析为 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.parse(source: ByteArray): T = parse(source, DocumentTypeReference.of<T>())

        /**
         * 按 reified 泛型参数把任意对象转换为 Java 实体。
         */
        public inline fun <reified T> DocumentMapperApi.convert(source: Any?): T = convert(source, DocumentTypeReference.of<T>())
    }
}
