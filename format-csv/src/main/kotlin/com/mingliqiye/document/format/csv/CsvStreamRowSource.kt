package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import org.apache.commons.csv.CSVRecord

/**
 * CSV 逐行数据源（内部实现）。
 *
 * 在 Commons CSV 的惰性记录迭代器之上复用 [CsvRowDecoder] / [CsvSchemaRowDecoder]：
 * 首次调用 [next] 时读取表头（有表头时）或确定无表头列布局，此后每次调用最多读取
 * 一条记录，不预读整张表。表头、类型推断、嵌套与 Schema 规则与整段解析完全一致。
 *
 * 迭代过程中的 CSV 格式错误、IO 错误与解码错误统一包装为 [CsvParseException]。
 *
 * @param records Commons CSV 记录迭代器
 * @param header 是否使用表头（Schema 存在时应传 [CsvSchema.header]）
 * @param nestedMode 嵌套值读写方式
 * @param schema CSV Schema；`null` 表示使用类型推断路径
 * @param maxDepth 解析最大嵌套深度；`0` 或负数表示不限制
 */
internal class CsvStreamRowSource(
    private val records: Iterator<CSVRecord>,
    header: Boolean,
    nestedMode: CsvNestedMode?,
    private val schema: CsvSchema?,
    maxDepth: Int = CsvConfig.DEFAULT_MAX_DEPTH,
) {

    private val plainDecoder: CsvRowDecoder?
    private val schemaDecoder: CsvSchemaRowDecoder?
    private val headerEnabled: Boolean = schema?.header ?: header
    private var initialized = false
    private var rowIndex = 0

    init {
        if (schema == null) {
            plainDecoder = CsvRowDecoder(nestedMode, maxDepth)
            schemaDecoder = null
        } else {
            plainDecoder = null
            schemaDecoder = CsvSchemaRowDecoder(schema, nestedMode, maxDepth)
        }
    }

    /**
     * 读取下一行数据。
     *
     * @return 行对象节点；没有更多数据行时返回 `null`
     * @throws CsvParseException CSV 格式错误、解码错误或 Schema 校验失败时抛出
     */
    fun next(): Node? = try {
        nextRow()
    } catch (e: CsvException) {
        throw e
    } catch (e: Exception) {
        throw CsvParseException("Invalid CSV: ${e.message}", e)
    }

    private fun nextRow(): Node? {
        if (!initialized) {
            initialized = true
            initializeHeader()
        }
        if (!hasNextRecord()) {
            return null
        }
        val record = nextRecord()
        val index = rowIndex
        rowIndex++
        val structured = schemaDecoder
        return if (structured != null) {
            structured.readRow(record, index)
        } else {
            plainDecoder?.readRow(record)
        }
    }

    private fun initializeHeader() {
        if (!headerEnabled) {
            schemaDecoder?.positionalLayout()
            return
        }
        if (!hasNextRecord()) {
            return
        }
        val headerRecord = nextRecord()
        val structured = schemaDecoder
        if (structured != null) {
            structured.readHeader(headerRecord)
        } else {
            plainDecoder?.readHeader(headerRecord)
        }
    }

    private fun hasNextRecord(): Boolean = records.hasNext()

    private fun nextRecord(): CSVRecord = records.next()
}
