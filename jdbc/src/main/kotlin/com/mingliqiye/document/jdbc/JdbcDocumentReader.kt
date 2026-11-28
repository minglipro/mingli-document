package com.mingliqiye.document.jdbc

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.exception.DocumentException
import com.mingliqiye.document.jdbc.exception.JdbcReadException
import com.mingliqiye.document.jdbc.internal.ColumnConversions
import com.mingliqiye.document.serialization.DocumentMapper
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.util.LinkedHashMap

/**
 * `ResultSet` 读取器：把查询结果逐行转换为对象节点或 Bean。
 *
 * 读取规则：
 * - 列名按 [JdbcConfig.columnToFieldName] 转换为文档字段名；显式
 *   [JdbcConfigBuilder.columnMapping] 优先；
 * - 列类型按 `ResultSetMetaData` 与驱动返回的 Java 值推断：布尔、数字（BigDecimal）、
 *   字符串、二进制（[com.mingliqiye.document.core.BinaryNode]）、日期/时间
 *   （ISO-8601 字符串节点）；
 * - 值为 SQL NULL 的列按 [JdbcConfig.nullPolicy] 生成 NullNode 或跳过；
 * - 注册了 [ColumnConverter] 的列优先使用自定义转换。
 *
 * 读取器不关闭 `ResultSet`，也不管理 `Connection`/事务，调用方负责。
 *
 * @param config JDBC 读写配置
 */
public class JdbcDocumentReader @JvmOverloads public constructor(
    public val config: JdbcConfig = JdbcConfig.builder().build(),
) {

    /**
     * 读取全部行，返回对象节点列表。
     *
     * @param resultSet 查询结果；调用前需已定位在第一条记录之前
     * @return 每行一个 [ObjectNode]
     * @throws JdbcReadException `ResultSet` 访问或列值转换失败时抛出
     */
    public fun readNodes(resultSet: ResultSet): List<ObjectNode> = read(resultSet, DocumentRowMapper.nodes())

    /**
     * 读取当前行，返回对象节点。
     *
     * 调用方需自行把游标移动到目标行（例如调用一次 `next()`）。
     *
     * @param resultSet 查询结果，当前行有效
     * @return 当前行的对象节点
     * @throws JdbcReadException `ResultSet` 访问或列值转换失败时抛出
     */
    public fun readRow(resultSet: ResultSet): ObjectNode = readCurrentRow(resultSet)

    /**
     * 读取当前行并按行映射器转换。
     *
     * @param resultSet 查询结果，当前行有效
     * @param rowMapper 行映射器
     * @return 映射结果
     * @throws JdbcReadException `ResultSet` 访问或行映射失败时抛出
     */
    public fun <T> mapRow(resultSet: ResultSet, rowMapper: DocumentRowMapper<T>): T = mapCurrentRow(resultSet, rowMapper, 0L)

    /**
     * 读取当前行并按 [DocumentMapper] 反序列化为 Bean。
     *
     * @param resultSet 查询结果，当前行有效
     * @param documentMapper 文档映射器
     * @param targetType 目标类型
     * @return Bean
     * @throws JdbcReadException 行映射失败时抛出
     */
    public fun <T> mapRow(resultSet: ResultSet, documentMapper: DocumentMapper, targetType: Class<T>): T = mapCurrentRow(
        resultSet,
        DocumentRowMapper.of(targetType, documentMapper),
        0L,
    )

    /**
     * 读取当前行并按 [DocumentMapper] 反序列化为泛型类型。
     *
     * @param resultSet 查询结果，当前行有效
     * @param documentMapper 文档映射器
     * @param targetType 目标泛型类型
     * @return Bean
     * @throws JdbcReadException 行映射失败时抛出
     */
    public fun <T> mapRow(
        resultSet: ResultSet,
        documentMapper: DocumentMapper,
        targetType: DocumentTypeReference<T>,
    ): T = mapCurrentRow(resultSet, DocumentRowMapper.of(targetType, documentMapper), 0L)

    /**
     * 读取全部行并按行映射器转换。
     *
     * @param resultSet 查询结果；调用前需已定位在第一条记录之前
     * @param rowMapper 行映射器
     * @return 每行一个映射结果
     * @throws JdbcReadException `ResultSet` 访问或行映射失败时抛出
     */
    public fun <T> read(resultSet: ResultSet, rowMapper: DocumentRowMapper<T>): List<T> {
        val rows = ArrayList<T>()
        var rowIndex = 0L
        while (true) {
            val hasNext = try {
                resultSet.next()
            } catch (e: SQLException) {
                throw JdbcReadException("读取 ResultSet 下一行失败（第 ${rowIndex + 1} 行）", e)
            }
            if (!hasNext) {
                return rows
            }
            rows.add(mapCurrentRow(resultSet, rowMapper, rowIndex))
            rowIndex++
        }
    }

    /**
     * 读取全部行并按 [DocumentMapper] 反序列化为 Bean。
     *
     * @param resultSet 查询结果；调用前需已定位在第一条记录之前
     * @param documentMapper 文档映射器
     * @param targetType 目标类型
     * @return Bean 列表
     * @throws JdbcReadException `ResultSet` 访问或行映射失败时抛出
     */
    public fun <T> read(resultSet: ResultSet, documentMapper: DocumentMapper, targetType: Class<T>): List<T> = read(
        resultSet,
        DocumentRowMapper.of(targetType, documentMapper),
    )

    /**
     * 读取全部行并按 [DocumentMapper] 反序列化为泛型类型。
     *
     * @param resultSet 查询结果；调用前需已定位在第一条记录之前
     * @param documentMapper 文档映射器
     * @param targetType 目标泛型类型
     * @return Bean 列表
     * @throws JdbcReadException `ResultSet` 访问或行映射失败时抛出
     */
    public fun <T> read(
        resultSet: ResultSet,
        documentMapper: DocumentMapper,
        targetType: DocumentTypeReference<T>,
    ): List<T> = read(resultSet, DocumentRowMapper.of(targetType, documentMapper))

    private fun <T> mapCurrentRow(resultSet: ResultSet, rowMapper: DocumentRowMapper<T>, rowIndex: Long): T {
        val row = readCurrentRow(resultSet)
        return try {
            rowMapper.mapRow(row)
        } catch (e: DocumentException) {
            throw e
        } catch (e: Exception) {
            throw JdbcReadException("行映射失败（第 ${rowIndex + 1} 行）", e)
        }
    }

    private fun readCurrentRow(resultSet: ResultSet): ObjectNode {
        val metadata = try {
            resultSet.metaData
        } catch (e: SQLException) {
            throw JdbcReadException("读取 ResultSet 元数据失败", e)
        }
        val columnCount = try {
            metadata.columnCount
        } catch (e: SQLException) {
            throw JdbcReadException("读取 ResultSet 列数量失败", e)
        }
        if (columnCount == 0) {
            return ObjectNode.EMPTY
        }
        val fields = LinkedHashMap<String, Node>(columnCount)
        for (index in 1..columnCount) {
            val column = readColumn(metadata, index)
            val node = readColumnNode(resultSet, column) ?: continue
            fields[config.columnToFieldName(column.label)] = node
        }
        return if (fields.isEmpty()) ObjectNode.EMPTY else ObjectNode.from(fields)
    }

    private fun readColumn(metadata: ResultSetMetaData, index: Int): JdbcColumn = try {
        val label = metadata.getColumnLabel(index).takeIf { it.isNotEmpty() } ?: metadata.getColumnName(index)
        JdbcColumn(
            index,
            label,
            metadata.getColumnType(index),
            metadata.getColumnClassName(index),
        )
    } catch (e: SQLException) {
        throw JdbcReadException("读取第 $index 列元数据失败", e)
    }

    private fun readColumnNode(resultSet: ResultSet, column: JdbcColumn): Node? {
        val rawValue = try {
            resultSet.getObject(column.index)
        } catch (e: SQLException) {
            throw JdbcReadException("读取列 '${column.label}' 失败", e)
        }
        val converter = config.converterFor(column)
        if (converter != null) {
            val converted = try {
                converter.toNode(rawValue, column)
            } catch (e: DocumentException) {
                throw e
            } catch (e: Exception) {
                throw JdbcReadException("列 '${column.label}' 的自定义转换器执行失败", e)
            }
            if (converted != null) {
                return converted
            }
        }
        if (rawValue == null) {
            return when (config.nullPolicy) {
                JdbcNullPolicy.INCLUDE -> NullNode.INSTANCE
                JdbcNullPolicy.OMIT -> null
            }
        }
        return try {
            ColumnConversions.toNode(resultSet, rawValue, column)
        } catch (e: SQLException) {
            throw JdbcReadException("读取列 '${column.label}' 的值失败", e)
        } catch (e: DocumentException) {
            throw e
        } catch (e: Exception) {
            throw JdbcReadException("列 '${column.label}' 的值无法转换为 Node", e)
        }
    }
}
