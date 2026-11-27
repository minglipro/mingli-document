package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericEnumSymbol
import org.apache.avro.generic.GenericFixed
import org.apache.avro.generic.IndexedRecord
import org.apache.avro.io.DecoderFactory
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * 用 `GenericDatumReader` / `BinaryDecoder` 读取单根 Avro binary datum，并转换为 [Node]。
 *
 * Avro 二进制不自带 Schema，读取时必须提供与写入一致的 Schema。数据损坏、字节
 * 截断、存在尾随字节或 Schema 不匹配统一抛出 [AvroParseException]；Avro
 * `float`/`double` 的 NaN、Infinity 无法由 [NumberNode] 表达，同样抛出
 * [AvroParseException]。
 */
internal class AvroWireReader(
    private val schema: Schema,
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
            val value = GenericDatumReader<Any?>(schema).read(null, decoder)
            if (!decoder.isEnd()) {
                throw AvroParseException("Unexpected trailing bytes after Avro root datum")
            }
            value
        } catch (e: AvroParseException) {
            throw e
        } catch (e: Exception) {
            throw AvroParseException("Failed to parse Avro binary data: ${e.message}", e)
        }
        return toNode(datum)
    }

    private fun toNode(datum: Any?): Node = when (datum) {
        null -> NullNode.INSTANCE
        is Boolean -> BooleanNode.of(datum)
        is Int, is Long, is Short, is Byte -> NumberNode.of((datum as Number).toLong())
        is Float -> floatNode(datum)
        is Double -> doubleNode(datum)
        is BigDecimal -> NumberNode.of(datum)
        is BigInteger -> NumberNode.of(datum)
        is CharSequence -> StringNode.of(datum.toString())
        is ByteBuffer -> BinaryNode.of(remainingBytes(datum))
        is GenericFixed -> BinaryNode.of(datum.bytes())
        is GenericEnumSymbol<*> -> StringNode.of(datum.toString())
        is IndexedRecord -> recordNode(datum)
        is Map<*, *> -> mapNode(datum)
        is Collection<*> -> arrayNode(datum)
        is Array<*> -> arrayNode(datum.asList())
        else -> throw AvroParseException("Unsupported Avro datum type: ${datum.javaClass.name}")
    }

    private fun floatNode(value: Float): Node {
        if (!value.isFinite()) {
            throw AvroParseException("Avro float NaN/Infinity cannot be represented as a number node")
        }
        return NumberNode.of(value)
    }

    private fun doubleNode(value: Double): Node {
        if (!value.isFinite()) {
            throw AvroParseException("Avro double NaN/Infinity cannot be represented as a number node")
        }
        return NumberNode.of(value)
    }

    private fun recordNode(record: IndexedRecord): ObjectNode {
        val recordSchema = record.schema
        val fields = LinkedHashMap<String, Node>(recordSchema.fields.size)
        for (field in recordSchema.fields) {
            fields[field.name()] = toNode(record.get(field.pos()))
        }
        return ObjectNode.from(fields)
    }

    private fun mapNode(map: Map<*, *>): ObjectNode {
        val fields = LinkedHashMap<String, Node>(map.size)
        for ((key, value) in map) {
            fields[key?.toString() ?: "null"] = toNode(value)
        }
        return ObjectNode.from(fields)
    }

    private fun arrayNode(values: Collection<*>): ArrayNode {
        val elements = ArrayList<Node>(values.size)
        for (element in values) {
            elements.add(toNode(element))
        }
        return ArrayNode.from(elements)
    }

    private fun remainingBytes(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }
}
