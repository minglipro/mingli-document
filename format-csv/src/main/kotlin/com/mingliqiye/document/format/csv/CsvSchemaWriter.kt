package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType

/**
 * 按 [CsvSchema] 将文档树写成 CSV 文本。
 *
 * 列顺序与表头由 Schema 决定；行中未声明的顶层列默认忽略，是否报错由
 * [CsvSchema.failOnUnknownColumns] 控制。写出规则见 [CsvSchema]。
 *
 * 写出格式选项来自 Schema：列分隔符（[CsvSchema.columnSeparator]，未配置时用 [delimiter]）、
 * 引号/转义字符、行分隔符、null 文本与注释；上游实现 风格写出特性由 [writeFeatures] 控制。
 *
 * 嵌套对象字段按子 Schema 递归展开为点分列名；未声明子 Schema 的 `OBJECT` / `ARRAY`
 * 字段以 JSON 文本单元格写出，需要 [nestedMode] 为 [CsvNestedMode.JSON]；
 * 带元素 Schema 的数组按元素 Schema 规范化后写成 JSON 数组；配置
 * [CsvSchema.arrayElementSeparator] 后改为分隔文本数组。
 *
 * @param schema CSV Schema
 * @param delimiter 字段分隔符（Schema 未配置列分隔符时使用）
 * @param nestedMode 嵌套值读写方式；默认 `null` 表示不支持通用嵌套
 * @param maxDepth 写出时嵌套深度的上限（用于限制 JSON 文本单元格内部嵌套）；
 *   `0` 或负数表示不限制
 * @param writeFeatures 写出特性集合，默认 [CsvConfig.DEFAULT_WRITE_FEATURES]
 */
public class CsvSchemaWriter(
    private val schema: CsvSchema,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val nestedMode: CsvNestedMode? = null,
    private val maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
    private val writeFeatures: Set<CsvWriteFeature> = CsvConfig.DEFAULT_WRITE_FEATURES,
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
        val encoder = CsvEncoder(
            schema.effectiveColumnSeparator(delimiter),
            schema.quoteChar,
            schema.escapeChar,
            schema.lineSeparator,
            schema.nullValue,
            schema.allowComments,
            writeFeatures,
        )
        val dataRows = ArrayList<List<CsvCell>>(rows.size)
        for (row in rows) {
            dataRows.add(cellsOf(row))
        }
        return try {
            encoder.write(if (schema.header) schema.columnNames else null, dataRows)
        } catch (e: CsvException) {
            throw e
        } catch (e: Exception) {
            throw CsvException("Cannot write CSV: ${e.message}", e)
        }
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

    private fun cellsOf(row: ObjectNode): List<CsvCell> {
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
        val cells = ArrayList<CsvCell>(schema.columnNames.size)
        appendObjectCells(schema, row, present = true, cells, depth = 0)
        return cells
    }

    /**
     * 按 Schema 字段顺序追加一行（或嵌套对象）的单元格。
     *
     * @param schema 当前层 Schema
     * @param source 当前层对象节点；`null` 表示父对象缺席
     * @param present 当前层对象是否真实存在，false 时跳过必填校验
     * @param cells 输出单元格列表
     * @param depth 当前对象相对行对象的嵌套层数（行对象为 `0`）
     */
    private fun appendObjectCells(
        schema: CsvSchema,
        source: ObjectNode?,
        present: Boolean,
        cells: MutableList<CsvCell>,
        depth: Int,
    ) {
        for (field in schema.fields) {
            val value = if (source == null) null else source[field.name]
            val nested = schema.nestedObjects[field.name]
            if (nested == null) {
                cells.add(cellOf(schema, field, value, present, depth))
                continue
            }
            when {
                value == null -> {
                    if (present && field.required) {
                        throw missingColumn(field.name, schema)
                    }
                    appendEmptyCells(nested, cells)
                }

                value is NullNode -> appendEmptyCells(nested, cells)
                value is ObjectNode -> appendObjectCells(nested, value, present = true, cells, depth = depth + 1)
                else -> throw CsvException(
                    "CSV column '${field.name}' must be an object but was ${value.type} for schema '${schema.name}'",
                )
            }
        }
    }

    private fun appendEmptyCells(schema: CsvSchema, cells: MutableList<CsvCell>) {
        for (column in schema.columns) {
            cells.add(CsvCell(null, CsvCellKind.MISSING))
        }
    }

    private fun cellOf(
        schema: CsvSchema,
        field: SchemaField,
        value: Node?,
        present: Boolean,
        depth: Int,
    ): CsvCell {
        if (value == null) {
            if (present && field.required) {
                throw missingColumn(field.name, schema)
            }
            return CsvCell(null, CsvCellKind.MISSING)
        }
        if (value is NullNode) {
            return CsvCell(null, CsvCellKind.NULL)
        }
        return when (field.type) {
            SchemaType.OBJECT -> CsvCell(jsonObjectCell(schema, field, value, cellNestingLimit(depth)), CsvCellKind.STRING)
            SchemaType.ARRAY -> CsvCell(jsonArrayCell(schema, field, value, cellNestingLimit(depth)), CsvCellKind.STRING)
            SchemaType.STRING -> CsvCell(scalarCell(schema, field, value), CsvCellKind.STRING)
            SchemaType.INT, SchemaType.LONG, SchemaType.DOUBLE, SchemaType.DECIMAL ->
                CsvCell(scalarCell(schema, field, value), CsvCellKind.NUMBER)

            SchemaType.BOOLEAN -> CsvCell(scalarCell(schema, field, value), CsvCellKind.BOOLEAN)
            SchemaType.BYTES -> CsvCell(scalarCell(schema, field, value), CsvCellKind.BINARY)
            SchemaType.MAP, SchemaType.NULL -> throw CsvException(
                "CSV column '${field.name}' does not support schema type ${field.type}",
            )
        }
    }

    /**
     * JSON 单元格相对嵌套上限：单元格根在整体树中深度为 `3 + depth`，可用相对深度为
     * `maxDepth - 2 - depth`；`0` 或负数表示不限制。
     */
    private fun cellNestingLimit(depth: Int): Int = if (maxDepth > 0) maxDepth - 2 - depth else 0

    private fun scalarCell(schema: CsvSchema, field: SchemaField, value: Node): String = try {
        CsvCells.textOf(value)
    } catch (e: IllegalArgumentException) {
        throw CsvException(
            "Cannot write CSV column '${field.name}' for schema '${schema.name}': ${e.message}",
            e,
        )
    }

    private fun jsonObjectCell(schema: CsvSchema, field: SchemaField, value: Node, cellLimit: Int): String {
        if (nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(schema, field, SchemaType.OBJECT)
        }
        val objectNode = value.asObjectOrNull() ?: throw CsvException(
            "CSV column '${field.name}' must be an object but was ${value.type} for schema '${schema.name}'",
        )
        return CsvJsonCodec.write(objectNode, cellLimit)
    }

    private fun jsonArrayCell(schema: CsvSchema, field: SchemaField, value: Node, cellLimit: Int): String {
        val element = schema.nestedArrays[field.name]
        val arraySeparator = schema.arrayElementSeparator
        if (arraySeparator != null) {
            return delimitedArrayCell(schema, field, value, element, arraySeparator)
        }
        if (element == null && nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(schema, field, SchemaType.ARRAY)
        }
        val array = value.asArrayOrNull() ?: throw CsvException(
            "CSV column '${field.name}' must be an array but was ${value.type} for schema '${schema.name}'",
        )
        if (element == null) {
            return CsvJsonCodec.write(array, cellLimit)
        }
        if (maxDepth > 0 && cellLimit < 2) {
            throw CsvException(
                "CSV column '${field.name}' array element depth exceeds maxDepth $maxDepth for schema '${schema.name}'",
            )
        }
        val elements = ArrayList<Node>(array.size)
        for (index in 0 until array.size) {
            elements.add(
                CsvSchemaCoercion.coerceObject(element, array[index], "column '${field.name}' element [$index]") { message ->
                    throw CsvException(message)
                },
            )
        }
        return CsvJsonCodec.write(ArrayNode.from(elements), cellLimit)
    }

    /**
     * 上游实现 风格分隔文本数组：元素按元素 Schema 校验后以 [separator] 连接。
     */
    private fun delimitedArrayCell(
        schema: CsvSchema,
        field: SchemaField,
        value: Node,
        element: CsvSchema?,
        separator: String,
    ): String {
        val array = value.asArrayOrNull() ?: throw CsvException(
            "CSV column '${field.name}' must be an array but was ${value.type} for schema '${schema.name}'",
        )
        if (array.size == 0) {
            return ""
        }
        if (element != null && element.fields.size != 1) {
            throw CsvException(
                "CSV column '${field.name}' with arrayElementSeparator requires a single-field element schema",
            )
        }
        val elementField = element?.fields?.get(0)
        val texts = ArrayList<String>(array.size)
        for (index in 0 until array.size) {
            val item = array[index]
            if (elementField != null) {
                if (elementField.type == SchemaType.OBJECT || elementField.type == SchemaType.ARRAY) {
                    throw CsvException(
                        "CSV column '${field.name}' with arrayElementSeparator requires scalar element type " +
                            "but was ${elementField.type}",
                    )
                }
                texts.add(
                    try {
                        CsvCells.textOf(item)
                    } catch (e: IllegalArgumentException) {
                        throw CsvException(
                            "Cannot write CSV column '${field.name}' element [$index] for schema " +
                                "'${schema.name}': ${e.message}",
                            e,
                        )
                    },
                )
            } else {
                if (item is ObjectNode || item is ArrayNode) {
                    throw CsvException(
                        "CSV column '${field.name}' with arrayElementSeparator does not support nested element " +
                            "[$index] for schema '${schema.name}'",
                    )
                }
                texts.add(CsvCells.textOf(item))
            }
        }
        return texts.joinToString(separator)
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
 * @param delimiter 字段分隔符（Schema 未配置列分隔符时使用）
 * @param nestedMode 嵌套值读写方式
 * @param maxDepth 写出时 JSON 文本单元格内允许的最大嵌套深度，默认
 *   [CsvConfig.DEFAULT_MAX_DEPTH]
 * @param writeFeatures 写出特性集合，默认 [CsvConfig.DEFAULT_WRITE_FEATURES]
 * @return CSV 文本
 * @throws CsvException 根节点类型错误、缺少必填列、存在未声明列或单元格不是标量时抛出
 */
public fun Node.toCsvString(
    schema: CsvSchema,
    delimiter: Char = DEFAULT_CSV_DELIMITER,
    nestedMode: CsvNestedMode? = null,
    maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
    writeFeatures: Set<CsvWriteFeature> = CsvConfig.DEFAULT_WRITE_FEATURES,
): String = CsvSchemaWriter(schema, delimiter, nestedMode, maxDepth, writeFeatures).write(this)
