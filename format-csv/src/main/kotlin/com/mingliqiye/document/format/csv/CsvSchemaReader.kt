package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal
import java.util.Base64

/**
 * 按 [CsvSchema] 读取 CSV 文本。
 *
 * 列由 Schema 决定：有表头（[CsvSchema.header] 为 true）时按列名映射，无表头时按
 * Schema 字段顺序对应。单元格按字段的 [SchemaType] 解析。解析规则见 [CsvSchema]。
 *
 * @param source CSV 文本
 * @param schema CSV Schema
 * @param delimiter 字段分隔符
 */
public class CsvSchemaReader(
    private val source: String,
    private val schema: CsvSchema,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
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
        val columns = if (schema.header) readHeader(records.next()) else null
        val rows = ArrayList<Node>()
        while (records.hasNext()) {
            rows.add(readRow(records.next(), columns, rows.size))
        }
        return ArrayNode.from(rows)
    }

    private fun readHeader(record: CSVRecord): List<SchemaField?> {
        val seen = HashSet<String>(record.size())
        val columns = ArrayList<SchemaField?>(record.size())
        val present = HashSet<String>(schema.fields.size)
        for (index in 0 until record.size()) {
            val column = record.get(index)
            if (!seen.add(column)) {
                throw CsvParseException("Duplicate CSV header '$column'")
            }
            val field = schema.field(column)
            if (field == null) {
                if (schema.failOnUnknownColumns) {
                    throw CsvParseException("Unknown CSV column '$column' for schema '${schema.name}'")
                }
                columns.add(null)
            } else {
                columns.add(field)
                present.add(column)
            }
        }
        for (field in schema.fields) {
            if (field.required && field.name !in present) {
                throw CsvParseException(
                    "Missing required CSV column '${field.name}' for schema '${schema.name}'",
                )
            }
        }
        return columns
    }

    private fun readRow(record: CSVRecord, columns: List<SchemaField?>?, rowIndex: Int): Node {
        val fields = LinkedHashMap<String, Node>(schema.fields.size)
        if (columns == null) {
            readPositional(record, fields, rowIndex)
        } else {
            readNamed(record, columns, fields, rowIndex)
        }
        return ObjectNode.from(fields)
    }

    private fun readNamed(
        record: CSVRecord,
        columns: List<SchemaField?>,
        fields: LinkedHashMap<String, Node>,
        rowIndex: Int,
    ) {
        if (schema.failOnUnknownColumns && record.size() > columns.size) {
            throw CsvParseException(
                "CSV record ${rowIndex + 1} has more columns than the header for schema '${schema.name}'",
            )
        }
        for (index in columns.indices) {
            val field = columns[index] ?: continue
            val text = if (index < record.size()) record.get(index) else ""
            fields[field.name] = parseCell(field, text, rowIndex)
        }
    }

    private fun readPositional(
        record: CSVRecord,
        fields: LinkedHashMap<String, Node>,
        rowIndex: Int,
    ) {
        val declared = schema.fields
        if (schema.failOnUnknownColumns && record.size() > declared.size) {
            throw CsvParseException(
                "CSV record ${rowIndex + 1} has ${record.size()} columns but schema " +
                    "'${schema.name}' declares ${declared.size}",
            )
        }
        val size = minOf(record.size(), declared.size)
        for (index in 0 until size) {
            val field = declared[index]
            fields[field.name] = parseCell(field, record.get(index), rowIndex)
        }
        for (index in size until declared.size) {
            val field = declared[index]
            if (field.required) {
                throw CsvParseException(
                    "Missing required CSV column '${field.name}' in record ${rowIndex + 1}",
                )
            }
        }
    }

    private fun parseCell(field: SchemaField, text: String, rowIndex: Int): Node {
        if (text.isEmpty()) {
            return when {
                field.nullable -> NullNode.INSTANCE
                field.type == SchemaType.STRING -> StringNode.of("")
                field.required -> throw CsvParseException(
                    "Required CSV column '${field.name}' is empty in record ${rowIndex + 1}",
                )

                else -> NullNode.INSTANCE
            }
        }
        return when (field.type) {
            SchemaType.STRING -> StringNode.of(text)
            SchemaType.INT -> parseInteger(field, text, rowIndex)
            SchemaType.LONG -> parseLong(field, text, rowIndex)
            SchemaType.DOUBLE -> parseDouble(field, text, rowIndex)
            SchemaType.DECIMAL -> parseDecimal(field, text, rowIndex)
            SchemaType.BOOLEAN -> parseBoolean(field, text, rowIndex)
            SchemaType.BYTES -> parseBytes(field, text, rowIndex)
            else -> throw CsvParseException(
                "CSV column '${field.name}' does not support schema type ${field.type}",
            )
        }
    }

    private fun parseInteger(field: SchemaField, text: String, rowIndex: Int): Node {
        if (text.toIntOrNull() == null) {
            throw invalidValue(field, text, rowIndex, SchemaType.INT)
        }
        return NumberNode.ofRaw(text)
    }

    private fun parseLong(field: SchemaField, text: String, rowIndex: Int): Node {
        if (text.toLongOrNull() == null) {
            throw invalidValue(field, text, rowIndex, SchemaType.LONG)
        }
        return NumberNode.ofRaw(text)
    }

    private fun parseDouble(field: SchemaField, text: String, rowIndex: Int): Node {
        val value = text.toDoubleOrNull()
        if (value == null || !value.isFinite()) {
            throw invalidValue(field, text, rowIndex, SchemaType.DOUBLE)
        }
        return NumberNode.ofRaw(text)
    }

    private fun parseDecimal(field: SchemaField, text: String, rowIndex: Int): Node {
        try {
            BigDecimal(text)
        } catch (_: NumberFormatException) {
            throw invalidValue(field, text, rowIndex, SchemaType.DECIMAL)
        }
        return NumberNode.ofRaw(text)
    }

    private fun parseBoolean(field: SchemaField, text: String, rowIndex: Int): Node = when {
        text.equals("true", ignoreCase = true) -> BooleanNode.of(true)
        text.equals("false", ignoreCase = true) -> BooleanNode.of(false)
        else -> throw invalidValue(field, text, rowIndex, SchemaType.BOOLEAN)
    }

    private fun parseBytes(field: SchemaField, text: String, rowIndex: Int): Node {
        val bytes = try {
            Base64.getDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            throw invalidValue(field, text, rowIndex, SchemaType.BYTES)
        }
        return BinaryNode.of(bytes)
    }

    private fun invalidValue(
        field: SchemaField,
        text: String,
        rowIndex: Int,
        type: SchemaType,
    ): CsvParseException = CsvParseException(
        "Cannot parse '$text' as $type for column '${field.name}' in record ${rowIndex + 1}",
    )

    public companion object {

        /**
         * 按 Schema 解析 CSV 字符串。
         *
         * @param source CSV 文本
         * @param schema CSV Schema
         * @param delimiter 字段分隔符
         * @return 文档树
         * @throws CsvParseException CSV 格式非法、列缺失或单元格无法按 Schema 类型解析时抛出
         */
        @JvmStatic
        public fun parse(
            source: String,
            schema: CsvSchema,
            delimiter: Char = DEFAULT_CSV_DELIMITER,
        ): Node = CsvSchemaReader(source, schema, delimiter).read()
    }
}
