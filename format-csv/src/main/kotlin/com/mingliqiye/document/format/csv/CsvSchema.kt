package com.mingliqiye.document.format.csv

import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField

/**
 * CSV Schema。
 *
 * 字段声明顺序即列顺序：写出时按该顺序输出列，读取无表头数据时按该顺序对应列。
 * 字段类型使用 [com.mingliqiye.document.schema.SchemaType]，读取时按类型解析单元格。
 *
 * 表头由 [header] 控制；当 Mapper 同时配置了 [CsvConfig] 与本 Schema 时，Schema 的
 * [header] 优先于 [CsvConfig.header]。分隔符默认沿用 [CsvConfig.delimiter]，
 * [columnSeparator] 非 null 时优先。
 *
 * 格式选项对齐 上游实现 `CsvSchema`：[columnSeparator]、[quoteChar]、[escapeChar]、
 * [lineSeparator]、[nullValue]、[skipFirstDataRow]、[allowComments] 与
 * [arrayElementSeparator]；列顺序可用 [sortedBy] 重排。
 *
 * 嵌套 Schema：
 * - 通过 [CsvSchemaBuilder.objectField] 声明的 `OBJECT` 字段拥有子 Schema，其子字段展开为
 *   点分列名（`address.city`），支持任意层级；写出时按子 Schema 递归展开嵌套对象；
 * - 通过 [CsvSchemaBuilder.arrayField] 声明的 `ARRAY` 字段可携带元素 Schema，数组默认以
 *   JSON 文本单元格读写；显式设置 [arrayElementSeparator] 后改为 上游实现 风格的分隔文本；
 * - 未声明子 Schema 的 `OBJECT` / `ARRAY` 字段由 [CsvConfig.nestedMode] 启用 JSON 单元格，
 *   默认不支持（保持旧行为）。
 *
 * 读取规则：
 * - 有表头时按列名映射，无表头时按展开后的列顺序对应；
 * - 单元格按字段类型解析，无法解析时抛出
 *   [com.mingliqiye.document.format.csv.exception.CsvParseException]；
 * - 空单元格在 [SchemaField.nullable] 时读作 null，`STRING` 读作空串，其余必填字段报错；
 * - [SchemaField.required] 的列缺失（表头中没有该列、无表头数据行过短）时报错；可选嵌套
 *   对象整体缺席时其内部必填列不报错；
 * - 嵌套对象的所有单元格都为空时按对象缺席处理，便于可选对象写出后 round-trip；
 * - 未在 Schema 中声明的列由 [failOnUnknownColumns] 控制：默认忽略，为 true 时报错；
 * - 嵌套对象字段按子 Schema 与点分列名读取；JSON 单元格非法、数组元素类型不符时报错。
 *
 * 写出规则：
 * - 只输出 Schema 声明的列；行中未声明的顶层字段由 [failOnUnknownColumns] 控制；
 * - 行中缺少 [SchemaField.required] 的列时抛出
 *   [com.mingliqiye.document.format.csv.exception.CsvException]；可选嵌套对象缺席时其列
 *   写成空单元格；
 * - 嵌套对象按子 Schema 展开，嵌套对象上的额外字段被忽略。
 *
 * @param name Schema 名称
 * @param fields 顶层字段列表，顺序即列顺序
 * @param header 是否读写表头行
 * @param failOnUnknownColumns 遇到 Schema 未声明的列/嵌套字段时是否抛出异常
 * @param nestedObjects `OBJECT` 字段的嵌套子 Schema，键为顶层字段名
 * @param nestedArrays `ARRAY` 字段的元素 Schema，键为顶层字段名
 * @param columnSeparator 列分隔符；null 表示沿用 [CsvConfig.delimiter]（默认 `,`）
 * @param quoteChar 引号字符；null 表示关闭引号（写出需要引号的值会尽量用转义字符）
 * @param escapeChar 转义字符；null 表示不使用转义字符（引号用双写转义）
 * @param lineSeparator 行分隔符（写出用），默认 `\n`
 * @param arrayElementSeparator 数组单元格元素分隔符；null 表示数组使用 JSON 文本（本库扩展）
 * @param nullValue null 值写出文本；null 表示空串
 * @param skipFirstDataRow 无表头读取时是否跳过第一条数据行（上游实现 `skipFirstDataRow`）
 * @param allowComments 是否允许 `#` 注释行（上游实现 `allowComments`）
 */
public class CsvSchema internal constructor(
    override val name: String,
    override val fields: List<SchemaField>,
    /** 是否读写表头行。 */
    public val header: Boolean,
    /** 遇到 Schema 未声明的列时是否抛出异常。 */
    public val failOnUnknownColumns: Boolean,
    /** `OBJECT` 字段的嵌套子 Schema，键为顶层字段名。 */
    public val nestedObjects: Map<String, CsvSchema> = emptyMap(),
    /** `ARRAY` 字段的元素 Schema，键为顶层字段名。 */
    public val nestedArrays: Map<String, CsvSchema> = emptyMap(),
    /** 列分隔符；null 表示沿用 [CsvConfig.delimiter]。 */
    public val columnSeparator: Char? = null,
    /** 引号字符；null 表示关闭引号。默认 [DEFAULT_QUOTE_CHAR]。 */
    public val quoteChar: Char? = DEFAULT_QUOTE_CHAR,
    /** 转义字符；null 表示不使用转义字符。 */
    public val escapeChar: Char? = null,
    /** 行分隔符（写出用），默认 [DEFAULT_LINE_SEPARATOR]。 */
    public val lineSeparator: String = DEFAULT_LINE_SEPARATOR,
    /** 数组单元格元素分隔符；null 表示数组使用 JSON 文本（本库扩展）。 */
    public val arrayElementSeparator: String? = null,
    /** null 值写出文本；null 表示空串。 */
    public val nullValue: String? = null,
    /** 无表头读取时是否跳过第一条数据行，默认 false。 */
    public val skipFirstDataRow: Boolean = false,
    /** 是否允许 `#` 注释行，默认 false。 */
    public val allowComments: Boolean = false,
) : DocumentSchema {

    init {
        require(lineSeparator.isNotEmpty()) { "CSV schema '$name' lineSeparator must not be empty" }
        require(columnSeparator == null || separatorValid(columnSeparator)) {
            "CSV schema '$name' columnSeparator must not be a line break, quote or null character"
        }
        require(quoteChar == null || quoteChar != columnSeparator) {
            "CSV schema '$name' quoteChar must differ from columnSeparator"
        }
        require(escapeChar == null || escapeChar != columnSeparator) {
            "CSV schema '$name' escapeChar must differ from columnSeparator"
        }
    }

    private val byName: Map<String, SchemaField> = fields.associateBy { it.name }

    /** 展开后的列定义列表，顺序与 [columnNames] 一致。 */
    internal val columns: List<CsvFlatColumn> = expandColumns(emptyList())

    private val byColumn: Map<String, CsvFlatColumn> = columns.associateBy { it.column }

    /** 展开后的列名列表，嵌套对象展开为点分列名。 */
    public val columnNames: List<String> = columns.map { it.column }

    /** null 值写出文本：未配置 [nullValue] 时为空串。 */
    public val nullValueText: String
        get() = nullValue ?: ""

    /**
     * 解析实际使用的列分隔符：Schema 显式配置优先，否则取 [fallback]。
     *
     * @param fallback 配置级分隔符（通常为 [CsvConfig.delimiter]）
     * @return 实际列分隔符
     */
    public fun effectiveColumnSeparator(fallback: Char): Char = columnSeparator ?: fallback

    override fun field(name: String): SchemaField? = byName[name]

    /**
     * 按字段名查找嵌套 `OBJECT` 子 Schema。
     *
     * @param name 顶层字段名
     * @return 子 Schema；不是嵌套对象字段时返回 `null`
     */
    public fun nestedObject(name: String): CsvSchema? = nestedObjects[name]

    /**
     * 按字段名查找嵌套 `ARRAY` 元素 Schema。
     *
     * @param name 顶层字段名
     * @return 元素 Schema；不是带元素 Schema 的数组字段时返回 `null`
     */
    public fun nestedArray(name: String): CsvSchema? = nestedArrays[name]

    /**
     * 按展开后的列名查找列定义。
     */
    internal fun column(name: String): CsvFlatColumn? = byColumn[name]

    /**
     * 返回按指定列顺序重排后的新 Schema（对齐 上游实现 `CsvSchema.sortedBy`）。
     *
     * 未列出的字段保持原相对顺序排在末尾；只影响顶层字段顺序，嵌套子 Schema 不变。
     *
     * @param columnNames 顶层字段名，按期望顺序
     * @return 重排后的新 Schema
     * @throws IllegalArgumentException 列名重复或不存在时抛出
     */
    public fun sortedBy(vararg columnNames: String): CsvSchema {
        val requested = columnNames.toList()
        require(requested.isNotEmpty()) { "CSV schema '$name' column order must not be empty" }
        require(requested.toSet().size == requested.size) {
            "CSV schema '$name' column order contains duplicates: $requested"
        }
        val byFieldName = fields.associateBy { it.name }
        for (column in requested) {
            require(byFieldName.containsKey(column)) {
                "CSV schema '$name' does not declare column '$column'"
            }
        }
        val reordered = ArrayList<SchemaField>(fields.size)
        for (column in requested) {
            reordered.add(byFieldName.getValue(column))
        }
        for (field in fields) {
            if (field.name !in requested) {
                reordered.add(field)
            }
        }
        return CsvSchema(
            name,
            reordered,
            header,
            failOnUnknownColumns,
            nestedObjects,
            nestedArrays,
            columnSeparator,
            quoteChar,
            escapeChar,
            lineSeparator,
            arrayElementSeparator,
            nullValue,
            skipFirstDataRow,
            allowComments,
        )
    }

    /** 以指定路径前缀展开当前 Schema 的列。 */
    internal fun expandColumns(prefix: List<String>): List<CsvFlatColumn> {
        val result = ArrayList<CsvFlatColumn>(fields.size)
        for (field in fields) {
            val path = prefix + field.name
            val nested = nestedObjects[field.name]
            if (nested != null) {
                result.addAll(nested.expandColumns(path))
            } else {
                result.add(CsvFlatColumn(path.joinToString("."), path, field))
            }
        }
        return result
    }

    public companion object {

        /** 上游实现 默认引号字符。 */
        public const val DEFAULT_QUOTE_CHAR: Char = '"'

        /** 上游实现 默认列分隔符。 */
        public const val DEFAULT_COLUMN_SEPARATOR: Char = ','

        /** 上游实现 默认行分隔符。 */
        public const val DEFAULT_LINE_SEPARATOR: String = "\n"

        /** 上游实现 默认数组元素分隔符。 */
        public const val DEFAULT_ARRAY_ELEMENT_SEPARATOR: String = ";"

        /** 无数组元素分隔符（上游实现 `NO_ARRAY_ELEMENT_SEPARATOR`）。 */
        public const val NO_ARRAY_ELEMENT_SEPARATOR: String = ""

        /**
         * 创建 CSV Schema 构建器。
         *
         * @param name Schema 名称
         * @return 构建器
         */
        @JvmStatic
        public fun builder(name: String): CsvSchemaBuilder = CsvSchemaBuilder(name)

        /**
         * 使用字段定义创建 CSV Schema。
         *
         * @param name Schema 名称
         * @param fields 字段定义，顺序即列顺序
         * @return CSV Schema
         */
        @JvmStatic
        public fun of(name: String, vararg fields: SchemaField): CsvSchema {
            val builder = CsvSchemaBuilder(name)
            for (field in fields) {
                builder.addField(field)
            }
            return builder.build()
        }

        private fun separatorValid(separator: Char): Boolean = separator != '\n' && separator != '\r' && separator != '\u0000'
    }
}

/**
 * 展开后的 CSV 列定义。
 *
 * @property column 展开后的列名（如 `address.city`）
 * @property path 从顶层字段到叶子字段的路径
 * @property field 叶子字段
 */
internal class CsvFlatColumn(
    val column: String,
    val path: List<String>,
    val field: SchemaField,
)

/** 默认是否读写表头行。 */
internal const val DEFAULT_CSV_SCHEMA_HEADER: Boolean = true

/** 默认是否在遇到未声明列时抛出异常。 */
internal const val DEFAULT_CSV_SCHEMA_FAIL_ON_UNKNOWN_COLUMNS: Boolean = false
