package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord

/**
 * 按 [CsvSchema] 读取 CSV 文本。
 *
 * 列由 Schema 决定：有表头（[CsvSchema.header] 为 true）时按列名映射，无表头时按
 * Schema 展开后的列顺序对应。单元格按字段的 [SchemaType] 解析。解析规则见 [CsvSchema]。
 *
 * 嵌套对象字段按子 Schema 递归读取：`address.city` 这类点分列会组装为嵌套对象。
 * 未声明子 Schema 的 `OBJECT` / `ARRAY` 字段（以及数组元素 Schema）以 JSON 文本单元格
 * 读写，需要 [nestedMode] 为 [CsvNestedMode.JSON] 或字段声明了元素 Schema。
 *
 * @param source CSV 文本
 * @param schema CSV Schema
 * @param delimiter 字段分隔符
 * @param nestedMode 嵌套值读写方式；默认 `null` 表示不支持通用嵌套
 */
public class CsvSchemaReader(
    private val source: String,
    private val schema: CsvSchema,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val nestedMode: CsvNestedMode? = null,
) {

    /**
     * 解析并返回文档树。
     *
     * 根节点固定为数组，每个元素对应一行对象；空文本返回空数组。
     *
     * @return 文档树
     * @throws CsvParseException CSV 格式非法、列缺失或单元格无法按 Schema 类型解析时抛出
     */
    public fun read(): Node = try {
        CSVParser.parse(source, format()).use { parser -> readRecords(parser) }
    } catch (e: CsvException) {
        throw e
    } catch (e: Exception) {
        throw CsvParseException("Invalid CSV: ${e.message}", e)
    }

    private fun format(): CSVFormat = CSVFormat.DEFAULT.builder()
        .setDelimiter(delimiter)
        .build()

    private fun readRecords(parser: CSVParser): Node {
        val records = parser.iterator()
        if (!records.hasNext()) {
            return ArrayNode.EMPTY
        }
        val layout = if (schema.header) readHeader(records.next()) else positionalLayout()
        val rows = ArrayList<Node>()
        while (records.hasNext()) {
            rows.add(readRow(records.next(), layout, rows.size))
        }
        return ArrayNode.from(rows)
    }

    private fun positionalLayout(): RowLayout {
        val indexes = LinkedHashMap<List<String>, Int>(schema.columns.size)
        for ((index, column) in schema.columns.withIndex()) {
            indexes[column.path] = index
        }
        return RowLayout(indexes, null, named = false)
    }

    private fun readHeader(record: CSVRecord): RowLayout {
        val indexes = LinkedHashMap<List<String>, Int>(record.size())
        val present = HashSet<String>(record.size())
        val seen = HashSet<String>(record.size())
        for (index in 0 until record.size()) {
            val column = record.get(index)
            if (!seen.add(column)) {
                throw CsvParseException("Duplicate CSV header '$column'")
            }
            present.add(column)
            val spec = schema.column(column)
            if (spec == null) {
                if (schema.failOnUnknownColumns) {
                    throw CsvParseException("Unknown CSV column '$column' for schema '${schema.name}'")
                }
            } else {
                indexes[spec.path] = index
            }
        }
        validateRequiredColumns(schema, emptyList(), present, active = true)
        return RowLayout(indexes, record.size(), named = true)
    }

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

    private fun readRow(record: CSVRecord, layout: RowLayout, rowIndex: Int): Node {
        if (layout.named && schema.failOnUnknownColumns && layout.size != null && record.size() > layout.size) {
            throw CsvParseException(
                "CSV record ${rowIndex + 1} has more columns than the header for schema '${schema.name}'",
            )
        }
        if (!layout.named && schema.failOnUnknownColumns && record.size() > schema.columns.size) {
            throw CsvParseException(
                "CSV record ${rowIndex + 1} has ${record.size()} columns but schema " +
                    "'${schema.name}' declares ${schema.columns.size}",
            )
        }
        val cells = RowCells(layout.indexes, record, layout.named)
        return readObject(schema, emptyList(), cells, rowIndex)
    }

    private fun readObject(
        schema: CsvSchema,
        prefix: List<String>,
        cells: RowCells,
        rowIndex: Int,
    ): ObjectNode {
        val fields = LinkedHashMap<String, Node>(schema.fields.size)
        for (field in schema.fields) {
            val path = prefix + field.name
            val nested = schema.nestedObjects[field.name]
            if (nested != null) {
                if (!hasAnyColumn(nested, path, cells)) {
                    if (field.required) {
                        throw missingColumn(path, rowIndex)
                    }
                    continue
                }
                if (!hasAnyNonEmptyCell(nested, path, cells)) {
                    if (field.required) {
                        throw missingColumn(path, rowIndex)
                    }
                    continue
                }
                fields[field.name] = readObject(nested, path, cells, rowIndex)
                continue
            }
            if (!cells.present(path)) {
                if (field.required) {
                    throw missingColumn(path, rowIndex)
                }
                continue
            }
            fields[field.name] = parseCell(field, schema.nestedArrays[field.name], cells.text(path), rowIndex, path)
        }
        return ObjectNode.from(fields)
    }

    private fun hasAnyColumn(schema: CsvSchema, prefix: List<String>, cells: RowCells): Boolean {
        for (column in schema.columns) {
            if (cells.present(prefix + column.path)) {
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
            if (cells.present(path) && cells.text(path).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun parseCell(
        field: SchemaField,
        element: CsvSchema?,
        text: String,
        rowIndex: Int,
        path: List<String>,
    ): Node {
        val column = path.joinToString(".")
        if (text.isNotEmpty()) {
            when (field.type) {
                SchemaType.OBJECT -> return parseObjectCell(field, text, rowIndex, column)
                SchemaType.ARRAY -> return parseArrayCell(field, element, text, rowIndex, column)
                else -> Unit
            }
        }
        return try {
            CsvCells.convert(field, text)
        } catch (e: IllegalArgumentException) {
            throw CsvParseException("${e.message} in record ${rowIndex + 1}", e)
        }
    }

    private fun parseObjectCell(field: SchemaField, text: String, rowIndex: Int, column: String): Node {
        if (nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(field, SchemaType.OBJECT)
        }
        val node = parseJsonCell(text, column, rowIndex)
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
    ): Node {
        if (element == null && nestedMode != CsvNestedMode.JSON) {
            throw unsupportedNested(field, SchemaType.ARRAY)
        }
        val node = parseJsonCell(text, column, rowIndex)
        val array = node.asArrayOrNull() ?: throw CsvParseException(
            "CSV column '$column' in record ${rowIndex + 1} must be a JSON array but was ${node.type}",
        )
        if (element == null) {
            return array
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

    private fun parseJsonCell(text: String, column: String, rowIndex: Int): Node = try {
        CsvJsonCodec.parse(text)
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
        private val record: CSVRecord,
        private val named: Boolean,
    ) {

        fun present(path: List<String>): Boolean {
            val index = indexes[path] ?: return false
            return named || index < record.size()
        }

        fun text(path: List<String>): String {
            val index = indexes[path] ?: return ""
            return if (index < record.size()) record.get(index) else ""
        }
    }

    public companion object {

        /**
         * 按 Schema 解析 CSV 字符串。
         *
         * @param source CSV 文本
         * @param schema CSV Schema
         * @param delimiter 字段分隔符
         * @param nestedMode 嵌套值读写方式
         * @return 文档树
         * @throws CsvParseException CSV 格式非法、列缺失或单元格无法按 Schema 类型解析时抛出
         */
        @JvmStatic
        public fun parse(
            source: String,
            schema: CsvSchema,
            delimiter: Char = DEFAULT_CSV_DELIMITER,
            nestedMode: CsvNestedMode? = null,
        ): Node = CsvSchemaReader(source, schema, delimiter, nestedMode).read()
    }
}
