package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.exception.AvroException
import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import java.io.ByteArrayOutputStream

/**
 * 按 writer Schema 把多条记录写成 Avro Object Container File（OCF）。
 *
 * 使用 `DataFileWriter<GenericRecord>`，容器头部内嵌 writer Schema，因此读取端
 * 可以只依赖容器字节本身；每个记录通过 [AvroWireWriter] 复用的 Node→datum 规则
 * 转换，根类型必须是 record。
 */
internal class AvroContainerWriter(
    private val schema: Schema,
) {

    /**
     * 写出 OCF。
     *
     * @param values 待写出值，可为 Java 实体、`Map` 或 [Node]
     * @param toNode 值到文档树的转换函数
     * @return OCF 字节
     * @throws AvroException 记录与 Schema 不匹配、不是 record 或写出失败时抛出
     */
    fun write(values: Iterable<Any?>, toNode: (Any?) -> Node): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            DataFileWriter<GenericRecord>(GenericDatumWriter<GenericRecord>(schema)).use { writer ->
                writer.create(schema, output)
                val nodeWriter = AvroWireWriter(schema)
                for (value in values) {
                    val node = toNode(value)
                    val datum = nodeWriter.toDatum(node)
                    if (datum !is GenericRecord) {
                        throw AvroException(
                            "Avro object container only supports record values but was ${node.type}",
                        )
                    }
                    writer.append(datum)
                }
            }
        } catch (e: AvroException) {
            throw e
        } catch (e: Exception) {
            throw AvroException("Failed to write Avro object container: ${e.message}", e)
        }
        return output.toByteArray()
    }
}
