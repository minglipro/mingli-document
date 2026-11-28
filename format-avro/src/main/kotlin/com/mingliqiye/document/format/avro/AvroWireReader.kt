package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import java.io.ByteArrayInputStream

/**
 * 用 `GenericDatumReader` / `BinaryDecoder` 读取单根 Avro binary datum，并转换为 [Node]。
 *
 * Avro 二进制不自带 Schema，读取时必须提供 writer Schema；提供 reader Schema 时按
 * Avro Schema 解析规则做演进（新增带默认值字段、删除字段、类型提升）。数据损坏、
 * 字节截断、存在尾随字节、Schema 不匹配或无法演进统一抛出 [AvroParseException]；
 * Avro `float`/`double` 的 NaN、Infinity 无法由
 * [com.mingliqiye.document.core.NumberNode] 表达，同样抛出 [AvroParseException]。
 *
 * [buffering] 对齐 上游实现 `AVRO_BUFFERING`：开启时使用缓冲解码器，关闭时使用
 * `DirectBinaryDecoder`（只读取最少字节）。两种模式解析结果与错误行为一致。
 *
 * datum → Node 转换按 reader Schema 进行，因此声明在 reader Schema 上的 logical
 * type（date / time / timestamp / local-timestamp / decimal / uuid / duration）
 * 会生效，见 [AvroLogicalType] 与 [AvroLogicalValues]。
 */
internal class AvroWireReader(
    private val writerSchema: Schema,
    private val readerSchema: Schema,
    private val maxDepth: Int = 0,
    /** 是否使用 Avro 缓冲解码器（对齐 上游实现 `AVRO_BUFFERING`，默认开启）。 */
    internal val buffering: Boolean = true,
) {

    /**
     * 读取单根 datum。
     *
     * @param source Avro binary 编码
     * @return 文档树
     * @throws AvroParseException 字节非法、存在尾随字节、Schema 不匹配或逻辑类型非法时抛出
     */
    fun read(source: ByteArray): Node {
        val datum = try {
            if (buffering) {
                val decoder = AvroFeatureSupport.createBufferedDecoder(source)
                val value = GenericDatumReader<Any?>(writerSchema, readerSchema).read(null, decoder)
                if (!decoder.isEnd()) {
                    throw trailingBytes()
                }
                value
            } else {
                // direct 解码器不支持 isEnd()，用底层 ByteArrayInputStream 的剩余字节判断尾随数据
                val input = ByteArrayInputStream(source)
                val decoder = AvroFeatureSupport.createDirectDecoder(input)
                val value = GenericDatumReader<Any?>(writerSchema, readerSchema).read(null, decoder)
                if (input.available() > 0) {
                    throw trailingBytes()
                }
                value
            }
        } catch (e: AvroParseException) {
            throw e
        } catch (e: Exception) {
            throw AvroParseException("Failed to parse Avro binary data: ${e.message}", e)
        }
        return AvroDatumConverter.toNode(datum, readerSchema, maxDepth)
    }

    private fun trailingBytes(): AvroParseException = AvroParseException("Unexpected trailing bytes after Avro root datum")
}
