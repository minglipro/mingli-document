package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.StringWriter

/**
 * 按 [CsvSchema] 将文档树写成 CSV 文本。
 *
 * 列顺序与表头由 Schema 决定；行中未声明的顶层列默认忽略，是否报错由
 * [CsvSchema.failOnUnknownColumns] 控制。写出规则见 [CsvSchema]。
 *
 * 嵌套对象字段按子 Schema 递归展开为点分列名；未声明子 Schema 的 `OBJECT` / `ARRAY`
 * 字段以 JSON 文本单元格写出，需要 [nestedMode] 为 [CsvNestedMode.JSON]；
 * 带元素 Schema 的数组始终按元素 Schema 规范化后写成 JSON 数组。
 *
 * @param schema CSV Schema
 * @param delimiter 字段分隔符
 * @param nestedMode 嵌套值读写方式；默认 `null` 表示不支持通用嵌套
 */
public class CsvSchemaWriter(
    private val schema: CsvSchema,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val nestedMode: CsvNestedMode? = null,
) {

    /**
     * 写出 CSV 文本。
     *
     * @param node 文档树
     * @return CSV 文本
     * @throws CsvException 根节点类型错误、缺少必填列、存在未声明列或单元格不是标量时抛出
     */
    public fun write(node: Node): String {
        val rows = rowsOf(node)
        val text = StringWriter()
        try {
            val format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setRecordSeparator("\n")
                .build()
            CSVPrinter(text, format).use { printer ->
                if (schema.header) {
                    printer.printRecord(schema.columnNames)
                }
                for (row in rows) {
                    printer.printRecord(cellsOf(row))
                }
            }
        } catch (e: CsvException) {
            throw e
        } catch (e: Exception) {
            throw CsvException("Cannot write CSV: ${e.message}", e)
        }
        return text.toString()
    }

    private fun rowsOf(node: Node): List<ObjectNode> = when (node) {
        is ObjectNode -> listOf(node)
        is ArrayNode -> {
            val rows = ArrayList<ObjectNode>(node.size)
            for (element in node) {
                rows.add(element.asObjectOrNull() ?: throw CsvException("CSV row must be an object but was ${element.type}"))
            }
            rows
        }

        else -> throw CsvException("CSV root must be an object or array but was ${node.type}")
    }

    private fun cellsOf(row: ObjectNode): List<String> {
        if (schema.failOnUnknownColumns) {
            val declared = HashSet<String>(schema.fields.size)
            for (field in schema.fields) {
                declared.add(field.name)
            }
            for (key in row.keys) {
                if (key !in declared) {
                    throw CsvException("Unknown CSV column '$key' for schema '${schema.name}'")
                }
            }
        }
        val values = ArrayList<String>(schema.columnNames.size)
        appendObjectCells(schema, row, present = true, values)
        return values
    }

    /**
     * 按 Schema 字段顺序追加一行（或嵌套对象）的单元格。
     *
     * @param schema 当前层 Schema
     * @param source 当前层对象节点；`null` 表示父对象缺席
     * @param present 当前层对象是否真实存在，false 时跳过必填校验
     * @param values 输出单元格列表
     */
    private fun appendObjectCells(
        schema: CsvSchema,
        source: ObjectNode?,
        present: Boolean,
        values: MutableList<String>,
    ) {
        for (field in schema.fields) {
            val value = if (source == null) null else source[field.name]
            val nested = schema.nestedObjects[field.name]
            if (nested == null) {
                values.add(cellOf(schema, field, value, present))
                continue
            }
            when {
                value == null -> {
                    if (present && field.required) {
                        throw missingColumn(field.name, schema)
                    }
                    appendEmptyCells(nested, values)
                }

                value is NullNode -> appendEmptyCells(nested, values)
                value is ObjectNode -> appendObjectCells(nested, value, present = true, values)
                else -> throw CsvException(
                    "CSV column '${field.name}' must be an object but was ${value.type} for schema '${schema.name}'",
                )
            }
        }
    }

    private fun appendEmptyCells(schema: CsvSchema, values: MutableList<String>) {
        for (column in schema.columns) {
            values.add("")
        }
    }

    private fun cellOf(
        schema: CsvSchema,
        field: SchemaField,
        value: Node?,
        present: Boolean,
    ): String {
        if (value == null) {
            if (present && field.required) {
                throw missingColumn(field.name, schema)
            }
            return ""
        }
        if (value is NullNode) {
            return ""
        }
        return when (field.type) {
            SchemaType.OBJECT -> jsonObjectCell(schema, field, value)
            SchemaType.ARRAY -> jsonArrayCell(schema, field, value)
            else -> scalarCell(schema, field, value)
        }
    }

    private fun scalarCell(schema: CsvSchema, field: SchemaField, value: Node): String = try {
        CsvCells.textOf(value)
    } catch (e: IllegalArgumentException) {
        throw CsvException(
            "Cannot write CSV column '${field.name}' for schema '${schema.name}': ${e.message}",
            e,
        )
    }

    private fun jsonObjectCell(schema: CsvSchema, field: SchemaField, value: Node): String {
        if (nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(schema, field, SchemaType.OBJECT)
        }
        val objectNode = value.asObjectOrNull() ?: throw CsvException(
            "CSV column '${field.name}' must be an object but was ${value.type} for schema '${schema.name}'",
        )
        return CsvJsonCodec.write(objectNode)
    }

    private fun jsonArrayCell(schema: CsvSchema, field: SchemaField, value: Node): String {
        val element = schema.nestedArrays[field.name]
        if (element == null && nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(schema, field, SchemaType.ARRAY)
        }
        val array = value.asArrayOrNull() ?: throw CsvException(
            "CSV column '${field.name}' must be an array but was ${value.type} for schema '${schema.name}'",
        )
        if (element == null) {
            return CsvJsonCodec.write(array)
        }
        val elements = ArrayList<Node>(array.size)
        for (index in 0 until array.size) {
            elements.add(
                CsvSchemaCoercion.coerceObject(element, array[index], "column '${field.name}' element [$index]") { message ->
                    throw CsvException(message)
                },
            )
        }
        return CsvJsonCodec.write(ArrayNode.from(elements))
    }

    private fun unsupportedNested(schema: CsvSchema, field: SchemaField, type: SchemaType): CsvException {
        val hint = if (nestedMode == CsvNestedMode.COLUMNS) {
            "; declare a nested CsvSchema or use CsvNestedMode.JSON"
        } else {
            ""
        }
        return CsvException(
            "CSV column '${field.name}' does not support schema type $type for schema '${schema.name}'$hint",
        )
    }

    private fun missingColumn(name: String, schema: CsvSchema): CsvException = CsvException(
        "Missing required CSV column '$name' for schema '${schema.name}'",
    )
}

/**
 * 按 [schema] 将文档树写成 CSV 字符串。
 *
 * @param schema CSV Schema
 * @param delimiter 字段分隔符
 * @param nestedMode 嵌套值读写方式
 * @return CSV 文本
 * @throws CsvException 根节点类型错误、缺少必填列、存在未声明列或单元格不是标量时抛出
 */
public fun Node.toCsvString(
    schema: CsvSchema,
    delimiter: Char = DEFAULT_CSV_DELIMITER,
    nestedMode: CsvNestedMode? = null,
): String = CsvSchemaWriter(schema, delimiter, nestedMode).write(this)
