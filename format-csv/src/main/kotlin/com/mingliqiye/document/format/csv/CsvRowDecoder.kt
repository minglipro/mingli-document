package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.csv.exception.CsvParseException
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal

/**
 * CSV 单行解码器（内部实现）。
 *
 * [CsvReader] 与流式 `CsvMapper.parseStream` 共用本类，保证表头、类型推断与嵌套规则
 * 在整段解析与逐行解析两条路径上完全一致。
 *
 * @param header 是否把首行当作表头
 * @param nestedMode 嵌套值读取方式；`null` 表示平坦表格
 */
internal class CsvRowDecoder(
    private val nestedMode: CsvNestedMode?,
) {

    private var columns: List<CsvColumn>? = null

    /**
     * 读取并校验表头行。
     *
     * @param record 表头记录
     * @throws CsvParseException 表头重复、嵌套列非法或嵌套列冲突时抛出
     */
    fun readHeader(record: CSVRecord) {
        columns = readColumns(record)
    }

    /**
     * 解码一行数据为对象节点。
     *
     * @param record 数据行记录
     * @return 行对象节点
     */
    fun readRow(record: CSVRecord): Node {
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
}
