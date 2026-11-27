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
 * 将文档树写成 CSV 文本。
 *
 * 根节点可以是对象（单行）或数组（每个元素为一行对象）；表头取所有行字段名的并集，
 * 保持字段首次出现的顺序。单元格只支持标量值，嵌套对象/数组会抛出 [CsvException]。
 *
 * @param delimiter 字段分隔符
 * @param header 是否写出表头
 */
internal class CsvWriter(
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val header: Boolean = DEFAULT_CSV_HEADER,
) {

    /**
     * 写出 CSV 文本。
     *
     * @param node 文档树
     * @return CSV 文本
     * @throws CsvException 根节点类型错误或单元格不是标量时抛出
     */
    fun write(node: Node): String {
        val rows = rowsOf(node)
        val columns = columnsOf(rows)
        if (columns.isEmpty()) {
            return ""
        }
        val text = StringWriter()
        try {
            val format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setRecordSeparator("\n")
                .build()
            CSVPrinter(text, format).use { printer ->
                if (header) {
                    printer.printRecord(columns)
                }
                for (row in rows) {
                    val values = ArrayList<String>(columns.size)
                    for (column in columns) {
                        values.add(cell(row[column]))
                    }
                    printer.printRecord(values)
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

    private fun columnsOf(rows: List<ObjectNode>): List<String> {
        val columns = LinkedHashSet<String>()
        for (row in rows) {
            columns.addAll(row.keys)
        }
        return ArrayList(columns)
    }

    private fun cell(node: Node?): String = when (node) {
        null -> ""
        is NullNode -> ""
        is StringNode -> node.value
        is NumberNode -> node.raw
        is BooleanNode -> node.value.toString()
        is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
        is ObjectNode, is ArrayNode -> throw CsvException("Nested CSV cell values are not supported: ${node.type}")
    }
}

/**
 * 将文档树写成 CSV 字符串。
 *
 * @param delimiter 字段分隔符
 * @param header 是否写出表头
 * @return CSV 文本
 * @throws CsvException 根节点类型错误或单元格不是标量时抛出
 */
public fun Node.toCsvString(
    delimiter: Char = DEFAULT_CSV_DELIMITER,
    header: Boolean = DEFAULT_CSV_HEADER,
): String = CsvWriter(delimiter, header).write(this)
