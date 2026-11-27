package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.file.DataFileConstants
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.DataFileStream
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * OCF 容器头部信息。
 *
 * @property schema 容器内嵌的 writer Schema
 * @property codec 容器声明的 codec
 */
internal class AvroContainerHeader(
    val schema: Schema,
    val codec: AvroCodec,
)

/**
 * 读取 Avro Object Container File（OCF）并转换为 [Node] 列表。
 *
 * 使用 `DataFileReader<GenericRecord>`：writer Schema 与 codec 取自容器头部
 * （`DataFileReader` 自动识别 codec），reader Schema 由构造参数提供（通常来自
 * [AvroMapper.readerSchema]），由 `GenericDatumReader` 完成 Schema 演进。
 * 容器损坏、非 OCF 字节、codec 不受支持（只支持 [AvroCodec] 中的三种）或 schema
 * 无法演进统一抛出 [AvroParseException]。
 */
internal class AvroContainerReader(
    private val readerSchema: Schema,
) {

    /**
     * 读取容器内全部记录。
     *
     * @param source OCF 字节
     * @return 文档树列表，保持记录顺序
     * @throws AvroParseException 容器非法、codec 不支持或 Schema 不匹配时抛出
     */
    fun read(source: ByteArray): List<Node> {
        val nodes = ArrayList<Node>()
        try {
            // 先校验容器头部 codec，让不支持 codec 的异常信息更明确
            readHeader(source)
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

    companion object {

        /**
         * 读取 OCF 字节的容器头部。
         *
         * @param source OCF 字节
         * @return 容器头部信息
         * @throws AvroParseException 容器非法或 codec 不支持时抛出
         */
        fun readHeader(source: ByteArray): AvroContainerHeader = readHeader(ByteArrayInputStream(source))

        /**
         * 读取 OCF 文件的容器头部。
         *
         * @param file OCF 文件
         * @return 容器头部信息
         * @throws AvroParseException 容器非法或 codec 不支持时抛出
         */
        fun readHeader(file: File): AvroContainerHeader = readHeader(file.inputStream())

        private fun readHeader(input: InputStream): AvroContainerHeader {
            try {
                DataFileStream<GenericRecord>(input, GenericDatumReader<GenericRecord>()).use { stream ->
                    val codec = AvroCodec.fromContainerCodec(stream.getMetaString(DataFileConstants.CODEC))
                    return AvroContainerHeader(stream.schema, codec)
                }
            } catch (e: AvroParseException) {
                throw e
            } catch (e: Exception) {
                throw AvroParseException("Failed to read Avro object container header: ${e.message}", e)
            }
        }
    }
}
