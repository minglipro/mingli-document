package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser

/**
 * 按 [CsvSchema] 读取 CSV 文本。
 *
 * 列由 Schema 决定：有表头（[CsvSchema.header] 为 true）时按列名映射，无表头时按
 * Schema 展开后的列顺序对应。单元格按字段的 [com.mingliqiye.document.schema.SchemaType]
 * 解析。解析规则见 [CsvSchema]。
 *
 * 嵌套对象字段按子 Schema 递归读取：`address.city` 这类点分列会组装为嵌套对象。
 * 未声明子 Schema 的 `OBJECT` / `ARRAY` 字段（以及数组元素 Schema）以 JSON 文本单元格
 * 读写，需要 [nestedMode] 为 [CsvNestedMode.JSON] 或字段声明了元素 Schema。
 *
 * 整段解析适合中小输入；大文件请使用 [CsvMapper.parseStream] 逐行解析。
 *
 * @param source CSV 文本
 * @param schema CSV Schema
 * @param delimiter 字段分隔符
 * @param nestedMode 嵌套值读写方式；默认 `null` 表示不支持通用嵌套
 */
public class CsvSchemaReader(
    private val source: String,
    private val schema: CsvSchema,
    private val delimiter: Char = DEFAULT_CSV_DELIMITER,
    private val nestedMode: CsvNestedMode? = null,
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
        val decoder = CsvSchemaRowDecoder(schema, nestedMode)
        if (schema.header) {
            decoder.readHeader(records.next())
        } else {
            decoder.positionalLayout()
        }
        val rows = ArrayList<Node>()
        while (records.hasNext()) {
            rows.add(decoder.readRow(records.next(), rows.size))
        }
        return ArrayNode.from(rows)
    }

    public companion object {

        /**
         * 按 Schema 解析 CSV 字符串。
         *
         * @param source CSV 文本
         * @param schema CSV Schema
         * @param delimiter 字段分隔符
         * @param nestedMode 嵌套值读写方式
         * @return 文档树
         * @throws CsvParseException CSV 格式非法、列缺失或单元格无法按 Schema 类型解析时抛出
         */
        @JvmStatic
        public fun parse(
            source: String,
            schema: CsvSchema,
            delimiter: Char = DEFAULT_CSV_DELIMITER,
            nestedMode: CsvNestedMode? = null,
        ): Node = CsvSchemaReader(source, schema, delimiter, nestedMode).read()
    }
}
