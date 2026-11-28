package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.StringNode
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal

/** 空单元格读取语义。 */
internal enum class CsvEmptyCell {

    /** 按普通空字符串处理（保留为字段）。 */
    EMPTY,

    /** 读取为 null。 */
    NULL,

    /** 按缺失列处理（不产生字段）。 */
    MISSING,
}

/**
 * CSV 读取侧特性与 Schema 选项的统一应用层（内部实现）。
 *
 * 整段读取、Schema 读取、流式读取与 Token 读取共用本类，保证 上游实现 风格特性在
 * 所有读取路径上语义一致。
 *
 * @param features 开启的读取特性
 * @param schema CSV Schema；null 表示类型推断路径
 * @param configuredDelimiter 配置级分隔符（Schema 未指定 [CsvSchema.columnSeparator] 时使用）
 */
internal class CsvReadSupport(
    val features: Set<CsvReadFeature>,
    private val schema: CsvSchema? = null,
    configuredDelimiter: Char = DEFAULT_CSV_DELIMITER,
) {

    /** 实际列分隔符。 */
    val columnSeparator: Char = schema?.effectiveColumnSeparator(configuredDelimiter) ?: configuredDelimiter

    /** 实际引号字符；null 表示关闭引号。 */
    val quoteChar: Char? = schema?.quoteChar ?: CsvSchema.DEFAULT_QUOTE_CHAR

    /** 实际转义字符；null 表示不使用转义字符。 */
    val escapeChar: Char? = schema?.escapeChar

    /** 生效的注释字符；null 表示不启用注释。 */
    val commentMarker: Char? = if (CsvReadFeature.ALLOW_COMMENTS in features || schema?.allowComments == true) '#' else null

    /** 是否裁剪无引号单元格两侧空白。 */
    val trimSpaces: Boolean = CsvReadFeature.TRIM_SPACES in features

    /** 是否裁剪表头名称两侧空白。 */
    val trimHeaderSpaces: Boolean = CsvReadFeature.TRIM_HEADER_SPACES in features

    /** 是否跳过空行/纯空白行。 */
    val skipEmptyLines: Boolean = CsvReadFeature.SKIP_EMPTY_LINES in features

    /** 是否跳过仅由分隔符组成的空行。 */
    val skipEmptyRows: Boolean = CsvReadFeature.SKIP_EMPTY_ROWS in features

    /** 是否允许行尾多一个空列。 */
    val allowTrailingComma: Boolean = CsvReadFeature.ALLOW_TRAILING_COMMA in features

    /** 数据行短于 Schema 时是否报错。 */
    val failOnMissingColumns: Boolean = CsvReadFeature.FAIL_ON_MISSING_COLUMNS in features

    /** 表头缺少 Schema 必填列时是否报错。 */
    val failOnMissingHeaderColumns: Boolean = CsvReadFeature.FAIL_ON_MISSING_HEADER_COLUMNS in features

    /** 数据行短于 Schema 时是否为缺失列插入 null。 */
    val insertNullsForMissingColumns: Boolean =
        CsvReadFeature.INSERT_NULLS_FOR_MISSING_COLUMNS in features && !failOnMissingColumns

    /** 是否强制忽略 Schema 未映射的多余列。 */
    val ignoreTrailingUnmappable: Boolean = CsvReadFeature.IGNORE_TRAILING_UNMAPPABLE in features

    /** 空字符串单元格是否读取为 null。 */
    val emptyStringAsNull: Boolean = CsvReadFeature.EMPTY_STRING_AS_NULL in features

    /** 无引号空字符串单元格是否读取为 null（无引号信息时等价 [emptyStringAsNull]）。 */
    val emptyUnquotedStringAsNull: Boolean = CsvReadFeature.EMPTY_UNQUOTED_STRING_AS_NULL in features

    /** 无引号空字符串单元格是否按缺失列处理。 */
    val emptyUnquotedStringAsMissing: Boolean = CsvReadFeature.EMPTY_UNQUOTED_STRING_AS_MISSING in features

    /** 是否只把无引号 nullValue 读取为 null。 */
    val onlyUnquotedNullValuesAsNull: Boolean = CsvReadFeature.ONLY_UNQUOTED_NULL_VALUES_AS_NULL in features

    /** 表头重复列名是否报错。 */
    val failOnDuplicateHeaderColumns: Boolean = CsvReadFeature.FAIL_ON_DUPLICATE_HEADER_COLUMNS in features

    /** 表头是否大小写不敏感匹配 Schema 列名。 */
    val caseInsensitiveHeaders: Boolean = CsvReadFeature.CASE_INSENSITIVE_HEADERS in features

    /** 应用特性与 Schema 选项后的 Commons CSV 解析格式。 */
    fun format(): CSVFormat {
        val builder = CSVFormat.DEFAULT.builder()
            .setDelimiter(columnSeparator)
            .setIgnoreEmptyLines(skipEmptyLines)
            .setIgnoreSurroundingSpaces(trimSpaces)
            .setCommentMarker(commentMarker)
        builder.setQuote(quoteChar)
        builder.setEscape(escapeChar)
        return builder.build()
    }

    /**
     * 读取记录的全部单元格值，应用尾逗号规则（允许时去掉一个行尾空列）。
     */
    fun cells(record: CSVRecord): List<String> {
        val size = record.size()
        val values = ArrayList<String>(size)
        for (index in 0 until size) {
            values.add(record.get(index))
        }
        if (allowTrailingComma && values.size > 1 && values[values.size - 1].isEmpty()) {
            values.removeAt(values.size - 1)
        }
        return values
    }

    /**
     * 读取表头列名，应用表头空格裁剪与重复列策略。
     *
     * @throws com.mingliqiye.document.format.csv.exception.CsvParseException
     *   表头重复且 [failOnDuplicateHeaderColumns] 开启时抛出
     */
    fun headerNames(record: CSVRecord): List<String> {
        val raw = cells(record)
        val names = ArrayList<String>(raw.size)
        val seen = HashSet<String>(raw.size)
        for (value in raw) {
            val name = if (trimHeaderSpaces) value.trim() else value
            if (!seen.add(name) && failOnDuplicateHeaderColumns) {
                throw com.mingliqiye.document.format.csv.exception.CsvParseException(
                    "Duplicate CSV header '$name'",
                )
            }
            names.add(name)
        }
        return names
    }

    /** 是否应跳过该记录（空行/纯空白行/仅分隔符行）。 */
    fun shouldSkip(record: CSVRecord): Boolean {
        val values = cells(record)
        if (skipEmptyLines && values.all { it.isBlank() }) {
            return true
        }
        return skipEmptyRows && values.all { it.isEmpty() }
    }

    /** 空单元格语义。 */
    fun emptyCell(): CsvEmptyCell = when {
        emptyStringAsNull || emptyUnquotedStringAsNull -> CsvEmptyCell.NULL
        emptyUnquotedStringAsMissing -> CsvEmptyCell.MISSING
        else -> CsvEmptyCell.EMPTY
    }

    /** Schema 配置的 null 值文本；未配置时为 `null`。 */
    fun nullValueText(): String? = schema?.nullValue

    /** 文本是否为 Schema 配置的 null 值（无法区分引号，见 KDoc 差异说明）。 */
    fun isNullValueText(text: String): Boolean {
        val nullValue = schema?.nullValue ?: return false
        return text == nullValue
    }

    /** 数组元素分隔符；null 表示数组使用 JSON 文本。 */
    fun arrayElementSeparator(): String? = schema?.arrayElementSeparator

    /** 按数组元素分隔符拆分数组单元格；分隔符为 null 时返回单元素列表。 */
    fun splitArray(text: String): List<String> {
        val separator = schema?.arrayElementSeparator ?: return listOf(text)
        if (text.isEmpty()) {
            return emptyList()
        }
        return text.split(separator)
    }

    /** 类型推断：true/false、数字、字符串。 */
    fun infer(text: String): Node = when (text) {
        "true" -> BooleanNode.of(true)
        "false" -> BooleanNode.of(false)
        else -> if (isNumber(text)) NumberNode.ofRaw(text) else StringNode.of(text)
    }

    /** 是否为合法数字文本。 */
    fun isNumber(text: String): Boolean = try {
        BigDecimal(text)
        true
    } catch (_: NumberFormatException) {
        false
    }
}
