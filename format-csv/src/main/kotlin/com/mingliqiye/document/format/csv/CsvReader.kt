package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.exception.CsvParseException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser

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
 * 整段解析适合中小输入；大文件请使用 [CsvMapper.parseStream] 逐行解析。
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
        val decoder = CsvRowDecoder(nestedMode)
        if (header) {
            decoder.readHeader(records.next())
        }
        val rows = ArrayList<Node>()
        while (records.hasNext()) {
            rows.add(decoder.readRow(records.next()))
        }
        return ArrayNode.from(rows)
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
