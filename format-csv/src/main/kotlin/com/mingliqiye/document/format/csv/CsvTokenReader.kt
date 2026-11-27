package com.mingliqiye.document.format.csv

import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.math.BigDecimal

/**
 * CSV 原生 Token 读取器。
 *
 * 直接从 CSV 记录流生成 `数组 -> 对象 -> 字段/值` Token，不构建完整 Node 树。
 * 字段类型推断规则与 [CsvReader] 一致：true/false、数字、字符串。
 */
internal class CsvTokenReader(
    source: String,
    delimiter: Char,
    private val header: Boolean,
) : DocumentTokenReader {

    private val parser: CSVParser = try {
        CSVParser.parse(
            source,
            CSVFormat.DEFAULT.builder().setDelimiter(delimiter).build(),
        )
    } catch (e: Exception) {
        throw CsvParseException("Invalid CSV: ${e.message}", e)
    }
    private val records = parser.iterator()
    private var columns: List<String>? = null
    private var row: CSVRecord? = null
    private var fieldIndex = 0
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
            if (header && records.hasNext()) {
                columns = readColumns(records.next())
            }
            return set(DocumentTokenType.START_ARRAY, null)
        }
        if (row == null) {
            if (!records.hasNext()) {
                if (!finished) {
                    finished = true
                    return set(DocumentTokenType.END_ARRAY, null)
                }
                return null
            }
            row = records.next()
            fieldIndex = 0
            expectField = true
            if (!header) {
                columns = defaultColumns(row!!.size())
            }
            return set(DocumentTokenType.START_OBJECT, null)
        }
        val currentRow = row!!
        val names = columns ?: defaultColumns(currentRow.size())
        if (fieldIndex >= names.size) {
            row = null
            return set(DocumentTokenType.END_OBJECT, null)
        }
        if (expectField) {
            val name = names[fieldIndex]
            expectField = false
            return set(DocumentTokenType.FIELD_NAME, name)
        }
        val value = if (fieldIndex < currentRow.size()) currentRow.get(fieldIndex) else ""
        fieldIndex++
        expectField = true
        return when (value) {
            "true" -> setBoolean(DocumentTokenType.BOOLEAN, true)
            "false" -> setBoolean(DocumentTokenType.BOOLEAN, false)
            else -> if (isNumber(value)) {
                set(DocumentTokenType.NUMBER, value)
            } else {
                set(DocumentTokenType.STRING, value)
            }
        }
    }

    private fun readColumns(record: CSVRecord): List<String> {
        val seen = HashSet<String>(record.size())
        val result = ArrayList<String>(record.size())
        for (index in 0 until record.size()) {
            val name = record.get(index)
            if (!seen.add(name)) {
                throw CsvParseException("Duplicate CSV header '$name'")
            }
            result.add(name)
        }
        return result
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
}
