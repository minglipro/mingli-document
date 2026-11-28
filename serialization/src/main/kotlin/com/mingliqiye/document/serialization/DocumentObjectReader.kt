package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path

/**
 * 绑定目标类型的对象读取器。
 *
 * 通过 [DocumentMapperApi.readerFor] 创建，是 Mapper `parse` / `readValue` 的轻量包装，
 * 用于在多处复用“同一个目标类型 + 同一套读取规则”的组合。它不增加任何新的配置能力：
 * 字符集、输入大小限制、`$ref` 解析、未知/缺失字段策略等全部由创建它的 Mapper 决定。
 *
 * 实例没有内部可变状态，可以复用；是否线程安全取决于底层 Mapper。
 *
 * @param T 目标类型
 */
public class DocumentObjectReader<T> internal constructor(
    private val mapper: DocumentMapperApi,
    /** 绑定的目标类型。 */
    public val targetType: DocumentTypeReference<T>,
) {

    /**
     * 从文本解析。
     *
     * @param source 格式文本
     * @return 目标对象
     */
    public fun readValue(source: String): T = mapper.readValue(source, targetType)

    /**
     * 从字节数组解析。
     *
     * 二进制格式直接按字节读取；文本格式按 Mapper 配置的字符集解码。
     *
     * @param source 字节内容
     * @return 目标对象
     */
    public fun readValue(source: ByteArray): T = mapper.readValue(source, targetType)

    /**
     * 从文件解析。
     *
     * @param file 输入文件
     * @return 目标对象
     */
    public fun readValue(file: File): T = mapper.readValue(file, targetType)

    /**
     * 从路径解析。
     *
     * @param path 输入路径
     * @return 目标对象
     */
    public fun readValue(path: Path): T = mapper.readValue(path, targetType)

    /**
     * 从输入流解析。
     *
     * 调用方负责关闭流。
     *
     * @param input 输入流
     * @return 目标对象
     */
    public fun readValue(input: InputStream): T = mapper.readValue(input, targetType)

    /**
     * 从字符流解析。
     *
     * 只支持文本格式；调用方负责关闭 Reader。
     *
     * @param reader 字符输入流
     * @return 目标对象
     */
    public fun readValue(reader: Reader): T = mapper.readValue(reader, targetType)

    override fun toString(): String = "DocumentObjectReader(${targetType.type})"
}
