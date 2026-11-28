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
import java.math.BigDecimal
import java.util.Base64

/**
 * CSV 单元格与节点之间的基础转换。
 *
 * 读取与写出共用同一套标量规则；失败时抛出 [IllegalArgumentException]，由调用方按场景
 * 包装为 [com.mingliqiye.document.format.csv.exception.CsvParseException]（读取）或
 * [com.mingliqiye.document.format.csv.exception.CsvException]（写出）。
 */
internal object CsvCells {

    /**
     * 按字段类型把单元格文本解析为节点。
     *
     * @param field Schema 字段
     * @param text 单元格文本
     * @return 节点
     * @throws IllegalArgumentException 单元格无法按字段类型解析，或必填单元格为空时抛出
     */
    fun convert(field: SchemaField, text: String): Node {
        if (text.isEmpty()) {
            return when {
                field.nullable -> NullNode.INSTANCE
                field.type == SchemaType.STRING -> StringNode.of("")
                field.required -> throw IllegalArgumentException(
                    "Required CSV column '${field.name}' is empty",
                )

                else -> NullNode.INSTANCE
            }
        }
        return when (field.type) {
            SchemaType.STRING -> StringNode.of(text)
            SchemaType.INT -> if (text.toIntOrNull() == null) {
                invalidType(field, text, SchemaType.INT)
            } else {
                NumberNode.ofRaw(text)
            }

            SchemaType.LONG -> if (text.toLongOrNull() == null) {
                invalidType(field, text, SchemaType.LONG)
            } else {
                NumberNode.ofRaw(text)
            }

            SchemaType.DOUBLE -> {
                val value = text.toDoubleOrNull()
                if (value == null || !value.isFinite()) {
                    invalidType(field, text, SchemaType.DOUBLE)
                } else {
                    NumberNode.ofRaw(text)
                }
            }

            SchemaType.DECIMAL -> try {
                BigDecimal(text)
                NumberNode.ofRaw(text)
            } catch (_: NumberFormatException) {
                invalidType(field, text, SchemaType.DECIMAL)
            }

            SchemaType.BOOLEAN -> when {
                text.equals("true", ignoreCase = true) -> BooleanNode.of(true)
                text.equals("false", ignoreCase = true) -> BooleanNode.of(false)
                else -> invalidType(field, text, SchemaType.BOOLEAN)
            }

            SchemaType.BYTES -> try {
                BinaryNode.of(Base64.getDecoder().decode(text))
            } catch (_: IllegalArgumentException) {
                invalidType(field, text, SchemaType.BYTES)
            }

            SchemaType.OBJECT, SchemaType.ARRAY, SchemaType.MAP, SchemaType.NULL -> throw IllegalArgumentException(
                "CSV column '${field.name}' does not support schema type ${field.type}",
            )
        }
    }

    /**
     * 把节点转换为单元格文本。
     *
     * @param node 节点
     * @return 单元格文本
     * @throws IllegalArgumentException 节点不是标量时抛出
     */
    fun textOf(node: Node): String = when (node) {
        is NullNode -> ""
        is StringNode -> node.value
        is NumberNode -> node.raw
        is BooleanNode -> node.value.toString()
        is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
        is ObjectNode, is ArrayNode -> throw IllegalArgumentException(
            "Nested CSV cell values are not supported: ${node.type}",
        )
    }

    private fun invalidType(field: SchemaField, text: String, type: SchemaType): Nothing = throw IllegalArgumentException(
        "Cannot parse '$text' as $type for column '${field.name}'",
    )
}
