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
 * @param source CSV 文本
 * @param delimiter 字段分隔符
 * @param header 是否使用首行作为表头
 */
public class CsvReader(
    private val source: String,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val header: Boolean = DEFAULT_CSV_HEADER,
) {

    /**
     * 解析并返回文档树。
     *
     * 根节点固定为数组，每个元素对应一行对象；空文本返回空数组。
     *
     * @throws CsvParseException CSV 格式非法或表头重复时抛出
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

    private fun readColumns(record: CSVRecord): List<String> {
        val columns = ArrayList<String>(record.size())
        val seen = HashSet<String>(record.size())
        for (index in 0 until record.size()) {
            val column = record.get(index)
            if (!seen.add(column)) {
                throw CsvParseException("Duplicate CSV header '$column'")
            }
            columns.add(column)
        }
        return columns
    }

    private fun readRow(record: CSVRecord, columns: List<String>?): Node {
        val names = columns ?: defaultColumns(record.size())
        val fields = LinkedHashMap<String, Node>(names.size)
        for (index in names.indices) {
            val text = if (index < record.size()) record.get(index) else ""
            fields[names[index]] = inferScalar(text)
        }
        return ObjectNode.from(fields)
    }

    private fun defaultColumns(size: Int): List<String> {
        val columns = ArrayList<String>(size)
        for (index in 1..size) {
            columns.add("column$index")
        }
        return columns
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

    public companion object {

        /**
         * 解析 CSV 字符串。
         *
         * @param source CSV 文本
         * @param delimiter 字段分隔符
         * @param header 是否使用首行作为表头
         * @return 文档树
         * @throws CsvParseException CSV 格式非法或表头重复时抛出
         */
        @JvmStatic
        public fun parse(
            source: String,
            delimiter: Char = DEFAULT_CSV_DELIMITER,
            header: Boolean = DEFAULT_CSV_HEADER,
        ): Node = CsvReader(source, delimiter, header).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @param delimiter 字段分隔符
 * @param header 是否使用首行作为表头
 * @return 文档树
 * @throws CsvParseException CSV 格式非法或表头重复时抛出
 */
public fun String.toCsvNode(
    delimiter: Char = DEFAULT_CSV_DELIMITER,
    header: Boolean = DEFAULT_CSV_HEADER,
): Node = CsvReader(this, delimiter, header).read()
