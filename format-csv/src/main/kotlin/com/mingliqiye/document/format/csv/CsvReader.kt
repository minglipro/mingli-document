package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.exception.CsvParseException
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
 * [readFeatures] 对齐 上游实现 `CsvParser.Feature` / `CsvReadFeature`，默认值见
 * [CsvConfig.DEFAULT_READ_FEATURES]；无 Schema 路径仅使用配置级选项（分隔符、表头、
 * 引号/转义使用默认值）。
 *
 * 整段解析适合中小输入；大文件请使用 [CsvMapper.parseStream] 逐行解析。
 *
 * 解析深度：根数组深度为 `1`、行对象为 `2`、单元格标量为 `3`；点分列名（
 * [CsvNestedMode.COLUMNS]）与 JSON 文本单元格内部嵌套都会累加深度。超过 [maxDepth]
 * 时抛出 [CsvParseException]。默认安全上限 [CsvConfig.DEFAULT_MAX_DEPTH]（512）。
 *
 * @param source CSV 文本
 * @param delimiter 字段分隔符
 * @param header 是否使用首行作为表头
 * @param nestedMode 嵌套值读写方式
 * @param maxDepth 解析最大嵌套深度，默认 [CsvConfig.DEFAULT_MAX_DEPTH]；
 *   `0` 或负数表示不限制（显式关闭保护）
 * @param readFeatures 读取特性集合，默认 [CsvConfig.DEFAULT_READ_FEATURES]
 */
public class CsvReader(
    private val source: String,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val header: Boolean = DEFAULT_CSV_HEADER,
    private val nestedMode: CsvNestedMode? = null,
    private val maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
    private val readFeatures: Set<CsvReadFeature> = CsvConfig.DEFAULT_READ_FEATURES,
) {

    private val support = CsvReadSupport(readFeatures, null, delimiter)

    /**
     * 解析并返回文档树。
     *
     * 根节点固定为数组，每个元素对应一行对象；空文本返回空数组。
     *
     * @throws CsvParseException CSV 格式非法、表头重复、嵌套列冲突或嵌套 JSON 非法时抛出
     */
    public fun read(): Node = try {
        CSVParser.parse(source, support.format()).use { parser -> readRows(parser) }
    } catch (e: CsvParseException) {
        throw e
    } catch (e: Exception) {
        throw CsvParseException("Invalid CSV: ${e.message}", e)
    }

    private fun readRows(parser: CSVParser): Node {
        val records = parser.iterator()
        val decoder = CsvRowDecoder(nestedMode, maxDepth, support)
        var headerRecord: org.apache.commons.csv.CSVRecord? = null
        if (header) {
            while (records.hasNext()) {
                val candidate = records.next()
                if (!support.shouldSkip(candidate)) {
                    headerRecord = candidate
                    break
                }
            }
            if (headerRecord == null) {
                return ArrayNode.EMPTY
            }
            decoder.readHeader(headerRecord)
        }
        val rows = ArrayList<Node>()
        while (records.hasNext()) {
            val record = records.next()
            if (support.shouldSkip(record)) {
                continue
            }
            rows.add(decoder.readRow(record))
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
         * @param maxDepth 解析最大嵌套深度，默认 [CsvConfig.DEFAULT_MAX_DEPTH]
         * @param readFeatures 读取特性集合，默认 [CsvConfig.DEFAULT_READ_FEATURES]
         * @return 文档树
         * @throws CsvParseException CSV 格式非法、表头重复、嵌套列冲突、嵌套 JSON 非法或
         *   嵌套深度超限时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun parse(
            source: String,
            delimiter: Char = DEFAULT_CSV_DELIMITER,
            header: Boolean = DEFAULT_CSV_HEADER,
            nestedMode: CsvNestedMode? = null,
            maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
            readFeatures: Set<CsvReadFeature> = CsvConfig.DEFAULT_READ_FEATURES,
        ): Node = CsvReader(source, delimiter, header, nestedMode, maxDepth, readFeatures).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @param delimiter 字段分隔符
 * @param header 是否使用首行作为表头
 * @param nestedMode 嵌套值读写方式
 * @param maxDepth 解析最大嵌套深度，默认 [CsvConfig.DEFAULT_MAX_DEPTH]
 * @param readFeatures 读取特性集合，默认 [CsvConfig.DEFAULT_READ_FEATURES]
 * @return 文档树
 * @throws CsvParseException CSV 格式非法、表头重复、嵌套列冲突、嵌套 JSON 非法或
 *   嵌套深度超限时抛出
 */
public fun String.toCsvNode(
    delimiter: Char = DEFAULT_CSV_DELIMITER,
    header: Boolean = DEFAULT_CSV_HEADER,
    nestedMode: CsvNestedMode? = null,
    maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
    readFeatures: Set<CsvReadFeature> = CsvConfig.DEFAULT_READ_FEATURES,
): Node = CsvReader(this, delimiter, header, nestedMode, maxDepth, readFeatures).read()
