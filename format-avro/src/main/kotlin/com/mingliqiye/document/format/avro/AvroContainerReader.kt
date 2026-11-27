package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord

/**
 * 读取 Avro Object Container File（OCF）并转换为 [Node] 列表。
 *
 * 使用 `DataFileReader<GenericRecord>`：writer Schema 取自容器头部，reader Schema
 * 由构造参数提供（通常来自 [AvroMapper.readerSchema]），由 `GenericDatumReader`
 * 完成 Schema 演进。容器损坏、非 OCF 字节或 schema 无法演进统一抛出
 * [AvroParseException]。
 */
internal class AvroContainerReader(
    private val readerSchema: Schema,
) {

    /**
     * 读取容器内全部记录。
     *
     * @param source OCF 字节
     * @return 文档树列表，保持记录顺序
     * @throws AvroParseException 容器非法或 Schema 不匹配时抛出
     */
    fun read(source: ByteArray): List<Node> {
        val nodes = ArrayList<Node>()
        try {
            val datumReader = GenericDatumReader<GenericRecord>(null, readerSchema)
            DataFileReader<GenericRecord>(SeekableByteArrayInput(source), datumReader).use { reader ->
                while (reader.hasNext()) {
                    nodes.add(AvroDatumConverter.toNode(reader.next()))
                }
            }
        } catch (e: AvroParseException) {
            throw e
        } catch (e: Exception) {
            throw AvroParseException("Failed to parse Avro object container: ${e.message}", e)
        }
        return nodes
    }
}
