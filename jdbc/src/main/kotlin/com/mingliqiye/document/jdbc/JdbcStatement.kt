package com.mingliqiye.document.jdbc

import java.util.ArrayList
import java.util.Collections

/**
 * 参数化 SQL 语句：SQL 文本与按占位符顺序排列的参数名。
 *
 * 参数名在绑定阶段通过 [JdbcConfig.columnToFieldName] 解析为对象字段名；
 * [JdbcDocumentWriter.insertStatement] / [JdbcDocumentWriter.updateStatement] 生成的
 * [parameters] 保存的是文档字段名，[JdbcDocumentWriter.namedStatement] 保存的是 SQL 中
 * `:name` 形式的参数名。
 *
 * 该类只描述 SQL 与参数顺序，不执行语句；执行由调用方使用
 * `PreparedStatement.executeUpdate` 等 JDBC API 完成。
 *
 * @property sql SQL 文本
 * @property parameters 与占位符顺序一致的参数名
 */
public class JdbcStatement public constructor(
    public val sql: String,
    parameters: List<String>,
) {

    /** 与占位符顺序一致的参数名（只读副本）。 */
    public val parameters: List<String> = Collections.unmodifiableList(ArrayList(parameters))

    init {
        require(sql.isNotBlank()) { "sql must not be blank" }
        for (parameter in parameters) {
            require(parameter.isNotEmpty()) { "parameter name must not be empty" }
        }
    }

    override fun toString(): String = "JdbcStatement(sql='$sql', parameters=$parameters)"
}
