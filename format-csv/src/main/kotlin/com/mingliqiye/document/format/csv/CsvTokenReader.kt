package com.mingliqiye.document.format.csv

import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import org.apache.commons.csv.CSVParser
import java.math.BigDecimal

/**
 * CSV 原生 Token 读取器。
 *
 * 直接从 CSV 记录流生成 `数组 -> 对象 -> 字段/值` Token，不构建完整 Node 树。
 * 字段类型推断规则与 [CsvReader] 一致：true/false、数字、字符串；并应用
 * [CsvReadFeature] 中的空单元格/null/重复表头/跳行等读取特性。
 *
 * 平坦 CSV 的固定深度为：根数组 `1`、行对象 `2`、单元格 `3`；[maxDepth] 小于该结构
 * 深度时在读取到数据行后抛出 [CsvParseException]。
 *
 * @param maxDepth 解析最大嵌套深度；`0` 或负数表示不限制
 * @param readFeatures 读取特性集合，默认 [CsvConfig.DEFAULT_READ_FEATURES]
 */
internal class CsvTokenReader(
    source: String,
    delimiter: Char,
    private val header: Boolean,
    private val maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
    readFeatures: Set<CsvReadFeature> = CsvConfig.DEFAULT_READ_FEATURES,
) : DocumentTokenReader {

    private val support = CsvReadSupport(readFeatures, null, delimiter)

    private val parser: CSVParser = try {
        CSVParser.parse(source, support.format())
    } catch (e: Exception) {
        throw CsvParseException("Invalid CSV: ${e.message}", e)
    }
    private val records = parser.iterator()
    private var columns: List<String>? = null
    private var rowCells: List<TokenCell>? = null
    private var cellIndex = 0
    private var expectField = true
    private var started = false
    private var finished = false
    private var current: DocumentTokenType? = null
    private var text: String? = null
    private var boolean: Boolean? = null

    override val currentToken: DocumentTokenType?
        get() = current

    override val textValue: String?
        get() = text

    override val booleanValue: Boolean?
        get() = boolean

    override val binaryValue: ByteArray?
        get() = null

    override fun nextToken(): DocumentTokenType? {
        if (!started) {
            started = true
            initializeHeader()
            return set(DocumentTokenType.START_ARRAY, null)
        }
        val cells = rowCells
        if (cells == null) {
            val next = nextRecordCells() ?: return finishArray()
            rowCells = next
            cellIndex = 0
            expectField = true
            if (maxDepth in 1..2) {
                throw CsvParseException("CSV row depth 3 exceeds maxDepth $maxDepth")
            }
            return set(DocumentTokenType.START_OBJECT, null)
        }
        if (cellIndex >= cells.size) {
            rowCells = null
            return set(DocumentTokenType.END_OBJECT, null)
        }
        val cell = cells[cellIndex]
        if (expectField) {
            expectField = false
            return set(DocumentTokenType.FIELD_NAME, cell.name)
        }
        cellIndex++
        expectField = true
        if (cell.nullToken) {
            return setNull()
        }
        return inferToken(cell.value)
    }

    private fun finishArray(): DocumentTokenType? {
        if (!finished) {
            finished = true
            return set(DocumentTokenType.END_ARRAY, null)
        }
        return null
    }

    private fun initializeHeader() {
        if (!header) {
            return
        }
        while (records.hasNext()) {
            val record = records.next()
            if (support.shouldSkip(record)) {
                continue
            }
            columns = support.headerNames(record)
            break
        }
    }

    private fun nextRecordCells(): List<TokenCell>? {
        while (records.hasNext()) {
            val record = records.next()
            if (support.shouldSkip(record)) {
                continue
            }
            return buildCells(support.cells(record))
        }
        return null
    }

    private fun buildCells(values: List<String>): List<TokenCell> {
        val names = columns ?: defaultColumns(values.size)
        val cells = ArrayList<TokenCell>(names.size)
        for (index in names.indices) {
            val name = names[index]
            val present = index < values.size
            val value = if (present) values[index] else ""
            if (present && support.isNullValueText(value)) {
                cells.add(TokenCell(name, value, nullToken = true))
                continue
            }
            if (!present) {
                when (support.emptyCell()) {
                    CsvEmptyCell.MISSING -> continue
                    CsvEmptyCell.NULL -> cells.add(TokenCell(name, value, nullToken = true))
                    CsvEmptyCell.EMPTY -> cells.add(TokenCell(name, value, nullToken = false))
                }
                continue
            }
            if (value.isEmpty()) {
                when (support.emptyCell()) {
                    CsvEmptyCell.MISSING -> continue
                    CsvEmptyCell.NULL -> cells.add(TokenCell(name, value, nullToken = true))
                    CsvEmptyCell.EMPTY -> cells.add(TokenCell(name, value, nullToken = false))
                }
                continue
            }
            cells.add(TokenCell(name, value, nullToken = false))
        }
        return cells
    }

    private fun inferToken(value: String): DocumentTokenType = when (value) {
        "true" -> setBoolean(DocumentTokenType.BOOLEAN, true)
        "false" -> setBoolean(DocumentTokenType.BOOLEAN, false)
        else -> if (isNumber(value)) {
            set(DocumentTokenType.NUMBER, value)
        } else {
            set(DocumentTokenType.STRING, value)
        }
    }

    private fun defaultColumns(size: Int): List<String> = (1..size).map { "column$it" }

    private fun isNumber(value: String): Boolean = try {
        BigDecimal(value)
        true
    } catch (_: NumberFormatException) {
        false
    }

    private fun set(type: DocumentTokenType, value: String?): DocumentTokenType {
        current = type
        text = value
        boolean = null
        return type
    }

    private fun setBoolean(type: DocumentTokenType, value: Boolean): DocumentTokenType {
        current = type
        text = value.toString()
        boolean = value
        return type
    }

    private fun setNull(): DocumentTokenType {
        current = DocumentTokenType.NULL
        text = null
        boolean = null
        return DocumentTokenType.NULL
    }

    /** 当前行的一个待输出单元格。 */
    private class TokenCell(
        val name: String,
        val value: String,
        val nullToken: Boolean,
    )
}
