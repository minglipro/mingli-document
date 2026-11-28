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
 * 保持字段首次出现的顺序。
 *
 * [nestedMode] 控制嵌套单元格：
 * - [CsvNestedMode.JSON]：嵌套对象/数组写成 JSON 文本单元格；
 * - [CsvNestedMode.COLUMNS]：嵌套对象展开为点分列名，数组没有列形式会抛出 [CsvException]；
 * - `null`（默认）不支持嵌套，保持原有行为。
 *
 * @param delimiter 字段分隔符
 * @param header 是否写出表头
 * @param nestedMode 嵌套值读写方式
 * @param maxDepth 解析/写出最大嵌套深度（用于限制 JSON 文本单元格内部嵌套）；
 *   `0` 或负数表示不限制
 */
internal class CsvWriter(
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val header: Boolean = DEFAULT_CSV_HEADER,
    private val nestedMode: CsvNestedMode? = null,
    private val maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
) {

    /**
     * 写出 CSV 文本。
     *
     * @param node 文档树
     * @return CSV 文本
     * @throws CsvException 根节点类型错误、嵌套列冲突或单元格不是标量时抛出
     */
    fun write(node: Node): String {
        val text = StringWriter()
        write(node, text)
        return text.toString()
    }

    /**
     * 直接写出到字符流，不先构造完整 String。
     */
    fun write(node: Node, out: Appendable) {
        val rows = rowsOf(node)
        val flatRows = if (nestedMode == CsvNestedMode.COLUMNS) {
            rows.map { flattenRow(it) }
        } else {
            null
        }
        val columns = if (flatRows == null) columnsOf(rows) else flatColumnsOf(flatRows)
        if (columns.isEmpty()) {
            return
        }
        try {
            val format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setRecordSeparator("\n")
                .build()
            val printer = CSVPrinter(out, format)
            if (header) {
                printer.printRecord(columns)
            }
            for (index in rows.indices) {
                val values = ArrayList<String>(columns.size)
                if (flatRows == null) {
                    for (column in columns) {
                        values.add(cell(rows[index][column]))
                    }
                } else {
                    for (column in columns) {
                        values.add(cell(flatRows[index][column]))
                    }
                }
                printer.printRecord(values)
            }
            printer.flush()
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

    private fun columnsOf(rows: List<ObjectNode>): List<String> {
        val columns = LinkedHashSet<String>()
        for (row in rows) {
            columns.addAll(row.keys)
        }
        return ArrayList(columns)
    }

    private fun flatColumnsOf(rows: List<LinkedHashMap<String, Node>>): List<String> {
        val columns = LinkedHashSet<String>()
        for (row in rows) {
            columns.addAll(row.keys)
        }
        val list = ArrayList(columns)
        val lookup = HashSet(list)
        for (column in list) {
            var parent = column
            while (true) {
                val index = parent.lastIndexOf('.')
                if (index <= 0) {
                    break
                }
                parent = parent.substring(0, index)
                if (parent in lookup) {
                    throw CsvException("Conflicting nested CSV columns '$parent' and '$column'")
                }
            }
        }
        return list
    }

    private fun flattenRow(row: ObjectNode): LinkedHashMap<String, Node> {
        val columns = LinkedHashMap<String, Node>()
        flattenInto(row, "", columns)
        return columns
    }

    private fun flattenInto(objectNode: ObjectNode, prefix: String, columns: LinkedHashMap<String, Node>) {
        for ((key, value) in objectNode.entries()) {
            val column = if (prefix.isEmpty()) key else "$prefix.$key"
            when (value) {
                is ObjectNode -> flattenInto(value, column, columns)
                is ArrayNode -> throw CsvException(
                    "Nested CSV arrays are not supported in COLUMNS mode (column '$column'); use CsvNestedMode.JSON",
                )

                else -> if (columns.put(column, value) != null) {
                    throw CsvException("Conflicting nested CSV column '$column'")
                }
            }
        }
    }

    private fun cell(node: Node?): String = when (node) {
        null -> ""
        is NullNode -> ""
        is StringNode -> node.value
        is NumberNode -> node.raw
        is BooleanNode -> node.value.toString()
        is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
        is ObjectNode, is ArrayNode -> if (nestedMode == CsvNestedMode.JSON) {
            CsvJsonCodec.write(node, cellNestingLimit())
        } else {
            throw CsvException("Nested CSV cell values are not supported: ${node.type}")
        }
    }

    /**
     * JSON 单元格相对嵌套上限：单元格根在整体树中深度为 `3`，可用相对深度为
     * `maxDepth - 2`；`0` 或负数表示不限制。
     */
    private fun cellNestingLimit(): Int = if (maxDepth > 0) maxDepth - 2 else 0
}

/**
 * 将文档树写成 CSV 字符串。
 *
 * @param delimiter 字段分隔符
 * @param header 是否写出表头
 * @param nestedMode 嵌套值读写方式
 * @param maxDepth 写出时 JSON 文本单元格内允许的最大嵌套深度，默认
 *   [CsvConfig.DEFAULT_MAX_DEPTH]
 * @return CSV 文本
 * @throws CsvException 根节点类型错误、嵌套列冲突或单元格不是标量时抛出
 */
public fun Node.toCsvString(
    delimiter: Char = DEFAULT_CSV_DELIMITER,
    header: Boolean = DEFAULT_CSV_HEADER,
    nestedMode: CsvNestedMode? = null,
    maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
): String = CsvWriter(delimiter, header, nestedMode, maxDepth).write(this)
