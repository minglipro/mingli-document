package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.csv.exception.CsvParseException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal

/**
 * CSV 读取入口。
 *
 * 首行是否作为字段名由 [header] 控制；未使用表头时字段名为 `column1`、`column2`……
 * 单元格按 `true` / `false` / 数字 / 字符串的顺序推断类型。
 *
 * [nestedMode] 控制嵌套单元格：
 * - [CsvNestedMode.JSON]：`{...}` / `[...]` 单元格解析为嵌套对象/数组；
 * - [CsvNestedMode.COLUMNS]：表头中的点分列名（`address.city`）组装为嵌套对象；
 * - `null`（默认）保持原来的平坦解析行为。
 *
 * @param source CSV 文本
 * @param delimiter 字段分隔符
 * @param header 是否使用首行作为表头
 * @param nestedMode 嵌套值读写方式
 */
public class CsvReader(
    private val source: String,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val header: Boolean = DEFAULT_CSV_HEADER,
    private val nestedMode: CsvNestedMode? = null,
) {

    /**
     * 解析并返回文档树。
     *
     * 根节点固定为数组，每个元素对应一行对象；空文本返回空数组。
     *
     * @throws CsvParseException CSV 格式非法、表头重复、嵌套列冲突或嵌套 JSON 非法时抛出
     */
    public fun read(): Node = try {
        CSVParser.parse(source, format()).use { parser -> readRows(parser) }
    } catch (e: CsvParseException) {
        throw e
    } catch (e: Exception) {
        throw CsvParseException("Invalid CSV: ${e.message}", e)
    }

    private fun format(): CSVFormat = CSVFormat.DEFAULT.builder()
        .setDelimiter(delimiter)
        .build()

    private fun readRows(parser: CSVParser): Node {
        val records = parser.iterator()
        if (!records.hasNext()) {
            return ArrayNode.EMPTY
        }
        val columns = if (header) readColumns(records.next()) else null
        val rows = ArrayList<Node>()
        while (records.hasNext()) {
            rows.add(readRow(records.next(), columns))
        }
        return ArrayNode.from(rows)
    }

    private fun readColumns(record: CSVRecord): List<CsvColumn> {
        val seen = HashSet<String>(record.size())
        val columns = ArrayList<CsvColumn>(record.size())
        for (index in 0 until record.size()) {
            val column = record.get(index)
            if (!seen.add(column)) {
                throw CsvParseException("Duplicate CSV header '$column'")
            }
            columns.add(CsvColumn(column, pathOf(column)))
        }
        validateNestedColumns(columns)
        return columns
    }

    private fun pathOf(column: String): List<String> {
        if (nestedMode != CsvNestedMode.COLUMNS) {
            return listOf(column)
        }
        val segments = column.split('.')
        if (segments.any { it.isEmpty() }) {
            throw CsvParseException("Invalid nested CSV column '$column'")
        }
        return segments
    }

    private fun validateNestedColumns(columns: List<CsvColumn>) {
        if (nestedMode != CsvNestedMode.COLUMNS) {
            return
        }
        for (index in columns.indices) {
            val path = columns[index].path
            for (other in index + 1 until columns.size) {
                val otherPath = columns[other].path
                if (isPrefix(path, otherPath) || isPrefix(otherPath, path)) {
                    throw CsvParseException(
                        "Conflicting nested CSV columns '${columns[index].name}' and '${columns[other].name}'",
                    )
                }
            }
        }
    }

    private fun readRow(record: CSVRecord, columns: List<CsvColumn>?): Node {
        if (nestedMode == CsvNestedMode.COLUMNS) {
            val refs = columns ?: defaultColumns(record.size())
            return buildNestedRow(record, refs)
        }
        val names = columns ?: defaultColumns(record.size())
        val fields = LinkedHashMap<String, Node>(names.size)
        for (index in names.indices) {
            val text = if (index < record.size()) record.get(index) else ""
            fields[names[index].name] = inferCell(text)
        }
        return ObjectNode.from(fields)
    }

    private fun defaultColumns(size: Int): List<CsvColumn> {
        val columns = ArrayList<CsvColumn>(size)
        for (index in 1..size) {
            val name = "column$index"
            columns.add(CsvColumn(name, listOf(name)))
        }
        return columns
    }

    private fun buildNestedRow(record: CSVRecord, columns: List<CsvColumn>): Node {
        val root = LinkedHashMap<String, NestedValue>()
        for ((index, column) in columns.withIndex()) {
            val text = if (index < record.size()) record.get(index) else ""
            insertNested(root, column.path, NestedLeaf(inferScalar(text)), column.name)
        }
        return toNode(root)
    }

    private fun insertNested(
        root: LinkedHashMap<String, NestedValue>,
        path: List<String>,
        value: NestedValue,
        columnName: String,
    ) {
        var current = root
        for (index in path.indices) {
            val segment = path[index]
            if (index == path.lastIndex) {
                if (current.put(segment, value) != null) {
                    throw CsvParseException("Duplicate nested CSV column '$columnName'")
                }
                return
            }
            val existing = current[segment]
            current = if (existing == null) {
                val children = LinkedHashMap<String, NestedValue>()
                current[segment] = NestedObject(children)
                children
            } else {
                val objectValue = existing as? NestedObject
                    ?: throw CsvParseException(
                        "Conflicting nested CSV column '${path.take(index + 1).joinToString(".")}'",
                    )
                objectValue.children
            }
        }
    }

    private fun toNode(children: Map<String, NestedValue>): ObjectNode {
        val fields = LinkedHashMap<String, Node>(children.size)
        for ((key, value) in children) {
            fields[key] = when (value) {
                is NestedObject -> toNode(value.children)
                is NestedLeaf -> value.node
            }
        }
        return ObjectNode.from(fields)
    }

    private fun inferCell(text: String): Node {
        if (nestedMode == CsvNestedMode.JSON) {
            val trimmed = text.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return try {
                    CsvJsonCodec.parse(trimmed)
                } catch (e: CsvParseException) {
                    throw CsvParseException("Invalid nested JSON in CSV cell: ${e.message}", e)
                }
            }
        }
        return inferScalar(text)
    }

    private fun inferScalar(text: String): Node = when (text) {
        "true" -> BooleanNode.of(true)
        "false" -> BooleanNode.of(false)
        else -> if (isNumber(text)) NumberNode.ofRaw(text) else StringNode.of(text)
    }

    private fun isNumber(text: String): Boolean = try {
        BigDecimal(text)
        true
    } catch (_: NumberFormatException) {
        false
    }

    /** 表头列与其嵌套路径。 */
    private class CsvColumn(
        val name: String,
        val path: List<String>,
    )

    /** 点分列名构建中的中间值。 */
    private sealed interface NestedValue

    private class NestedObject(val children: LinkedHashMap<String, NestedValue>) : NestedValue

    private class NestedLeaf(val node: Node) : NestedValue

    private fun isPrefix(prefix: List<String>, path: List<String>): Boolean {
        if (prefix.size >= path.size) {
            return false
        }
        for (index in prefix.indices) {
            if (prefix[index] != path[index]) {
                return false
            }
        }
        return true
    }

    public companion object {

        /**
         * 解析 CSV 字符串。
         *
         * @param source CSV 文本
         * @param delimiter 字段分隔符
         * @param header 是否使用首行作为表头
         * @param nestedMode 嵌套值读写方式
         * @return 文档树
         * @throws CsvParseException CSV 格式非法、表头重复、嵌套列冲突或嵌套 JSON 非法时抛出
         */
        @JvmStatic
        public fun parse(
            source: String,
            delimiter: Char = DEFAULT_CSV_DELIMITER,
            header: Boolean = DEFAULT_CSV_HEADER,
            nestedMode: CsvNestedMode? = null,
        ): Node = CsvReader(source, delimiter, header, nestedMode).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @param delimiter 字段分隔符
 * @param header 是否使用首行作为表头
 * @param nestedMode 嵌套值读写方式
 * @return 文档树
 * @throws CsvParseException CSV 格式非法、表头重复、嵌套列冲突或嵌套 JSON 非法时抛出
 */
public fun String.toCsvNode(
    delimiter: Char = DEFAULT_CSV_DELIMITER,
    header: Boolean = DEFAULT_CSV_HEADER,
    nestedMode: CsvNestedMode? = null,
): Node = CsvReader(this, delimiter, header, nestedMode).read()
