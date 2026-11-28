package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import java.util.Base64

/**
 * 按 [CsvSchema] 规范化嵌套 JSON 值。
 *
 * 供数组元素 Schema（以及嵌套 Schema 内部的 JSON 值）在读取与写出时共用：
 * - 按 Schema 字段顺序重排字段；
 * - 校验类型并统一为节点类型（数字/布尔/字符串/二进制）；
 * - 处理 null、必填与未知字段；
 * - 未声明子 Schema 的 `OBJECT` / `ARRAY` 字段保留原 JSON 值。
 */
internal object CsvSchemaCoercion {

    /**
     * 按 [schema] 规范化一个 JSON 对象节点。
     *
     * @param schema 目标 Schema
     * @param node 待处理节点
     * @param context 错误信息中的位置描述
     * @param fail 失败回调
     * @return 规范化后的对象节点
     */
    fun coerceObject(
        schema: CsvSchema,
        node: Node,
        context: String,
        fail: (String) -> Nothing,
    ): ObjectNode {
        val source = node.asObjectOrNull() ?: fail("$context must be an object but was ${node.type}")
        if (schema.failOnUnknownColumns) {
            for (key in source.keys) {
                if (schema.field(key) == null) {
                    fail("Unknown nested field '$key' for schema '${schema.name}' in $context")
                }
            }
        }
        val fields = LinkedHashMap<String, Node>(schema.fields.size)
        for (field in schema.fields) {
            val value = source[field.name]
            if (value == null || value is NullNode) {
                when {
                    field.nullable -> fields[field.name] = NullNode.INSTANCE
                    field.required -> fail("Missing required field '${field.name}' in $context")
                    else -> Unit
                }
                continue
            }
            val fieldContext = "$context.${field.name}"
            val nestedObject = schema.nestedObjects[field.name]
            val nestedArray = schema.nestedArrays[field.name]
            fields[field.name] = when {
                nestedObject != null -> coerceObject(nestedObject, value, fieldContext, fail)
                nestedArray != null -> coerceArray(nestedArray, value, fieldContext, fail)
                field.type == SchemaType.OBJECT && value is ObjectNode -> value
                field.type == SchemaType.ARRAY && value is ArrayNode -> value
                else -> coerceScalar(field, value, fieldContext, fail)
            }
        }
        return ObjectNode.from(fields)
    }

    private fun coerceArray(
        elementSchema: CsvSchema,
        node: Node,
        context: String,
        fail: (String) -> Nothing,
    ): Node {
        val array = node.asArrayOrNull() ?: fail("$context must be an array but was ${node.type}")
        val elements = ArrayList<Node>(array.size)
        for (index in 0 until array.size) {
            elements.add(coerceObject(elementSchema, array[index], "$context[$index]", fail))
        }
        return ArrayNode.from(elements)
    }

    private fun coerceScalar(
        field: SchemaField,
        node: Node,
        context: String,
        fail: (String) -> Nothing,
    ): Node {
        val text = when (node) {
            is StringNode -> node.value
            is NumberNode -> node.raw
            is BooleanNode -> node.value.toString()
            is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
            is NullNode -> ""
            is ObjectNode, is ArrayNode -> fail("$context must be a scalar but was ${node.type}")
        }
        return try {
            CsvCells.convert(field, text)
        } catch (e: IllegalArgumentException) {
            fail("${e.message} at $context")
        }
    }
}
