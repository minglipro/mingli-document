package com.mingliqiye.document.format.csv

/**
 * CSV 单元格值类型，用于写出的引号策略判断。
 */
internal enum class CsvCellKind {

    /** 字符串值（含 JSON 文本单元格）。 */
    STRING,

    /** 数字值。 */
    NUMBER,

    /** 布尔值。 */
    BOOLEAN,

    /** 二进制值（Base64 文本）。 */
    BINARY,

    /** null 值，写出为 null 文本（默认空串）。 */
    NULL,

    /** 缺失列，开启 `OMIT_MISSING_TAIL_COLUMNS` 时行尾缺失列可省略。 */
    MISSING,

    /** 其他标量。 */
    OTHER,
}

/**
 * 待写出的单元格。
 *
 * @property text 单元格文本；[CsvCellKind.NULL]/[CsvCellKind.MISSING] 时可为 null
 * @property kind 值类型，用于 上游实现 引号/省略特性判断
 */
internal class CsvCell(
    val text: String?,
    val kind: CsvCellKind = CsvCellKind.OTHER,
)

/**
 * 自定义 CSV 编码器（内部实现），支持 上游实现 `CsvGenerator.Feature` 全部写出行为。
 *
 * 引号判断对齐 上游实现 `CsvEncoder`：默认（未开启 `STRICT_CHECK_FOR_QUOTING`）使用宽松
 * 检查——值长度超过 24 或包含小于 `minSafeChar` 的字符、引号/转义字符时加引号；开启严格
 * 检查后只在确实需要时加引号。
 *
 * @param columnSeparator 列分隔符
 * @param quoteChar 引号字符；null 表示关闭引号
 * @param escapeChar 转义字符；null 表示用双写引号转义
 * @param lineSeparator 行分隔符
 * @param nullValue null 值写出文本；null 表示空串
 * @param allowComments 是否允许注释行（首列以 `#` 开头时加引号）
 * @param features 写出特性
 */
internal class CsvEncoder(
    private val columnSeparator: Char,
    private val quoteChar: Char?,
    private val escapeChar: Char?,
    private val lineSeparator: String,
    private val nullValue: String?,
    private val allowComments: Boolean,
    features: Set<CsvWriteFeature>,
) {

    private val strictQuoting = CsvWriteFeature.STRICT_CHECK_FOR_QUOTING in features
    private val omitMissingTail = CsvWriteFeature.OMIT_MISSING_TAIL_COLUMNS in features
    private val alwaysQuoteStrings = CsvWriteFeature.ALWAYS_QUOTE_STRINGS in features
    private val alwaysQuoteEmptyStrings = CsvWriteFeature.ALWAYS_QUOTE_EMPTY_STRINGS in features
    private val alwaysQuoteNumbers = CsvWriteFeature.ALWAYS_QUOTE_NUMBERS in features
    private val escapeQuoteCharWithEscapeChar = CsvWriteFeature.ESCAPE_QUOTE_CHAR_WITH_ESCAPE_CHAR in features
    private val escapeControlChars = CsvWriteFeature.ESCAPE_CONTROL_CHARS_WITH_ESCAPE_CHAR in features
    private val writeLinefeedAfterLastRow = CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW in features
    private val quoteStringsWithEdgeWhitespace = CsvWriteFeature.QUOTE_STRINGS_WITH_LEADING_TRAILING_WHITESPACE in features

    /** 无需引号即可安全输出的最小字符码点 + 1。 */
    private val minSafeChar: Int = computeMinSafeChar()

    /** 引号转义时使用的字符。 */
    private val quoteEscapeChar: Char = when {
        escapeQuoteCharWithEscapeChar && escapeChar != null -> escapeChar
        quoteChar != null -> quoteChar
        else -> '\\'
    }

    /** 控制字符转义时使用的字符。 */
    private val controlEscapeChar: Char = escapeChar ?: '\\'

    /**
     * 写出 CSV 文本。
     *
     * @param header 表头；null 表示不写表头
     * @param rows 数据行（每行单元格数量可不同，行尾缺失列按特性处理）
     * @return CSV 文本；没有任何输出时为空串
     */
    fun write(header: List<String>?, rows: List<List<CsvCell>>): String {
        val out = StringBuilder()
        var written = 0
        if (header != null) {
            appendRow(out, header.map { CsvCell(it, CsvCellKind.STRING) })
            written++
        }
        for (row in rows) {
            appendRow(out, row)
            written++
        }
        if (written == 0) {
            return ""
        }
        if (!writeLinefeedAfterLastRow && out.endsWith(lineSeparator)) {
            out.setLength(out.length - lineSeparator.length)
        }
        return out.toString()
    }

    private fun appendRow(out: StringBuilder, rowInput: List<CsvCell>) {
        var row = rowInput
        if (omitMissingTail) {
            var last = row.size - 1
            while (last >= 0 && row[last].kind == CsvCellKind.MISSING) {
                last--
            }
            if (last + 1 < row.size) {
                row = row.subList(0, last + 1)
            }
        }
        for (index in row.indices) {
            if (index > 0) {
                out.append(columnSeparator)
            }
            appendCell(out, row[index], index)
        }
        out.append(lineSeparator)
    }

    private fun appendCell(out: StringBuilder, cell: CsvCell, columnIndex: Int) {
        when (cell.kind) {
            CsvCellKind.MISSING -> return
            CsvCellKind.NULL -> {
                out.append(nullValue ?: "")
                return
            }

            else -> Unit
        }
        val text = cell.text ?: ""
        // 上游实现 对数字/布尔值不参与引号检查（仅 ALWAYS_QUOTE_NUMBERS 强制数字加引号）。
        if (cell.kind == CsvCellKind.NUMBER) {
            if (alwaysQuoteNumbers && quoteChar != null) {
                appendQuoted(out, text)
            } else {
                out.append(text)
            }
            return
        }
        if (cell.kind == CsvCellKind.BOOLEAN) {
            out.append(text)
            return
        }
        val forceQuote = cell.kind == CsvCellKind.STRING &&
            (
                alwaysQuoteStrings ||
                    (alwaysQuoteEmptyStrings && text.isEmpty()) ||
                    (quoteStringsWithEdgeWhitespace && hasEdgeWhitespace(text))
                )
        val quote = quoteChar != null && (forceQuote || mayNeedQuotes(text, columnIndex))
        if (quote) {
            appendQuoted(out, text)
        } else {
            out.append(text)
        }
    }

    private fun mayNeedQuotes(text: String, columnIndex: Int): Boolean {
        if (quoteChar == null) {
            return false
        }
        if (strictQuoting) {
            if (allowComments && columnIndex == 0 && text.startsWith("#")) {
                return true
            }
            return needsQuotingStrict(text)
        }
        if (text.length > MAX_QUOTE_CHECK) {
            return true
        }
        if (alwaysQuoteEmptyStrings && text.isEmpty()) {
            return true
        }
        return needsQuotingLoose(text)
    }

    private fun needsQuotingLoose(text: String): Boolean {
        for (ch in text) {
            if (ch.code < minSafeChar) {
                return true
            }
            if (escapeChar != null) {
                if (ch == escapeChar) {
                    return true
                }
            } else if (ch == quoteEscapeChar || ch == controlEscapeChar) {
                return true
            }
        }
        return false
    }

    private fun needsQuotingStrict(text: String): Boolean {
        val lineSeparatorFirst = lineSeparator[0].code
        for (ch in text) {
            val code = ch.code
            if (code < minSafeChar) {
                if (code == columnSeparator.code ||
                    code == (quoteChar?.code ?: -1) ||
                    code == lineSeparatorFirst ||
                    code == '\n'.code ||
                    code == '\r'.code
                ) {
                    return true
                }
            } else if (escapeChar != null && ch == escapeChar) {
                return true
            }
        }
        return false
    }

    private fun appendQuoted(out: StringBuilder, text: String) {
        val quote = quoteChar ?: return
        out.append(quote)
        for (ch in text) {
            if (escapeControlChars) {
                val escaped = controlEscape(ch)
                if (escaped != null) {
                    out.append(escaped)
                    continue
                }
            }
            if (ch == quote) {
                if (escapeQuoteCharWithEscapeChar && escapeChar != null) {
                    out.append(escapeChar)
                    out.append(ch)
                } else {
                    out.append(quote)
                    out.append(ch)
                }
            } else if (escapeChar != null && ch == escapeChar && !escapeControlChars) {
                out.append(escapeChar)
                out.append(ch)
            } else {
                out.append(ch)
            }
        }
        out.append(quote)
    }

    /**
     * 控制字符转义文本；仅支持 Commons CSV 可读回的 `\b`、`\t`、`\n`、`\f`、`\r`。
     */
    private fun controlEscape(ch: Char): String? {
        val code = when (ch) {
            '\b' -> "b"
            '\t' -> "t"
            '\n' -> "n"
            '\u000C' -> "f"
            '\r' -> "r"
            else -> return null
        }
        return controlEscapeChar.toString() + code
    }

    private fun hasEdgeWhitespace(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return text[0].code <= SPACE_CODE || text[text.length - 1].code <= SPACE_CODE
    }

    private fun computeMinSafeChar(): Int {
        var min = maxOf(columnSeparator.code, quoteChar?.code ?: -1)
        for (ch in lineSeparator) {
            min = maxOf(min, ch.code)
        }
        return min + 1
    }

    private companion object {

        /** 宽松引号检查时最长不检查长度；更长的值直接加引号（对齐 上游实现）。 */
        const val MAX_QUOTE_CHECK: Int = 24

        /** 空格码点。 */
        const val SPACE_CODE: Int = 0x20
    }
}
