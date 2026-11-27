package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.io.DecoderFactory

/**
 * 用 `GenericDatumReader` / `BinaryDecoder` 读取单根 Avro binary datum，并转换为 [Node]。
 *
 * Avro 二进制不自带 Schema，读取时必须提供 writer Schema；提供 reader Schema 时按
 * Avro Schema 解析规则做演进（新增带默认值字段、删除字段、类型提升）。数据损坏、
 * 字节截断、存在尾随字节、Schema 不匹配或无法演进统一抛出 [AvroParseException]；
 * Avro `float`/`double` 的 NaN、Infinity 无法由
 * [com.mingliqiye.document.core.NumberNode] 表达，同样抛出 [AvroParseException]。
 */
internal class AvroWireReader(
    private val writerSchema: Schema,
    private val readerSchema: Schema,
    private val maxDepth: Int = 0,
) {

    /**
     * 读取单根 datum。
     *
     * @param source Avro binary 编码
     * @return 文档树
     * @throws AvroParseException 字节非法、存在尾随字节或 Schema 不匹配时抛出
     */
    fun read(source: ByteArray): Node {
        val decoder = DecoderFactory.get().binaryDecoder(source, null)
        val datum = try {
            val value = GenericDatumReader<Any?>(writerSchema, readerSchema).read(null, decoder)
            if (!decoder.isEnd()) {
                throw AvroParseException("Unexpected trailing bytes after Avro root datum")
            }
            value
        } catch (e: AvroParseException) {
            throw e
        } catch (e: Exception) {
            throw AvroParseException("Failed to parse Avro binary data: ${e.message}", e)
        }
        return AvroDatumConverter.toNode(datum, maxDepth)
    }
}
