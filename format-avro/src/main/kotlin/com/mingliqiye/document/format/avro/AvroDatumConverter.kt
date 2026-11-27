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
import org.apache.avro.generic.GenericEnumSymbol
import org.apache.avro.generic.GenericFixed
import org.apache.avro.generic.IndexedRecord
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer

/**
 * 把 Avro 通用 datum 转换为 [Node]。
 *
 * 转换按 datum 的运行时类型分派：`record` 使用其自身 Schema 的字段顺序，
 * `bytes`/`fixed` 映射为 [BinaryNode]，`enum` 映射为 [StringNode]，
 * `array`/`map` 映射为 [ArrayNode]/[ObjectNode]，`Utf8` 映射为 [StringNode]。
 *
 * Avro `float`/`double` 的 NaN、Infinity 无法由 [NumberNode] 表达，抛出
 * [AvroParseException]。
 */
internal object AvroDatumConverter {

    /**
     * 转换单个 Avro datum。
     *
     * @param datum Avro 通用 datum
     * @return 文档树
     * @throws AvroParseException 出现不支持的 datum 类型或 NaN/Infinity 时抛出
     */
    fun toNode(datum: Any?): Node = when (datum) {
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
