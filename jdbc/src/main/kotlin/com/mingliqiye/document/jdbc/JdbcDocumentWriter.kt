package com.mingliqiye.document.jdbc

import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jdbc.exception.JdbcWriteException
import com.mingliqiye.document.jdbc.internal.ColumnConversions
import com.mingliqiye.document.jdbc.internal.NamedParameters
import com.mingliqiye.document.jdbc.internal.NodeConversions
import com.mingliqiye.document.serialization.DocumentMapper
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Types
import java.util.ArrayList
import java.util.LinkedHashMap

/**
 * `PreparedStatement` 参数绑定器：把 Bean、Map 或对象节点写入 JDBC 参数。
 *
 * 三种输入：
 * - [ObjectNode]：直接按文档字段名取值；
 * - `Map`：key 必须是 `String`，值按标量/日期/二进制/Map/集合规则转换为节点，其它对象
 *   依赖构造时传入的 [DocumentMapper]；
 * - 其它对象：使用构造时传入的 [DocumentMapper] 序列化为对象节点。
 *
 * 支持两种占位符顺序：
 * - 顺序参数：调用方给出参数列表（列名/参数名），按列表顺序绑定 `?`；
 * - 命名参数：从 SQL 的 `:name` 占位符解析顺序（[namedStatement]），再按名称绑定。
 *
 * 参数名通过 [JdbcConfig.columnToFieldName] 解析为对象字段名；SQL 生成
 * （[insertStatement] / [updateStatement]）使用 [JdbcConfig.fieldToColumnName] 生成列名。
 *
 * 绑定规则：
 * - NullNode 以 `setNull` 绑定，SQL 类型优先取 `ParameterMetaData`，驱动不支持时使用
 *   [JdbcConfig.defaultNullSqlType]；[JdbcConfig.nullPolicy] 为
 *   [JdbcNullPolicy.OMIT] 时抛出 [JdbcWriteException]；
 * - 数字按参数 SQL 类型收窄为 Byte/Short/Int/Long，无法精确收窄时保留 BigDecimal；
 * - 字符串在目标列是 DATE/TIME/TIMESTAMP 时按 ISO-8601 解析后绑定；
 * - 二进制以 `setBytes` 绑定；
 * - 对象/数组节点无法直接绑定，抛出 [JdbcWriteException]。
 *
 * 写入器不关闭 `PreparedStatement`，也不执行 SQL，调用方负责。
 *
 * @param documentMapper 可选文档映射器，用于 Bean/复杂 Map 值转换
 * @param config JDBC 读写配置
 */
public class JdbcDocumentWriter public constructor(
    private val documentMapper: DocumentMapper?,
    public val config: JdbcConfig,
) {

    /** 创建不带 [DocumentMapper] 的写入器（只能写 Map 或对象节点）。 */
    public constructor() : this(null, JdbcConfig.builder().build())

    /** 创建使用 [documentMapper] 的写入器。 */
    public constructor(documentMapper: DocumentMapper) : this(documentMapper, JdbcConfig.builder().build())

    /** 创建使用 [config] 的写入器（只能写 Map 或对象节点）。 */
    public constructor(config: JdbcConfig) : this(null, config)

    /**
     * 创建命名参数语句：解析 SQL 中 `:name` 占位符顺序。
     *
     * 解析跳过字符串字面量、引号标识符、注释与 `::` 类型转换；不支持 PostgreSQL 的
     * dollar-quoted 字符串。
     *
     * @param sql 含 `:name` 占位符的 SQL
     * @return 语句与参数名
     */
    public fun namedStatement(sql: String): JdbcStatement = JdbcStatement(sql, NamedParameters.parse(sql))

    /**
     * 生成 `INSERT` 语句（占位符为 `?`）。
     *
     * @param table 目标表名（由调用方提供，不参与参数化）
     * @param value 待写入对象：Bean、Map 或对象节点
     * @param columns 写入列（文档字段名）；`null` 时取对象字段顺序
     * @return SQL 与参数名（文档字段名）
     * @throws JdbcWriteException 对象无法转换、没有可写列或恰为 null 的列全部被跳过时抛出
     */
    public fun insertStatement(table: String, value: Any?, columns: List<String>? = null): JdbcStatement {
        requireTable(table)
        val node = toObjectNode(value)
        val requested = columns ?: node.keys.toList()
        val parameters = requested.filter { field -> !isOmittedNull(node, field) }
        if (parameters.isEmpty()) {
            throw JdbcWriteException("INSERT 没有可写入的列：$table")
        }
        val columnSql = parameters.joinToString(", ") { config.fieldToColumnName(it) }
        val placeholderSql = parameters.joinToString(", ") { "?" }
        return JdbcStatement("INSERT INTO $table ($columnSql) VALUES ($placeholderSql)", parameters)
    }

    /**
     * 生成 `UPDATE` 语句（占位符为 `?`，`SET` 列在前、`WHERE` 列在后）。
     *
     * @param table 目标表名（由调用方提供，不参与参数化）
     * @param value 待写入对象：Bean、Map 或对象节点
     * @param whereColumns `WHERE` 条件列（文档字段名），必须非空
     * @param setColumns `SET` 列（文档字段名）；`null` 时取除 `WHERE` 列外的全部字段
     * @return SQL 与参数名（`SET` 列 + `WHERE` 列）
     * @throws JdbcWriteException 对象无法转换、没有 `WHERE` 列或没有可更新列时抛出
     */
    public fun updateStatement(
        table: String,
        value: Any?,
        whereColumns: List<String>,
        setColumns: List<String>? = null,
    ): JdbcStatement {
        requireTable(table)
        if (whereColumns.isEmpty()) {
            throw JdbcWriteException("UPDATE 的 WHERE 列不能为空：$table")
        }
        val node = toObjectNode(value)
        val requested = setColumns ?: node.keys.filter { it !in whereColumns }
        val setFields = requested.filter { field -> !isOmittedNull(node, field) }
        if (setFields.isEmpty()) {
            throw JdbcWriteException("UPDATE 没有可更新的列：$table")
        }
        val parameters = ArrayList<String>(setFields.size + whereColumns.size)
        parameters.addAll(setFields)
        parameters.addAll(whereColumns)
        val setSql = setFields.joinToString(", ") { "${config.fieldToColumnName(it)} = ?" }
        val whereSql = whereColumns.joinToString(" AND ") { "${config.fieldToColumnName(it)} = ?" }
        return JdbcStatement("UPDATE $table SET $setSql WHERE $whereSql", parameters)
    }

    /**
     * 按 [JdbcStatement] 的参数顺序绑定参数。
     *
     * @param statement 已按 [JdbcStatement.sql] 预编译的语句
     * @param sqlStatement 语句与参数名
     * @param value 待写入对象：Bean、Map 或对象节点
     * @return 绑定的参数数量
     * @throws JdbcWriteException 对象缺少字段、值无法绑定或 JDBC 调用失败时抛出
     */
    public fun bind(statement: PreparedStatement, sqlStatement: JdbcStatement, value: Any?): Int = bind(
        statement,
        sqlStatement.parameters,
        value,
    )

    /**
     * 按给定参数名顺序绑定参数。
     *
     * @param statement 已按相同顺序预编译的语句
     * @param parameters 参数名（列名/参数名），允许重复
     * @param value 待写入对象：Bean、Map 或对象节点
     * @return 绑定的参数数量
     * @throws JdbcWriteException 对象缺少字段、值无法绑定或 JDBC 调用失败时抛出
     */
    public fun bind(statement: PreparedStatement, parameters: List<String>, value: Any?): Int {
        val node = toObjectNode(value)
        var bound = 0
        for ((offset, parameter) in parameters.withIndex()) {
            val fieldName = config.columnToFieldName(parameter)
            val fieldValue = node[fieldName]
                ?: throw JdbcWriteException("对象缺少参数 '$parameter' 对应的字段 '$fieldName'")
            bindValue(statement, offset + 1, parameter, fieldName, fieldValue)
            bound++
        }
        return bound
    }

    /**
     * 解析 SQL 中的 `:name` 占位符并按名称绑定参数。
     *
     * @param statement 已按 [sql] 预编译的语句
     * @param sql 含 `:name` 占位符的 SQL
     * @param value 待写入对象：Bean、Map 或对象节点
     * @return 绑定的参数数量
     * @throws JdbcWriteException 对象缺少字段、值无法绑定或 JDBC 调用失败时抛出
     */
    public fun bindNamed(statement: PreparedStatement, sql: String, value: Any?): Int = bind(
        statement,
        namedStatement(sql),
        value,
    )

    private fun bindValue(
        statement: PreparedStatement,
        index: Int,
        parameter: String,
        fieldName: String,
        node: Node,
    ) {
        val jdbcValue = ColumnConversions.nodeToJdbcValue(node, fieldName)
        val parameterType = parameterSqlType(statement, index)
        try {
            when (node) {
                is NullNode -> bindNull(statement, index, parameter, parameterType)
                is StringNode -> {
                    val temporal = parameterType?.let { ColumnConversions.parseTemporalText(node.value, it) }
                    if (temporal != null) {
                        bindObject(statement, index, temporal)
                    } else {
                        bindObject(statement, index, jdbcValue)
                    }
                }

                is NumberNode -> bindObject(statement, index, convertNumber(node.value, parameterType))
                is BinaryNode -> statement.setBytes(index, jdbcValue as ByteArray)
                else -> bindObject(statement, index, jdbcValue)
            }
        } catch (e: SQLException) {
            throw JdbcWriteException("绑定参数 $index（'$parameter'）失败", e)
        }
    }

    private fun bindObject(statement: PreparedStatement, index: Int, value: Any?) {
        val sqlType = config.sqlTypeFor(value?.javaClass)
        if (sqlType != null) {
            statement.setObject(index, value, sqlType)
        } else {
            statement.setObject(index, value)
        }
    }

    private fun bindNull(statement: PreparedStatement, index: Int, parameter: String, parameterType: Int?) {
        if (config.nullPolicy == JdbcNullPolicy.OMIT) {
            throw JdbcWriteException(
                "参数 '$parameter' 的值为 null，nullPolicy=OMIT 无法显式绑定 null；请改用 JdbcNullPolicy.INCLUDE",
            )
        }
        statement.setNull(index, parameterType ?: config.defaultNullSqlType)
    }

    private fun parameterSqlType(statement: PreparedStatement, index: Int): Int? = try {
        val sqlType = statement.parameterMetaData?.getParameterType(index) ?: Types.NULL
        when (sqlType) {
            Types.NULL, Types.OTHER -> null
            else -> sqlType
        }
    } catch (e: Exception) {
        null
    }

    private fun convertNumber(value: BigDecimal, parameterType: Int?): Any = when (parameterType) {
        Types.TINYINT -> exactOrFallback(value) { it.byteValueExact() }
        Types.SMALLINT -> exactOrFallback(value) { it.shortValueExact() }
        Types.INTEGER -> exactOrFallback(value) { it.intValueExact() }
        Types.BIGINT -> exactOrFallback(value) { it.longValueExact() }
        else -> value
    }

    private inline fun exactOrFallback(value: BigDecimal, convert: (BigDecimal) -> Any): Any = try {
        convert(value)
    } catch (e: ArithmeticException) {
        value
    }

    private fun toObjectNode(value: Any?): ObjectNode = when (value) {
        null -> throw JdbcWriteException("待写入的对象不能为 null")
        is ObjectNode -> value
        is Node -> throw JdbcWriteException("待写入的值必须是对象节点，实际为 ${value.type}")
        is Map<*, *> -> mapToObjectNode(value)
        else -> beanToObjectNode(value)
    }

    private fun mapToObjectNode(map: Map<*, *>): ObjectNode {
        val fields = LinkedHashMap<String, Node>(map.size)
        for ((key, rawValue) in map) {
            val fieldName = key as? String
                ?: throw JdbcWriteException("Map 的 key 必须是 String，实际为 ${key?.javaClass?.name ?: "null"}")
            fields[fieldName] = NodeConversions.fromJavaValue(rawValue, documentMapper)
        }
        return ObjectNode.from(fields)
    }

    private fun beanToObjectNode(value: Any): ObjectNode {
        val mapper = documentMapper
            ?: throw JdbcWriteException("写入 Bean（${value.javaClass.name}）需要构造函数传入 DocumentMapper")
        val node = mapper.toNode(value)
        return node.asObjectOrNull()
            ?: throw JdbcWriteException("Bean ${value.javaClass.name} 序列化为 ${node.type}，无法作为一行写入")
    }

    private fun isOmittedNull(node: ObjectNode, fieldName: String): Boolean = config.nullPolicy == JdbcNullPolicy.OMIT && node[fieldName] is NullNode

    private fun requireTable(table: String) {
        if (table.isBlank()) {
            throw JdbcWriteException("表名不能为空")
        }
    }
}
