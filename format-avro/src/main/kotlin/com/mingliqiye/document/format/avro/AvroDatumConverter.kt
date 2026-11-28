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
import org.apache.avro.generic.GenericEnumSymbol
import org.apache.avro.generic.GenericFixed
import org.apache.avro.generic.IndexedRecord
import java.nio.ByteBuffer

/**
 * 把 Avro 通用 datum 按 Schema 转换为 [Node]。
 *
 * 转换按 Schema 节点类型分派，因此能看到字节流本身不携带的 Schema 信息：
 * record 使用 Schema 的字段顺序，array/map 使用元素的 Schema 递归转换，
 * union 按 datum 运行时类型选择分支，并对已知 logical type 使用
 * [AvroLogicalValues] 转换为适合文档树的表示。
 *
 * Avro `float`/`double` 的 NaN、Infinity 无法由 [NumberNode] 表达，抛出
 * [AvroParseException]；Schema 中已知 logical type 结构非法同样抛出
 * [AvroParseException]。
 */
internal object AvroDatumConverter {

    /**
     * 转换单个 Avro datum。
     *
     * @param datum Avro 通用 datum
     * @param schema datum 对应的 Schema 节点（通常是 reader Schema）
     * @param maxDepth 最大嵌套深度；`0` 表示不限制
     * @return 文档树
     * @throws AvroParseException 出现不支持的 datum 类型、NaN/Infinity 或非法逻辑类型时抛出
     */
    fun toNode(datum: Any?, schema: Schema, maxDepth: Int): Node = try {
        convert(datum, schema, 1, maxDepth, "$")
    } catch (e: AvroParseException) {
        throw e
    } catch (e: RuntimeException) {
        throw AvroParseException("Failed to convert Avro datum to node: ${e.message}", e)
    }

    private fun convert(datum: Any?, schema: Schema, depth: Int, maxDepth: Int, path: String): Node {
        if (schema.type == Schema.Type.UNION) {
            return convert(datum, unionBranch(datum, schema, path), depth, maxDepth, path)
        }
        val logicalType = logicalTypeOf(schema, path)
        if (logicalType != null) {
            return AvroLogicalValues.toNode(datum, schema, logicalType, path)
        }
        return when (schema.type) {
            Schema.Type.NULL -> NullNode.INSTANCE
            Schema.Type.BOOLEAN -> BooleanNode.of(booleanDatum(datum, path))
            Schema.Type.INT -> NumberNode.of(intDatum(datum, path))
            Schema.Type.LONG -> NumberNode.of(longDatum(datum, path))
            Schema.Type.FLOAT -> floatNode(datum, path)
            Schema.Type.DOUBLE -> doubleNode(datum, path)
            Schema.Type.STRING -> StringNode.of(charSequenceDatum(datum, path))
            Schema.Type.BYTES -> BinaryNode.of(byteBufferBytes(datum, path))
            Schema.Type.FIXED -> BinaryNode.of(fixedBytes(datum, path))
            Schema.Type.ENUM -> StringNode.of(enumSymbol(datum, path))
            Schema.Type.ARRAY -> arrayNode(datum, schema, depth, maxDepth, path)
            Schema.Type.MAP -> mapNode(datum, schema, depth, maxDepth, path)
            Schema.Type.RECORD -> recordNode(datum, schema, depth, maxDepth, path)
            Schema.Type.UNION -> throw AvroParseException("Unexpected Avro union at $path")
        }
    }

    private fun logicalTypeOf(schema: Schema, path: String): AvroLogicalType? = try {
        AvroLogicalType.fromSchema(schema)
    } catch (e: IllegalArgumentException) {
        throw AvroParseException("Invalid Avro logical type at $path: ${e.message}", e)
    }

    private fun checkDepth(depth: Int, maxDepth: Int) {
        if (maxDepth > 0 && depth > maxDepth) {
            throw AvroParseException("Avro nesting depth $depth exceeds maxDepth $maxDepth")
        }
    }

    private fun booleanDatum(datum: Any?, path: String): Boolean = datum as? Boolean
        ?: throw AvroParseException("Expected boolean datum at $path but was ${datumType(datum)}")

    private fun intDatum(datum: Any?, path: String): Long = when (datum) {
        is Int -> datum.toLong()
        is Short -> datum.toLong()
        is Byte -> datum.toLong()
        is Long -> if (datum in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            datum
        } else {
            throw AvroParseException("Long datum $datum at $path does not fit Avro int")
        }

        else -> throw AvroParseException("Expected int datum at $path but was ${datumType(datum)}")
    }

    private fun longDatum(datum: Any?, path: String): Long = when (datum) {
        is Long -> datum
        is Int -> datum.toLong()
        is Short -> datum.toLong()
        is Byte -> datum.toLong()
        else -> throw AvroParseException("Expected long datum at $path but was ${datumType(datum)}")
    }

    private fun floatNode(datum: Any?, path: String): Node {
        val value = when (datum) {
            is Float -> datum
            is Number -> datum.toFloat()
            else -> throw AvroParseException("Expected float datum at $path but was ${datumType(datum)}")
        }
        if (!value.isFinite()) {
            throw AvroParseException("Avro float NaN/Infinity cannot be represented as a number node")
        }
        return NumberNode.of(value)
    }

    private fun doubleNode(datum: Any?, path: String): Node {
        val value = when (datum) {
            is Double -> datum
            is Number -> datum.toDouble()
            else -> throw AvroParseException("Expected double datum at $path but was ${datumType(datum)}")
        }
        if (!value.isFinite()) {
            throw AvroParseException("Avro double NaN/Infinity cannot be represented as a number node")
        }
        return NumberNode.of(value)
    }

    private fun charSequenceDatum(datum: Any?, path: String): String = when (datum) {
        is CharSequence -> datum.toString()
        else -> throw AvroParseException("Expected string datum at $path but was ${datumType(datum)}")
    }

    private fun byteBufferBytes(datum: Any?, path: String): ByteArray {
        if (datum !is ByteBuffer) {
            throw AvroParseException("Expected bytes datum at $path but was ${datumType(datum)}")
        }
        return remainingBytes(datum)
    }

    private fun fixedBytes(datum: Any?, path: String): ByteArray {
        if (datum !is GenericFixed) {
            throw AvroParseException("Expected fixed datum at $path but was ${datumType(datum)}")
        }
        return datum.bytes()
    }

    private fun enumSymbol(datum: Any?, path: String): String {
        if (datum !is GenericEnumSymbol<*>) {
            throw AvroParseException("Expected enum datum at $path but was ${datumType(datum)}")
        }
        return datum.toString()
    }

    private fun arrayNode(datum: Any?, schema: Schema, depth: Int, maxDepth: Int, path: String): ArrayNode {
        checkDepth(depth, maxDepth)
        val values: List<Any?> = when (datum) {
            is Collection<*> -> datum.toList()
            is Array<*> -> datum.asList()
            else -> throw AvroParseException("Expected array datum at $path but was ${datumType(datum)}")
        }
        val elementSchema = schema.elementType
        val elements = ArrayList<Node>(values.size)
        values.forEachIndexed { index, element ->
            elements.add(convert(element, elementSchema, depth + 1, maxDepth, "$path[$index]"))
        }
        return ArrayNode.from(elements)
    }

    private fun mapNode(datum: Any?, schema: Schema, depth: Int, maxDepth: Int, path: String): ObjectNode {
        checkDepth(depth, maxDepth)
        if (datum !is Map<*, *>) {
            throw AvroParseException("Expected map datum at $path but was ${datumType(datum)}")
        }
        val valueSchema = schema.valueType
        val fields = LinkedHashMap<String, Node>(datum.size)
        for ((key, value) in datum) {
            val name = key?.toString() ?: "null"
            fields[name] = convert(value, valueSchema, depth + 1, maxDepth, "$path.$name")
        }
        return ObjectNode.from(fields)
    }

    private fun recordNode(datum: Any?, schema: Schema, depth: Int, maxDepth: Int, path: String): ObjectNode {
        checkDepth(depth, maxDepth)
        if (datum !is IndexedRecord) {
            throw AvroParseException(
                "Expected record datum at $path for ${schema.fullName} but was ${datumType(datum)}",
            )
        }
        val fields = LinkedHashMap<String, Node>(schema.fields.size)
        for (field in schema.fields) {
            fields[field.name()] = convert(
                datum.get(field.pos()),
                field.schema(),
                depth + 1,
                maxDepth,
                "$path.${field.name()}",
            )
        }
        return ObjectNode.from(fields)
    }

    private fun unionBranch(datum: Any?, schema: Schema, path: String): Schema {
        val branches = schema.types
        if (datum == null) {
            return branches.firstOrNull { it.type == Schema.Type.NULL }
                ?: throw AvroParseException("Cannot resolve Avro union branch for null at $path")
        }
        val branch = when (datum) {
            is Boolean -> branches.firstOfType(Schema.Type.BOOLEAN)
            is Int -> branches.firstOfType(Schema.Type.INT)
            is Long -> branches.firstOfType(Schema.Type.LONG)
            is Float -> branches.firstOfType(Schema.Type.FLOAT)
            is Double -> branches.firstOfType(Schema.Type.DOUBLE)
            is GenericEnumSymbol<*> -> branches.firstOfType(Schema.Type.ENUM)
            is CharSequence -> branches.firstOfType(Schema.Type.STRING)
            is ByteBuffer -> branches.firstOfType(Schema.Type.BYTES)
            is GenericFixed -> branches.firstOrNull {
                it.type == Schema.Type.FIXED && it.fullName == datum.schema.fullName
            } ?: branches.firstOfType(Schema.Type.FIXED)

            is IndexedRecord -> branches.firstOrNull {
                it.type == Schema.Type.RECORD && it.fullName == datum.schema.fullName
            } ?: branches.firstOfType(Schema.Type.RECORD)

            is Map<*, *> -> branches.firstOfType(Schema.Type.MAP)
            is Collection<*>, is Array<*> -> branches.firstOfType(Schema.Type.ARRAY)
            else -> throw AvroParseException(
                "Unsupported Avro union datum ${datumType(datum)} at $path",
            )
        }
        return branch ?: throw AvroParseException(
            "Cannot resolve Avro union branch for ${datumType(datum)} at $path",
        )
    }

    private fun List<Schema>.firstOfType(type: Schema.Type): Schema? = firstOrNull { it.type == type }

    private fun datumType(datum: Any?): String = datum?.javaClass?.name ?: "null"

    private fun remainingBytes(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }
}
