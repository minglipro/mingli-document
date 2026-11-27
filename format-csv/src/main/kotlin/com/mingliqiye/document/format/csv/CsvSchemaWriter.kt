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
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.StringWriter
import java.util.Base64

/**
 * 按 [CsvSchema] 将文档树写成 CSV 文本。
 *
 * 列顺序与表头由 Schema 决定；行中未声明的列默认忽略，是否报错由
 * [CsvSchema.failOnUnknownColumns] 控制。写出规则见 [CsvSchema]。
 *
 * @param schema CSV Schema
 * @param delimiter 字段分隔符
 */
public class CsvSchemaWriter(
    private val schema: CsvSchema,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
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
        val values = ArrayList<String>(schema.fields.size)
        for (field in schema.fields) {
            val value = row[field.name]
            if (value == null) {
                if (field.required) {
                    throw CsvException(
                        "Missing required CSV column '${field.name}' for schema '${schema.name}'",
                    )
                }
                values.add("")
            } else {
                values.add(cell(value))
            }
        }
        return values
    }

    private fun cell(node: Node): String = when (node) {
        is NullNode -> ""
        is StringNode -> node.value
        is NumberNode -> node.raw
        is BooleanNode -> node.value.toString()
        is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
        is ObjectNode, is ArrayNode -> throw CsvException("Nested CSV cell values are not supported: ${node.type}")
    }
}

/**
 * 按 [schema] 将文档树写成 CSV 字符串。
 *
 * @param schema CSV Schema
 * @param delimiter 字段分隔符
 * @return CSV 文本
 * @throws CsvException 根节点类型错误、缺少必填列、存在未声明列或单元格不是标量时抛出
 */
public fun Node.toCsvString(
    schema: CsvSchema,
    delimiter: Char = DEFAULT_CSV_DELIMITER,
): String = CsvSchemaWriter(schema, delimiter).write(this)
