package com.mingliqiye.document.serialization

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.Writer
import java.nio.file.Path

/**
 * 对象写出器。
 *
 * 通过 [DocumentMapperApi.writer] / [DocumentMapperApi.writerWithDefaultPrettyPrinter] 创建，
 * 是 Mapper `format` / `formatPretty` / `formatBytes` 的轻量包装，固定一种输出风格
 * （紧凑或格式化）。它不提供额外的格式配置能力：字符集、原子写、父目录创建、Node 限制等
 * 全部由创建它的 Mapper 决定。
 *
 * 实例没有内部可变状态，可以复用；是否线程安全取决于底层 Mapper。
 */
public class DocumentObjectWriter internal constructor(
    private val mapper: DocumentMapperApi,
    /** 是否使用格式化（pretty）输出。 */
    public val pretty: Boolean,
) {

    /**
     * 将任意对象写为文本。
     *
     * 格式化 writer 等价于 [DocumentMapperApi.formatPretty]；普通 writer 等价于
     * [DocumentMapperApi.format]。二进制格式会抛
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @return 格式文本
     */
    public fun writeValueAsString(value: Any?): String = if (pretty) mapper.formatPretty(value) else mapper.format(value)

    /**
     * 将任意对象写为字节数组。
     *
     * 文本格式按 Mapper 配置的字符集编码（JSON 固定 UTF-8）；二进制格式写原始字节。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @return 字节内容
     */
    public fun writeValueAsBytes(value: Any?): ByteArray {
        val output = ByteArrayOutputStream()
        writeValue(value, output)
        return output.toByteArray()
    }

    /**
     * 将任意对象写入文件。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @param file 目标文件
     */
    public fun writeValue(value: Any?, file: File) {
        if (pretty) mapper.formatPretty(value, file) else mapper.format(value, file)
    }

    /**
     * 将任意对象写入路径。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @param path 目标路径
     */
    public fun writeValue(value: Any?, path: Path) {
        if (pretty) mapper.formatPretty(value, path) else mapper.format(value, path)
    }

    /**
     * 将任意对象写入输出流。
     *
     * 调用方负责关闭流；本方法只 flush。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @param output 目标输出流
     */
    public fun writeValue(value: Any?, output: OutputStream) {
        if (pretty) mapper.formatPretty(value, output) else mapper.format(value, output)
    }

    /**
     * 将任意对象写入字符流。
     *
     * 只支持文本格式；调用方负责关闭 Writer。
     *
     * @param value 任意实体、集合、Map 或 Node
     * @param writer 目标字符流
     */
    public fun writeValue(value: Any?, writer: Writer) {
        if (pretty) mapper.formatPretty(value, writer) else mapper.format(value, writer)
    }

    override fun toString(): String = "DocumentObjectWriter(pretty=$pretty)"
}
