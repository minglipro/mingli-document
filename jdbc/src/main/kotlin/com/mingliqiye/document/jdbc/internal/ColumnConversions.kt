package com.mingliqiye.document.jdbc.internal

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jdbc.JdbcColumn
import com.mingliqiye.document.jdbc.exception.JdbcWriteException
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Blob
import java.sql.Clob
import java.sql.ResultSet
import java.sql.SQLXML
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

/**
 * `ResultSet` 列值与 [Node] 之间的内置转换，以及写出时的类型判断辅助。
 */
internal object ColumnConversions {

    /**
     * 把非 null 的列原始值转换为节点。
     *
     * 日期/时间统一输出 ISO-8601 字符串节点；二进制输出
     * [com.mingliqiye.document.core.BinaryNode]；布尔列即使驱动返回数字也转换为
     * [com.mingliqiye.document.core.BooleanNode]；无法识别的类型回退为
     * `toString()` 字符串节点。
     */
    internal fun toNode(resultSet: ResultSet, value: Any, column: JdbcColumn): Node {
        if (isBooleanSqlType(column.sqlType) && value is Number) {
            return BooleanNode.of(value.toLong() != 0L)
        }
        return when (value) {
            is Boolean -> BooleanNode.of(value)
            is CharSequence -> StringNode.of(value.toString())
            is Char -> StringNode.of(value.toString())
            is ByteArray -> BinaryNode.of(value)
            is Double -> NumberNode.of(value)
            is Float -> NumberNode.of(value)
            is BigDecimal -> NumberNode.of(value)
            is BigInteger -> NumberNode.of(value)
            is Number -> NumberNode.of(BigDecimal(value.toString()))
            is Blob -> BinaryNode.of(readBlob(value))
            is Clob -> StringNode.of(readClob(value))
            is SQLXML -> StringNode.of(value.getString())
            is UUID -> StringNode.of(value.toString())
            is Enum<*> -> StringNode.of(value.name)
            is Node -> value
            else -> toFallbackNode(resultSet, value, column)
        }
    }

    private fun toFallbackNode(resultSet: ResultSet, value: Any, column: JdbcColumn): Node {
        NodeConversions.formatTemporal(value)?.let { return StringNode.of(it) }
        if (isBinarySqlType(column.sqlType)) {
            resultSet.getBytes(column.index)?.let { return BinaryNode.of(it) }
        }
        if (isCharacterLobSqlType(column.sqlType)) {
            resultSet.getString(column.index)?.let { return StringNode.of(it) }
        }
        return StringNode.of(value.toString())
    }

    private fun readClob(clob: Clob): String = clob.characterStream.use { it.readText() }

    private fun readBlob(blob: Blob): ByteArray = blob.binaryStream.use { it.readBytes() }

    /** 是否为布尔列类型。 */
    internal fun isBooleanSqlType(sqlType: Int): Boolean = when (sqlType) {
        Types.BOOLEAN, Types.BIT -> true
        else -> false
    }

    /** 是否为二进制列类型。 */
    internal fun isBinarySqlType(sqlType: Int): Boolean = when (sqlType) {
        Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> true
        else -> false
    }

    /** 是否为字符大对象列类型。 */
    internal fun isCharacterLobSqlType(sqlType: Int): Boolean = when (sqlType) {
        Types.CLOB, Types.NCLOB, Types.SQLXML, Types.LONGVARCHAR, Types.LONGNVARCHAR -> true
        else -> false
    }

    /**
     * 按目标 SQL 类型把 ISO-8601 字符串转换为 JDBC 日期/时间值。
     *
     * 无法解析或不是日期/时间列类型时返回 `null`，由调用方回退字符串绑定。
     */
    internal fun parseTemporalText(text: String, sqlType: Int): Any? = try {
        when (sqlType) {
            Types.DATE -> java.sql.Date.valueOf(LocalDate.parse(text))
            Types.TIME -> java.sql.Time.valueOf(LocalTime.parse(text))
            Types.TIMESTAMP -> java.sql.Timestamp.valueOf(LocalDateTime.parse(text))
            Types.TIMESTAMP_WITH_TIMEZONE -> OffsetDateTime.parse(text)
            else -> null
        }
    } catch (e: RuntimeException) {
        null
    }

    /**
     * 把节点值转换为可绑定的 JDBC 参数对象。
     *
     * 对象/数组节点不支持直接绑定，抛出 [JdbcWriteException]；
     * null 节点返回 `null`。
     */
    internal fun nodeToJdbcValue(node: Node, fieldName: String): Any? = when (node) {
        is NullNode -> null
        is BooleanNode -> node.value
        is NumberNode -> node.value
        is StringNode -> node.value
        is BinaryNode -> node.value
        is ArrayNode, is ObjectNode -> throw JdbcWriteException(
            "字段 '$fieldName' 的值是 ${node.type} 节点，无法绑定为 JDBC 参数；请先转换为标量",
        )

        else -> throw JdbcWriteException("字段 '$fieldName' 的节点类型 ${node.type} 无法绑定为 JDBC 参数")
    }
}
