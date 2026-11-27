package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.format.avro.exception.AvroException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64

/**
 * 按 Avro Schema 把 [Node] 转换成 Avro datum，再用 `GenericDatumWriter` /
 * `BinaryEncoder` 写成二进制。
 *
 * 记录字段按 Schema 顺序写出：Node 缺少字段时优先使用 Schema 默认值，可空 union
 * 字段写 `null`，其余情况抛出 [AvroException]；记录中出现 Schema 未声明的字段同样
 * 抛出 [AvroException]。
 */
internal class AvroWireWriter(
    private val schema: Schema,
) {

    /**
     * 写出单根 datum。
     *
     * @param node 文档树
     * @return Avro binary 编码
     * @throws AvroException 节点与 Schema 不匹配或编码失败时抛出
     */
    fun write(node: Node): ByteArray {
        val datum = toDatum(node, schema, "$")
        val output = ByteArrayOutputStream()
        try {
            val encoder = EncoderFactory.get().binaryEncoder(output, null)
            GenericDatumWriter<Any?>(schema).write(datum, encoder)
            encoder.flush()
        } catch (e: Exception) {
            throw AvroException("Failed to write Avro binary data: ${e.message}", e)
        }
        return output.toByteArray()
    }

    private fun toDatum(node: Node, schema: Schema, path: String): Any? = when (schema.type) {
        Schema.Type.NULL -> nullDatum(node, path)
        Schema.Type.BOOLEAN -> booleanDatum(node, path)
        Schema.Type.INT -> intDatum(node, path)
        Schema.Type.LONG -> longDatum(node, path)
        Schema.Type.FLOAT -> floatDatum(node, path)
        Schema.Type.DOUBLE -> doubleDatum(node, path)
        Schema.Type.STRING -> stringDatum(node, path)
        Schema.Type.BYTES -> bytesDatum(node, path)
        Schema.Type.FIXED -> fixedDatum(node, schema, path)
        Schema.Type.ENUM -> enumDatum(node, schema, path)
        Schema.Type.ARRAY -> arrayDatum(node, schema, path)
        Schema.Type.MAP -> mapDatum(node, schema, path)
        Schema.Type.RECORD -> recordDatum(node, schema, path)
        Schema.Type.UNION -> unionDatum(node, schema, path)
    }

    private fun nullDatum(node: Node, path: String): Any? {
        if (!node.isNull) {
            throw AvroException("Expected null at $path but was ${node.type}")
        }
        return null
    }

    private fun booleanDatum(node: Node, path: String): Boolean = node.asBooleanOrNull()
        ?: throw AvroException("Expected boolean at $path but was ${node.type}")

    private fun intDatum(node: Node, path: String): Int {
        val number = numberNode(node, path)
        return try {
            number.toInt()
        } catch (e: ArithmeticException) {
            throw AvroException("Number '${number.raw}' at $path does not fit Avro int", e)
        }
    }

    private fun longDatum(node: Node, path: String): Long {
        val number = numberNode(node, path)
        return try {
            number.toLong()
        } catch (e: ArithmeticException) {
            throw AvroException("Number '${number.raw}' at $path does not fit Avro long", e)
        }
    }

    private fun floatDatum(node: Node, path: String): Float = numberNode(node, path).toFloat()

    private fun doubleDatum(node: Node, path: String): Double = numberNode(node, path).toDouble()

    private fun stringDatum(node: Node, path: String): String = node.asStringOrNull()
        ?: throw AvroException("Expected string at $path but was ${node.type}")

    private fun bytesDatum(node: Node, path: String): ByteBuffer = ByteBuffer.wrap(binaryBytes(node, path))

    private fun fixedDatum(node: Node, schema: Schema, path: String): GenericData.Fixed {
        val bytes = binaryBytes(node, path)
        if (bytes.size != schema.fixedSize) {
            throw AvroException("Avro fixed at $path expects ${schema.fixedSize} bytes but was ${bytes.size}")
        }
        return GenericData.Fixed(schema, bytes)
    }

    private fun enumDatum(node: Node, schema: Schema, path: String): GenericData.EnumSymbol {
        val symbol = node.asStringOrNull()
            ?: throw AvroException(
                "Expected string at $path for Avro enum ${schema.fullName} but was ${node.type}",
            )
        if (!schema.hasEnumSymbol(symbol)) {
            throw AvroException("Unknown Avro enum symbol '$symbol' at $path for ${schema.fullName}")
        }
        return GenericData.EnumSymbol(schema, symbol)
    }

    private fun arrayDatum(node: Node, schema: Schema, path: String): List<Any?> {
        val array = node.asArrayOrNull()
            ?: throw AvroException("Expected array at $path for Avro array but was ${node.type}")
        val elementSchema = schema.elementType
        val values = ArrayList<Any?>(array.size)
        for (index in 0 until array.size) {
            values.add(toDatum(array[index], elementSchema, "$path[$index]"))
        }
        return values
    }

    private fun mapDatum(node: Node, schema: Schema, path: String): Map<String, Any?> {
        val objectNode = node.asObjectOrNull()
            ?: throw AvroException("Expected object at $path for Avro map but was ${node.type}")
        val valueSchema = schema.valueType
        val values = LinkedHashMap<String, Any?>(objectNode.size)
        for (key in objectNode.keys) {
            values[key] = toDatum(objectNode[key]!!, valueSchema, "$path.$key")
        }
        return values
    }

    private fun recordDatum(node: Node, schema: Schema, path: String): GenericData.Record {
        val objectNode = node.asObjectOrNull()
            ?: throw AvroException(
                "Expected object at $path for Avro record ${schema.fullName} but was ${node.type}",
            )
        rejectUnknownFields(objectNode.keys, schema, path)
        val record = GenericData.Record(schema)
        for (field in schema.fields) {
            val fieldPath = "$path.${field.name()}"
            val value = when {
                objectNode.containsKey(field.name()) ->
                    toDatum(objectNode[field.name()]!!, field.schema(), fieldPath)

                field.hasDefaultValue() -> defaultDatum(field, fieldPath)
                allowsNull(field.schema()) -> null
                else -> throw AvroException(
                    "Missing Avro field '${field.name()}' at $path for record ${schema.fullName}",
                )
            }
            record.put(field.pos(), value)
        }
        return record
    }

    private fun unionDatum(node: Node, schema: Schema, path: String): Any? {
        var lastError: AvroException? = null
        for (branch in schema.types) {
            try {
                return toDatum(node, branch, path)
            } catch (e: AvroException) {
                lastError = e
            }
        }
        throw AvroException(
            "Cannot write ${node.type} at $path as any branch of Avro union (${lastError?.message})",
            lastError,
        )
    }

    private fun rejectUnknownFields(keys: Set<String>, schema: Schema, path: String) {
        val declared = HashSet<String>(schema.fields.size)
        for (field in schema.fields) {
            declared.add(field.name())
        }
        for (key in keys) {
            if (key !in declared) {
                throw AvroException("Unknown field '$key' at $path for Avro record ${schema.fullName}")
            }
        }
    }

    private fun defaultDatum(field: Schema.Field, path: String): Any? = try {
        GenericData.get().getDefaultValue(field)
    } catch (e: Exception) {
        throw AvroException("Invalid default value for Avro field '${field.name()}' at $path", e)
    }

    private fun allowsNull(schema: Schema): Boolean = schema.type == Schema.Type.UNION &&
        schema.types.any { it.type == Schema.Type.NULL }

    private fun numberNode(node: Node, path: String): NumberNode = node.asNumberOrNull()
        ?: throw AvroException("Expected number at $path but was ${node.type}")

    private fun binaryBytes(node: Node, path: String): ByteArray = when {
        node.isBinary -> node.asBinary()
        node.isString -> base64Bytes(node.asString(), path)
        else -> throw AvroException("Expected binary or Base64 string at $path but was ${node.type}")
    }

    private fun base64Bytes(value: String, path: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        throw AvroException("Invalid Base64 string at $path", e)
    }
}
