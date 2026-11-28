package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import org.apache.commons.csv.CSVParser

/**
 * 按 [CsvSchema] 读取 CSV 文本。
 *
 * 列由 Schema 决定：有表头（[CsvSchema.header] 为 true）时按列名映射，无表头时按
 * Schema 展开后的列顺序对应。单元格按字段的 [com.mingliqiye.document.schema.SchemaType]
 * 解析。解析规则见 [CsvSchema]。
 *
 * 读取格式选项来自 Schema：列分隔符（[CsvSchema.columnSeparator]，未配置时用 [delimiter]）、
 * 引号/转义字符、注释标记（[CsvSchema.allowComments]）、无表头时跳过首条数据行
 * （[CsvSchema.skipFirstDataRow]）；上游实现 风格特性由 [readFeatures] 控制。
 *
 * 嵌套对象字段按子 Schema 递归读取：`address.city` 这类点分列会组装为嵌套对象。
 * 未声明子 Schema 的 `OBJECT` / `ARRAY` 字段（以及数组元素 Schema）以 JSON 文本单元格
 * 读写，需要 [nestedMode] 为 [CsvNestedMode.JSON] 或字段声明了元素 Schema；
 * 配置 [CsvSchema.arrayElementSeparator] 后数组改为分隔文本。
 *
 * 整段解析适合中小输入；大文件请使用 [CsvMapper.parseStream] 逐行解析。
 *
 * @param source CSV 文本
 * @param schema CSV Schema
 * @param delimiter 字段分隔符（Schema 未配置 [CsvSchema.columnSeparator] 时使用）
 * @param nestedMode 嵌套值读写方式；默认 `null` 表示不支持通用嵌套
 * @param maxDepth 解析最大嵌套深度，默认 [CsvConfig.DEFAULT_MAX_DEPTH]；
 *   `0` 或负数表示不限制（显式关闭保护）
 * @param readFeatures 读取特性集合，默认 [CsvConfig.DEFAULT_READ_FEATURES]
 */
public class CsvSchemaReader(
    private val source: String,
    private val schema: CsvSchema,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val nestedMode: CsvNestedMode? = null,
    private val maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
    private val readFeatures: Set<CsvReadFeature> = CsvConfig.DEFAULT_READ_FEATURES,
) {

    private val support = CsvReadSupport(readFeatures, schema, delimiter)

    /**
     * 解析并返回文档树。
     *
     * 根节点固定为数组，每个元素对应一行对象；空文本返回空数组。
     *
     * @return 文档树
     * @throws CsvParseException CSV 格式非法、列缺失或单元格无法按 Schema 类型解析时抛出
     */
    public fun read(): Node = try {
        CSVParser.parse(source, support.format()).use { parser -> readRecords(parser) }
    } catch (e: CsvException) {
        throw e
    } catch (e: Exception) {
        throw CsvParseException("Invalid CSV: ${e.message}", e)
    }

    private fun readRecords(parser: CSVParser): Node {
        val records = parser.iterator()
        val decoder = CsvSchemaRowDecoder(schema, nestedMode, maxDepth, support)
        if (schema.header) {
            var headerRecord: org.apache.commons.csv.CSVRecord? = null
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
        } else {
            decoder.positionalLayout()
        }
        if (schema.skipFirstDataRow && records.hasNext()) {
            records.next()
        }
        val rows = ArrayList<Node>()
        while (records.hasNext()) {
            val record = records.next()
            if (support.shouldSkip(record)) {
                continue
            }
            rows.add(decoder.readRow(record, rows.size))
        }
        return ArrayNode.from(rows)
    }

    public companion object {

        /**
         * 按 Schema 解析 CSV 字符串。
         *
         * @param source CSV 文本
         * @param schema CSV Schema
         * @param delimiter 字段分隔符（Schema 未配置列分隔符时使用）
         * @param nestedMode 嵌套值读写方式
         * @param maxDepth 解析最大嵌套深度，默认 [CsvConfig.DEFAULT_MAX_DEPTH]
         * @param readFeatures 读取特性集合，默认 [CsvConfig.DEFAULT_READ_FEATURES]
         * @return 文档树
         * @throws CsvParseException CSV 格式非法、列缺失、单元格无法按 Schema 类型解析或
         *   嵌套深度超限时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun parse(
            source: String,
            schema: CsvSchema,
            delimiter: Char = DEFAULT_CSV_DELIMITER,
            nestedMode: CsvNestedMode? = null,
            maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
            readFeatures: Set<CsvReadFeature> = CsvConfig.DEFAULT_READ_FEATURES,
        ): Node = CsvSchemaReader(source, schema, delimiter, nestedMode, maxDepth, readFeatures).read()
    }
}
