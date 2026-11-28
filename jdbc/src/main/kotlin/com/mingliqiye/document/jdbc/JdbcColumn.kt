package com.mingliqiye.document.jdbc

import java.sql.Types

/**
 * `ResultSet` 列元数据。
 *
 * @param index 1 起始的列下标
 * @param label 列标签（驱动优先返回别名，没有别名时通常是列名）
 * @param sqlType 列类型，取值为 [java.sql.Types] 中的常量
 * @param columnClassName 驱动返回的 Java 类名；未知时为 `null`
 */
public class JdbcColumn public constructor(
    public val index: Int,
    public val label: String,
    public val sqlType: Int,
    public val columnClassName: String? = null,
) {

    init {
        require(index >= 1) { "index must be >= 1" }
        require(label.isNotEmpty()) { "label must not be empty" }
    }

    override fun toString(): String = "JdbcColumn(index=$index, label='$label', sqlType=${typeName(sqlType)}, columnClassName=$columnClassName)"
}

/** 返回 [java.sql.Types] 常量的可读名称；未知时返回数字。 */
internal fun typeName(sqlType: Int): String = when (sqlType) {
    Types.BIT -> "BIT"
    Types.TINYINT -> "TINYINT"
    Types.SMALLINT -> "SMALLINT"
    Types.INTEGER -> "INTEGER"
    Types.BIGINT -> "BIGINT"
    Types.FLOAT -> "FLOAT"
    Types.REAL -> "REAL"
    Types.DOUBLE -> "DOUBLE"
    Types.NUMERIC -> "NUMERIC"
    Types.DECIMAL -> "DECIMAL"
    Types.CHAR -> "CHAR"
    Types.VARCHAR -> "VARCHAR"
    Types.LONGVARCHAR -> "LONGVARCHAR"
    Types.DATE -> "DATE"
    Types.TIME -> "TIME"
    Types.TIMESTAMP -> "TIMESTAMP"
    Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP_WITH_TIMEZONE"
    Types.TIME_WITH_TIMEZONE -> "TIME_WITH_TIMEZONE"
    Types.BINARY -> "BINARY"
    Types.VARBINARY -> "VARBINARY"
    Types.LONGVARBINARY -> "LONGVARBINARY"
    Types.NULL -> "NULL"
    Types.OTHER -> "OTHER"
    Types.JAVA_OBJECT -> "JAVA_OBJECT"
    Types.DISTINCT -> "DISTINCT"
    Types.STRUCT -> "STRUCT"
    Types.ARRAY -> "ARRAY"
    Types.BLOB -> "BLOB"
    Types.CLOB -> "CLOB"
    Types.REF -> "REF"
    Types.DATALINK -> "DATALINK"
    Types.BOOLEAN -> "BOOLEAN"
    Types.ROWID -> "ROWID"
    Types.NCHAR -> "NCHAR"
    Types.NVARCHAR -> "NVARCHAR"
    Types.LONGNVARCHAR -> "LONGNVARCHAR"
    Types.NCLOB -> "NCLOB"
    Types.SQLXML -> "SQLXML"
    Types.REF_CURSOR -> "REF_CURSOR"
    else -> sqlType.toString()
}
