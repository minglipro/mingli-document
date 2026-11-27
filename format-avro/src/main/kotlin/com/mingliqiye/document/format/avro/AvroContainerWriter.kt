package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.exception.AvroException
import org.apache.avro.Schema
import org.apache.avro.file.DataFileWriter
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 按 writer Schema 与指定 [codec] 把多条记录写成 Avro Object Container File（OCF），
 * 并支持向已有容器追加记录。
 *
 * 使用 `DataFileWriter<GenericRecord>`，容器头部内嵌 writer Schema 与 codec 元数据；
 * 每个记录通过 [AvroWireWriter] 复用的 Node→datum 规则转换，根类型必须是 record。
 * 追加时复用已有容器的 sync 标记并在其后写入新数据块，已有记录保持不变。
 */
internal class AvroContainerWriter(
    private val schema: Schema,
    private val codec: AvroCodec = AvroCodec.NULL,
) {

    /**
     * 写出 OCF 字节。
     *
     * @param values 待写出值，可为 Java 实体、`Map` 或 [Node]
     * @param toNode 值到文档树的转换函数
     * @return OCF 字节
     * @throws AvroException 记录与 Schema 不匹配、不是 record 或写出失败时抛出
     */
    fun write(values: Iterable<Any?>, toNode: (Any?) -> Node): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            newWriter().use { writer ->
                writer.setCodec(codec.toCodecFactory())
                writer.create(schema, output)
                appendValues(writer, values, toNode)
            }
        } catch (e: AvroException) {
            throw e
        } catch (e: Exception) {
            throw AvroException("Failed to write Avro object container: ${e.message}", e)
        }
        return output.toByteArray()
    }

    /**
     * 新建（或覆盖）文件写入 OCF。
     *
     * @param file 目标文件
     * @param values 待写出值
     * @param toNode 值到文档树的转换函数
     * @throws AvroException 记录与 Schema 不匹配或写出失败时抛出
     */
    fun writeToFile(file: File, values: Iterable<Any?>, toNode: (Any?) -> Node) {
        try {
            newWriter().use { writer ->
                writer.setCodec(codec.toCodecFactory())
                writer.create(schema, file)
                appendValues(writer, values, toNode)
            }
        } catch (e: AvroException) {
            throw e
        } catch (e: Exception) {
            throw AvroException("Failed to write Avro object container: ${e.message}", e)
        }
    }

    /**
     * 向已有 OCF 文件追加记录。
     *
     * 会先校验文件头部 Schema 与 codec 是否与本 writer 一致；不一致抛 [AvroException]。
     * 追加保留已有记录与 sync 标记。
     *
     * @param file 已有 OCF 文件
     * @param values 待追加值
     * @param toNode 值到文档树的转换函数
     * @throws AvroException Schema/codec 不一致或追加失败时抛出
     */
    fun appendTo(file: File, values: Iterable<Any?>, toNode: (Any?) -> Node) {
        try {
            validateAppend(AvroContainerReader.readHeader(file))
            newWriter().use { writer ->
                writer.appendTo(file)
                appendValues(writer, values, toNode)
            }
        } catch (e: AvroException) {
            throw e
        } catch (e: Exception) {
            throw AvroException("Failed to append Avro object container: ${e.message}", e)
        }
    }

    /**
     * 向已有 OCF 字节追加记录，返回追加后的完整容器字节。
     *
     * 输出以原容器字节开头，新记录以原 sync 标记写入后续数据块。
     *
     * @param existing 已有 OCF 字节
     * @param values 待追加值
     * @param toNode 值到文档树的转换函数
     * @return 追加后的 OCF 字节
     * @throws AvroException Schema/codec 不一致或追加失败时抛出
     */
    fun appendTo(existing: ByteArray, values: Iterable<Any?>, toNode: (Any?) -> Node): ByteArray {
        validateAppend(AvroContainerReader.readHeader(existing))
        val output = ByteArrayOutputStream(existing.size + 1024)
        output.write(existing)
        try {
            newWriter().use { writer ->
                writer.appendTo(SeekableByteArrayInput(existing), output)
                appendValues(writer, values, toNode)
            }
        } catch (e: AvroException) {
            throw e
        } catch (e: Exception) {
            throw AvroException("Failed to append Avro object container: ${e.message}", e)
        }
        return output.toByteArray()
    }

    private fun newWriter(): DataFileWriter<GenericRecord> = DataFileWriter<GenericRecord>(GenericDatumWriter<GenericRecord>(schema))

    private fun appendValues(
        writer: DataFileWriter<GenericRecord>,
        values: Iterable<Any?>,
        toNode: (Any?) -> Node,
    ) {
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

    private fun validateAppend(header: AvroContainerHeader) {
        if (header.schema != schema) {
            throw AvroException(
                "Cannot append to Avro container: existing schema '${header.schema.fullName}' " +
                    "does not match writer schema '${schema.fullName}'",
            )
        }
        if (header.codec != codec) {
            throw AvroException(
                "Cannot append to Avro container: existing codec '${header.codec.codecName}' " +
                    "does not match requested codec '${codec.codecName}'",
            )
        }
    }
}
