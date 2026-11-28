package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
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
     * 将 Java 实体以格式化形式写入文件。
     */
    public fun formatPretty(value: Any?, file: File) {
        file.writeBytes(formatPretty(value).toByteArray(Charsets.UTF_8))
    }

    /**
     * 将 Java 实体以格式化形式写入路径。
     */
    public fun formatPretty(value: Any?, path: Path) {
        java.nio.file.Files.write(path, formatPretty(value).toByteArray(Charsets.UTF_8))
    }

    /**
     * 将 Java 实体以格式化形式写入输出流。
     *
     * 调用方负责关闭流；本方法只 flush。
     */
    public fun formatPretty(value: Any?, output: OutputStream) {
        output.write(formatPretty(value).toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /**
     * 将 Java 实体以格式化形式写入字符流。
     *
     * 只支持文本格式；调用方负责关闭 Writer。
     */
    public fun formatPretty(value: Any?, writer: Writer) {
        writer.write(formatPretty(value))
        writer.flush()
    }

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

    /**
     * 将文档树合并到已有对象，文档中没有出现的字段保持目标对象的原值。
     *
     * 等价于 [merge] 使用 [MergePolicy.REPLACE]。
     *
     * @param node 合并来源文档树，必须是对象节点
     * @param target 已存在的目标对象
     * @return 合并结果；可变 Bean 就地修改并返回同一实例，
     *   不可变 Bean（record、构造器注入、Builder）返回新实例
     */
    public fun <T> merge(node: Node, target: T): T = merge(node, target, MergePolicy.REPLACE)

    /**
     * 按指定策略将文档树合并到已有对象。
     *
     * 文档中出现的字段覆盖目标对象，未出现的字段保留原值；嵌套 Bean 是否
     * 递归合并、文档中的 `null` 是否覆盖由 [policy] 决定。
     *
     * 默认实现不支持合并；[DocumentMapper] 提供完整实现。
     *
     * @param node 合并来源文档树，必须是对象节点
     * @param target 已存在的目标对象
     * @param policy 合并策略
     * @return 合并结果
     */
    public fun <T> merge(node: Node, target: T, policy: MergePolicy): T = throw DocumentSerializationException("${this.javaClass.name} does not support merge")

    // ------------------------------------------------------------------
    // 对象映射便捷 API
    //
    // 下面这些方法都是既有能力（parse / format / toNode / fromNode / convert / merge）
    // 的命名别名或轻量组合，不改变原有语义，也不新增格式绑定：
    // - 树 API：readTree / writeTree / writeTreeAsBytes / valueToTree / treeToValue
    // - 值 API：readValue / writeValueAsString / writeValueAsBytes / updateValue / convertValue
    // - Reader / Writer：readerFor / writer / writerWithDefaultPrettyPrinter
    //
    // 全部为接口默认方法，Kotlin 自定义 DocumentMapperApi 实现无需改动；DocumentMapper
    // 另外显式覆写，使 Java 通过具体 Mapper 类型也能直接调用。
    // ------------------------------------------------------------------

    /**
     * 将文本解析为文档树。
     *
     * 读取行为（格式语法、注释、重复 key、Node 限制等）与 Mapper 的 `parse` 完全一致；
     * 顶层文档为 `null` 时返回 [NullNode.INSTANCE]，而不是 `null`。
     *
     * @param source 格式文本
     * @return 文档树；文本内容为 `null` 时返回 `NullNode`
     */
    public fun readTree(source: String): Node = parse(source, Any::class.java) as? Node ?: NullNode.INSTANCE

    /**
     * 将字节数组解析为文档树。
     *
     * 文本格式按配置字符集解码，二进制格式按原始字节读取；顶层文档为 `null` 时返回
     * [NullNode.INSTANCE]。
     *
     * @param source 二进制内容
     * @return 文档树
     */
    public fun readTree(source: ByteArray): Node = parse(source, Any::class.java) as? Node ?: NullNode.INSTANCE

    /**
     * 从文件解析文档树。
     *
     * @param file 输入文件
     * @return 文档树
     */
    public fun readTree(file: File): Node = parse(file, Any::class.java) as? Node ?: NullNode.INSTANCE

    /**
     * 从路径解析文档树。
     *
     * @param path 输入路径
     * @return 文档树
     */
    public fun readTree(path: Path): Node = parse(path, Any::class.java) as? Node ?: NullNode.INSTANCE

    /**
     * 从输入流解析文档树。
     *
     * 调用方负责关闭流。
     *
     * @param input 输入流
     * @return 文档树
     */
    public fun readTree(input: InputStream): Node = parse(input, Any::class.java) as? Node ?: NullNode.INSTANCE

    /**
     * 从字符流解析文档树。
     *
     * 只支持文本格式；调用方负责关闭 Reader。
     *
     * @param reader 字符输入流
     * @return 文档树
     */
    public fun readTree(reader: Reader): Node = parse(reader, Any::class.java) as? Node ?: NullNode.INSTANCE

    /**
     * 将文档树写为文本。
     *
     * 等价于 `format(node)`；`Node` 作为值直接写出，不当成 Bean 展开。
     *
     * @param node 文档树
     * @return 格式文本
     */
    public fun writeTree(node: Node): String = format(node)

    /**
     * 将文档树写为字节数组。
     *
     * 等价于 `formatBytes(node)`；文本格式按配置字符集编码（JSON 固定 UTF-8），
     * 二进制格式写原始字节。
     *
     * @param node 文档树
     * @return 二进制内容
     */
    public fun writeTreeAsBytes(node: Node): ByteArray = formatBytes(node)

    /**
     * 将任意对象转换为文档树。
     *
     * 等价于 [toNode]。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @return 文档树
     */
    public fun valueToTree(value: Any?): Node = toNode(value)

    /**
     * 将文档树转换为指定 Java 类型。
     *
     * 等价于 `fromNode(node, targetType)`。
     *
     * @param node 文档树
     * @param targetType 目标类型
     * @return 目标对象
     */
    public fun <T> treeToValue(node: Node, targetType: Class<T>): T = fromNode(node, targetType)

    /**
     * 将文档树转换为指定泛型类型。
     *
     * 等价于 `fromNode(node, targetType)`。
     *
     * @param node 文档树
     * @param targetType 目标类型引用
     * @return 目标对象
     */
    public fun <T> treeToValue(node: Node, targetType: DocumentTypeReference<T>): T = fromNode(node, targetType)

    /**
     * 将文本解析为 Java 实体。
     *
     * 等价于 `parse(source, targetType)`。
     *
     * @param source 格式文本
     * @param targetType 目标类型
     * @return 目标对象
     */
    public fun <T> readValue(source: String, targetType: Class<T>): T = parse(source, targetType)

    /**
     * 将文本解析为指定泛型类型。
     *
     * @param source 格式文本
     * @param targetType 目标类型引用
     * @return 目标对象
     */
    public fun <T> readValue(source: String, targetType: DocumentTypeReference<T>): T = parse(source, targetType)

    /**
     * 将字节数组解析为 Java 实体。
     *
     * @param source 二进制内容
     * @param targetType 目标类型
     * @return 目标对象
     */
    public fun <T> readValue(source: ByteArray, targetType: Class<T>): T = parse(source, targetType)

    /**
     * 将字节数组解析为指定泛型类型。
     *
     * @param source 二进制内容
     * @param targetType 目标类型引用
     * @return 目标对象
     */
    public fun <T> readValue(source: ByteArray, targetType: DocumentTypeReference<T>): T = parse(source, targetType)

    /**
     * 从文件解析 Java 实体。
     *
     * @param file 输入文件
     * @param targetType 目标类型
     * @return 目标对象
     */
    public fun <T> readValue(file: File, targetType: Class<T>): T = parse(file, targetType)

    /**
     * 从文件解析指定泛型类型。
     *
     * @param file 输入文件
     * @param targetType 目标类型引用
     * @return 目标对象
     */
    public fun <T> readValue(file: File, targetType: DocumentTypeReference<T>): T = parse(file, targetType)

    /**
     * 从路径解析 Java 实体。
     *
     * @param path 输入路径
     * @param targetType 目标类型
     * @return 目标对象
     */
    public fun <T> readValue(path: Path, targetType: Class<T>): T = parse(path, targetType)

    /**
     * 从路径解析指定泛型类型。
     *
     * @param path 输入路径
     * @param targetType 目标类型引用
     * @return 目标对象
     */
    public fun <T> readValue(path: Path, targetType: DocumentTypeReference<T>): T = parse(path, targetType)

    /**
     * 从输入流解析 Java 实体。
     *
     * 调用方负责关闭流。
     *
     * @param input 输入流
     * @param targetType 目标类型
     * @return 目标对象
     */
    public fun <T> readValue(input: InputStream, targetType: Class<T>): T = parse(input, targetType)

    /**
     * 从输入流解析指定泛型类型。
     *
     * 调用方负责关闭流。
     *
     * @param input 输入流
     * @param targetType 目标类型引用
     * @return 目标对象
     */
    public fun <T> readValue(input: InputStream, targetType: DocumentTypeReference<T>): T = parse(input, targetType)

    /**
     * 从字符流解析 Java 实体。
     *
     * 只支持文本格式；调用方负责关闭 Reader。
     *
     * @param reader 字符输入流
     * @param targetType 目标类型
     * @return 目标对象
     */
    public fun <T> readValue(reader: Reader, targetType: Class<T>): T = parse(reader, targetType)

    /**
     * 从字符流解析指定泛型类型。
     *
     * 只支持文本格式；调用方负责关闭 Reader。
     *
     * @param reader 字符输入流
     * @param targetType 目标类型引用
     * @return 目标对象
     */
    public fun <T> readValue(reader: Reader, targetType: DocumentTypeReference<T>): T = parse(reader, targetType)

    /**
     * 将任意对象写为文本。
     *
     * 等价于 [format]。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @return 格式文本
     */
    public fun writeValueAsString(value: Any?): String = format(value)

    /**
     * 将任意对象写为字节数组。
     *
     * 等价于 [formatBytes]。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @return 二进制内容
     */
    public fun writeValueAsBytes(value: Any?): ByteArray = formatBytes(value)

    /**
     * 将覆盖文档合并到已有对象。
     *
     * 等价于 `merge(toNode(overrides), target)`，即先把覆盖内容序列化为对象节点，
     * 再按 [MergePolicy.REPLACE] 合并：
     * - 可变 Bean 就地修改并返回同一实例；
     * - 不可变 Bean（record、构造器注入、Builder）返回新实例；
     * - 只覆盖 `overrides` 中出现的字段，未出现的字段保留目标对象原值。
     *
     * @param target 已存在的目标对象，不能为 `null`
     * @param overrides 覆盖内容，可以是实体、Map 或对象 [Node]
     * @return 合并结果
     */
    public fun <T> updateValue(target: T, overrides: Any?): T = merge(toNode(overrides), target)

    /**
     * 将任意对象转换为指定 Java 类型。
     *
     * 等价于 [convert]。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationClass 目标类型
     * @return 目标对象
     */
    public fun <D> convertValue(source: Any?, destinationClass: Class<D>): D = convert(source, destinationClass)

    /**
     * 将任意对象转换为指定泛型类型。
     *
     * 等价于 [convert]。
     *
     * @param source 源对象，可以是 Java 实体或 [Node]
     * @param destinationType 目标类型引用
     * @return 目标对象
     */
    public fun <D> convertValue(source: Any?, destinationType: DocumentTypeReference<D>): D = convert(source, destinationType)

    /**
     * 创建绑定目标类型（[Class]）的对象读取器。
     *
     * @param targetType 目标类型
     * @return 对象读取器
     */
    public fun <T> readerFor(targetType: Class<T>): DocumentObjectReader<T> = DocumentObjectReader(this, DocumentTypeReference.of(targetType))

    /**
     * 创建绑定目标泛型类型的对象读取器。
     *
     * @param targetType 目标类型引用
     * @return 对象读取器
     */
    public fun <T> readerFor(targetType: DocumentTypeReference<T>): DocumentObjectReader<T> = DocumentObjectReader(this, targetType)

    /**
     * 创建紧凑风格的对象写出器。
     *
     * @return 对象写出器
     */
    public fun writer(): DocumentObjectWriter = DocumentObjectWriter(this, pretty = false)

    /**
     * 创建格式化（pretty）风格的对象写出器。
     *
     * 二进制格式不支持格式化输出，写出时抛
     * [DocumentSerializationException]。
     *
     * @return 对象写出器
     */
    public fun writerWithDefaultPrettyPrinter(): DocumentObjectWriter = DocumentObjectWriter(this, pretty = true)
}
