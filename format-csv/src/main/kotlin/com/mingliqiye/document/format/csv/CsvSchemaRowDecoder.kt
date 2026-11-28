package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import org.apache.commons.csv.CSVRecord

/**
 * 按 [CsvSchema] 解码单行 CSV（内部实现）。
 *
 * [CsvSchemaReader] 与流式 `CsvMapper.parseStream` 共用本类，保证列布局、必填列、
 * 嵌套 Schema、类型转换与 上游实现 读取特性在整段解析与逐行解析两条路径上完全一致。
 *
 * 使用顺序：有表头时先调用 [readHeader]；无表头时先调用 [positionalLayout]，
 * 再对每个数据行调用 [readRow]。
 *
 * @param schema CSV Schema
 * @param nestedMode 嵌套值读写方式；默认 `null` 表示不支持通用嵌套
 * @param maxDepth 解析最大嵌套深度（根数组为 `1`、行对象为 `2`、单元格标量为 `3`，
 *   嵌套对象/JSON 单元格继续累加）；`0` 或负数表示不限制
 * @param support 读取特性与 Schema 选项应用层
 */
internal class CsvSchemaRowDecoder(
    private val schema: CsvSchema,
    private val nestedMode: CsvNestedMode?,
    private val maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
    private val support: CsvReadSupport,
) {

    private var layout: RowLayout? = null

    private val caseInsensitiveColumns: Map<String, CsvFlatColumn> by lazy {
        if (support.caseInsensitiveHeaders) {
            schema.columns.associateBy { it.column.lowercase() }
        } else {
            emptyMap()
        }
    }

    /**
     * 读取并校验表头行，确定列名到 Schema 路径的映射。
     *
     * @param record 表头记录
     * @throws CsvParseException 表头重复、未知列（`failOnUnknownColumns`）或必填列缺失时抛出
     */
    fun readHeader(record: CSVRecord) {
        layout = readHeaderLayout(record)
    }

    /**
     * 使用无表头模式：按 Schema 展开后的列顺序对应数据列。
     */
    fun positionalLayout() {
        layout = positionalLayoutInternal()
    }

    /**
     * 解码一行数据为对象节点。
     *
     * @param record 数据行记录
     * @param rowIndex 数据行序号（从 0 开始，用于错误消息）
     * @return 行对象节点
     */
    fun readRow(record: CSVRecord, rowIndex: Int): Node {
        val rowLayout = layout ?: positionalLayoutInternal().also { layout = it }
        return readRowInternal(record, rowLayout, rowIndex)
    }

    private fun positionalLayoutInternal(): RowLayout {
        val indexes = LinkedHashMap<List<String>, Int>(schema.columns.size)
        for ((index, column) in schema.columns.withIndex()) {
            indexes[column.path] = index
        }
        return RowLayout(indexes, null, named = false)
    }

    private fun readHeaderLayout(record: CSVRecord): RowLayout {
        val headerNames = support.headerNames(record)
        val indexes = LinkedHashMap<List<String>, Int>(headerNames.size)
        val present = HashSet<String>(headerNames.size)
        for ((index, column) in headerNames.withIndex()) {
            val spec = schemaColumnFor(column)
            if (spec == null) {
                if (schema.failOnUnknownColumns && !support.ignoreTrailingUnmappable) {
                    throw CsvParseException("Unknown CSV column '$column' for schema '${schema.name}'")
                }
            } else {
                indexes[spec.path] = index
                present.add(spec.column)
            }
        }
        if (support.failOnMissingHeaderColumns) {
            validateRequiredColumns(schema, emptyList(), present, active = true)
        }
        return RowLayout(indexes, headerNames.size, named = true)
    }

    private fun schemaColumnFor(column: String): CsvFlatColumn? = schema.column(column) ?: caseInsensitiveColumns[column.lowercase()]

    /** 递归校验必填列：可选父对象整体缺席时，其内部必填列不报错。 */
    private fun validateRequiredColumns(schema: CsvSchema, prefix: List<String>, present: Set<String>, active: Boolean) {
        for (field in schema.fields) {
            val path = prefix + field.name
            val nested = schema.nestedObjects[field.name]
            if (nested != null) {
                val anyPresent = nested.expandColumns(path).any { it.column in present }
                validateRequiredColumns(nested, path, present, active = active && (field.required || anyPresent))
            } else if (active && field.required && path.joinToString(".") !in present) {
                throw CsvParseException(
                    "Missing required CSV column '${path.joinToString(".")}' for schema '${schema.name}'",
                )
            }
        }
    }

    private fun readRowInternal(record: CSVRecord, layout: RowLayout, rowIndex: Int): Node {
        val values = support.cells(record)
        if (support.failOnMissingColumns && values.size < schema.columns.size) {
            throw CsvParseException(
                "CSV record ${rowIndex + 1} has ${values.size} columns but schema " +
                    "'${schema.name}' declares ${schema.columns.size}",
            )
        }
        if (layout.named &&
            schema.failOnUnknownColumns &&
            !support.ignoreTrailingUnmappable &&
            layout.size != null &&
            values.size > layout.size
        ) {
            throw CsvParseException(
                "CSV record ${rowIndex + 1} has more columns than the header for schema '${schema.name}'",
            )
        }
        if (!layout.named &&
            schema.failOnUnknownColumns &&
            !support.ignoreTrailingUnmappable &&
            values.size > schema.columns.size
        ) {
            throw CsvParseException(
                "CSV record ${rowIndex + 1} has ${values.size} columns but schema " +
                    "'${schema.name}' declares ${schema.columns.size}",
            )
        }
        val cells = RowCells(layout.indexes, values, layout.named)
        return readObject(schema, emptyList(), cells, rowIndex)
    }

    private fun readObject(
        schema: CsvSchema,
        prefix: List<String>,
        cells: RowCells,
        rowIndex: Int,
    ): ObjectNode {
        checkDepth(2 + prefix.size, "CSV row object")
        val fields = LinkedHashMap<String, Node>(schema.fields.size)
        for (field in schema.fields) {
            val path = prefix + field.name
            val nested = schema.nestedObjects[field.name]
            if (nested != null) {
                val anyColumn = hasAnyColumn(nested, path, cells)
                val anyValue = anyColumn && hasAnyNonEmptyCell(nested, path, cells)
                if (!anyColumn || !anyValue) {
                    if (support.insertNullsForMissingColumns) {
                        fields[field.name] = nullFilledObject(nested)
                        continue
                    }
                    val anyMapped = hasAnyMappedColumn(nested, path, cells)
                    if (field.required && (anyMapped || support.failOnMissingHeaderColumns)) {
                        throw missingColumn(path, rowIndex)
                    }
                    continue
                }
                fields[field.name] = readObject(nested, path, cells, rowIndex)
                continue
            }
            if (!cells.present(path)) {
                if (support.insertNullsForMissingColumns) {
                    fields[field.name] = NullNode.INSTANCE
                    continue
                }
                if (field.required && (cells.mapped(path) || support.failOnMissingHeaderColumns)) {
                    throw missingColumn(path, rowIndex)
                }
                continue
            }
            if (support.insertNullsForMissingColumns && !cells.hasValue(path)) {
                fields[field.name] = NullNode.INSTANCE
                continue
            }
            checkDepth(3 + prefix.size, "CSV column '${path.joinToString(".")}'")
            val node = parseCell(field, schema.nestedArrays[field.name], cells.text(path), rowIndex, path) ?: continue
            fields[field.name] = node
        }
        return ObjectNode.from(fields)
    }

    /**
     * 构造“全部叶子为 null”的嵌套对象，用于 `INSERT_NULLS_FOR_MISSING_COLUMNS`。
     */
    private fun nullFilledObject(schema: CsvSchema): ObjectNode {
        val fields = LinkedHashMap<String, Node>(schema.fields.size)
        for (field in schema.fields) {
            val nested = schema.nestedObjects[field.name]
            fields[field.name] = if (nested != null) nullFilledObject(nested) else NullNode.INSTANCE
        }
        return ObjectNode.from(fields)
    }

    /**
     * 校验单个节点深度；超限抛 [CsvParseException]。
     */
    private fun checkDepth(depth: Int, what: String) {
        if (maxDepth > 0 && depth > maxDepth) {
            throw CsvParseException("$what depth $depth exceeds maxDepth $maxDepth")
        }
    }

    /**
     * JSON 单元格相对嵌套上限：单元格根节点在整体树中深度为 `3 + prefix.size`，
     * 因此可用相对深度为 `maxDepth - 2 - prefix.size`；`0` 或负数交给
     * [CsvJsonCodec] 表示不限制（此处单元格值深度已单独校验过）。
     */
    private fun cellNestingLimit(prefix: List<String>): Int = if (maxDepth > 0) maxDepth - 2 - prefix.size else 0

    private fun hasAnyColumn(schema: CsvSchema, prefix: List<String>, cells: RowCells): Boolean {
        for (column in schema.columns) {
            if (cells.present(prefix + column.path)) {
                return true
            }
        }
        return false
    }

    /** 布局（表头或位置）中是否存在嵌套对象的任一列。 */
    private fun hasAnyMappedColumn(schema: CsvSchema, prefix: List<String>, cells: RowCells): Boolean {
        for (column in schema.columns) {
            if (cells.mapped(prefix + column.path)) {
                return true
            }
        }
        return false
    }

    /**
     * 嵌套对象是否存在非空单元格。
     *
     * 可选嵌套对象整体为空时（例如写出时对象为 null 得到的空单元格），按对象缺席处理，
     * 从而支持写出后 round-trip。
     */
    private fun hasAnyNonEmptyCell(schema: CsvSchema, prefix: List<String>, cells: RowCells): Boolean {
        for (column in schema.columns) {
            val path = prefix + column.path
            if (cells.hasValue(path) && cells.text(path).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * 解析单个单元格；返回 `null` 表示该字段缺失（不写入行对象）。
     */
    private fun parseCell(
        field: SchemaField,
        element: CsvSchema?,
        text: String,
        rowIndex: Int,
        path: List<String>,
    ): Node? {
        val column = path.joinToString(".")
        val cellLimit = cellNestingLimit(path.dropLast(1))
        val arraySeparator = support.arrayElementSeparator()
        if (text.isNotEmpty() && support.isNullValueText(text)) {
            return NullNode.INSTANCE
        }
        if (text.isEmpty()) {
            if (field.type == SchemaType.ARRAY && arraySeparator != null) {
                return when (support.emptyCell()) {
                    CsvEmptyCell.MISSING -> null
                    CsvEmptyCell.NULL -> NullNode.INSTANCE
                    CsvEmptyCell.EMPTY -> ArrayNode.EMPTY
                }
            }
            when (support.emptyCell()) {
                CsvEmptyCell.MISSING -> return null
                CsvEmptyCell.NULL -> return NullNode.INSTANCE
                CsvEmptyCell.EMPTY -> Unit
            }
        }
        if (text.isNotEmpty() && field.type == SchemaType.ARRAY && arraySeparator != null) {
            return parseDelimitedArray(field, element, text, rowIndex, column)
        }
        if (text.isNotEmpty()) {
            when (field.type) {
                SchemaType.OBJECT -> return parseObjectCell(field, text, rowIndex, column, cellLimit)
                SchemaType.ARRAY -> return parseArrayCell(field, element, text, rowIndex, column, cellLimit)
                else -> Unit
            }
        }
        return try {
            CsvCells.convert(field, text)
        } catch (e: IllegalArgumentException) {
            throw CsvParseException("${e.message} in record ${rowIndex + 1}", e)
        }
    }

    /**
     * 解析 上游实现 风格的分隔文本数组（[CsvSchema.arrayElementSeparator] 非 null 时）。
     */
    private fun parseDelimitedArray(
        field: SchemaField,
        element: CsvSchema?,
        text: String,
        rowIndex: Int,
        column: String,
    ): Node {
        val parts = support.splitArray(text)
        val nodes = ArrayList<Node>(parts.size)
        if (element == null) {
            for (part in parts) {
                nodes.add(support.infer(part))
            }
            return ArrayNode.from(nodes)
        }
        if (element.fields.size != 1) {
            throw CsvParseException(
                "CSV column '$column' in record ${rowIndex + 1} with arrayElementSeparator " +
                    "requires a single-field element schema",
            )
        }
        val elementField = element.fields[0]
        if (elementField.type == SchemaType.OBJECT || elementField.type == SchemaType.ARRAY) {
            throw CsvParseException(
                "CSV column '$column' in record ${rowIndex + 1} with arrayElementSeparator " +
                    "requires scalar element type but was ${elementField.type}",
            )
        }
        for ((index, part) in parts.withIndex()) {
            val node = try {
                CsvCells.convert(elementField, part)
            } catch (e: IllegalArgumentException) {
                throw CsvParseException("${e.message} in record ${rowIndex + 1} column '$column' element [$index]", e)
            }
            nodes.add(node)
        }
        return ArrayNode.from(nodes)
    }

    private fun parseObjectCell(field: SchemaField, text: String, rowIndex: Int, column: String, cellLimit: Int): Node {
        if (nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(field, SchemaType.OBJECT)
        }
        val node = parseJsonCell(text, column, rowIndex, cellLimit)
        return node.asObjectOrNull() ?: throw CsvParseException(
            "CSV column '$column' in record ${rowIndex + 1} must be a JSON object but was ${node.type}",
        )
    }

    private fun parseArrayCell(
        field: SchemaField,
        element: CsvSchema?,
        text: String,
        rowIndex: Int,
        column: String,
        cellLimit: Int,
    ): Node {
        if (element == null && nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(field, SchemaType.ARRAY)
        }
        val node = parseJsonCell(text, column, rowIndex, cellLimit)
        val array = node.asArrayOrNull() ?: throw CsvParseException(
            "CSV column '$column' in record ${rowIndex + 1} must be a JSON array but was ${node.type}",
        )
        if (element == null) {
            return array
        }
        if (maxDepth > 0 && cellLimit < 2) {
            throw CsvParseException(
                "CSV column '$column' in record ${rowIndex + 1} array element depth exceeds maxDepth $maxDepth",
            )
        }
        val elements = ArrayList<Node>(array.size)
        for (index in 0 until array.size) {
            val elementContext = "column '$column' in record ${rowIndex + 1} element [$index]"
            elements.add(
                CsvSchemaCoercion.coerceObject(element, array[index], elementContext) { message ->
                    throw CsvParseException(message)
                },
            )
        }
        return ArrayNode.from(elements)
    }

    private fun parseJsonCell(text: String, column: String, rowIndex: Int, cellLimit: Int): Node = try {
        CsvJsonCodec.parse(text, cellLimit)
    } catch (e: CsvParseException) {
        throw CsvParseException(
            "Cannot parse JSON value for column '$column' in record ${rowIndex + 1}: ${e.message}",
            e,
        )
    }

    private fun unsupportedNested(field: SchemaField, type: SchemaType): CsvParseException {
        val hint = if (nestedMode == CsvNestedMode.COLUMNS) {
            "; declare a nested CsvSchema or use CsvNestedMode.JSON"
        } else {
            ""
        }
        return CsvParseException("CSV column '${field.name}' does not support schema type $type$hint")
    }

    private fun missingColumn(path: List<String>, rowIndex: Int): CsvParseException = CsvParseException(
        "Missing required CSV column '${path.joinToString(".")}' in record ${rowIndex + 1}",
    )

    /** 一行数据与 Schema 展开列的对应关系。 */
    private class RowLayout(
        val indexes: Map<List<String>, Int>,
        val size: Int?,
        val named: Boolean,
    )

    /** 从 CSV 记录中按 Schema 路径取单元格。 */
    private class RowCells(
        private val indexes: Map<List<String>, Int>,
        private val values: List<String>,
        private val named: Boolean,
    ) {

        fun present(path: List<String>): Boolean {
            val index = indexes[path] ?: return false
            return named || index < values.size
        }

        /** 该列是否出现在布局中（表头/位置映射），与当前行是否足够长无关。 */
        fun mapped(path: List<String>): Boolean = indexes.containsKey(path)

        fun hasValue(path: List<String>): Boolean {
            val index = indexes[path] ?: return false
            return index < values.size
        }

        fun text(path: List<String>): String {
            val index = indexes[path] ?: return ""
            return if (index < values.size) values[index] else ""
        }
    }
}
