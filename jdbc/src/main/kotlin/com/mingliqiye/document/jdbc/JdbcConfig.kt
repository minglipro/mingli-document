package com.mingliqiye.document.jdbc

import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.sql.Types
import java.util.Collections
import java.util.LinkedHashMap

/**
 * JDBC 读写配置。
 *
 * 负责列名与文档字段名的映射、SQL NULL 策略、未知/null 参数绑定使用的 SQL 类型提示，
 * 以及自定义列转换器与 Java 类型 -> JDBC 类型映射。
 *
 * 命名策略同时用于两个方向：
 * - [columnToFieldName]：读取时把列名转换为文档字段名；
 * - [fieldToColumnName]：生成 `INSERT`/`UPDATE` 时把文档字段名转换为列名。
 *
 * [namingStrategy] 不是 [PropertyNamingStrategy.AS_IS] 时，列名按“拆分单词后转为
 * lowerCamelCase”反向解析（`USER_NAME`、`user-name`、`UserName` 都解析为 `userName`），
 * 因此可以覆盖常见的数据库列名风格；需要特殊映射时使用
 * [JdbcConfigBuilder.columnMapping]。
 *
 * @property namingStrategy 列名 <-> 文档字段名命名策略，默认
 * [PropertyNamingStrategy.AS_IS]
 * @property nullPolicy SQL NULL 处理策略，默认 [JdbcNullPolicy.INCLUDE]
 * @property defaultNullSqlType 驱动未提供参数元数据时绑定 SQL NULL 使用的
 * [java.sql.Types] 常量，默认 [Types.NULL]
 * @property columnMappings 显式列名映射：列名 -> 文档字段名
 * @property typeMappings Java 类型 -> [java.sql.Types] 常量，用于非 null 参数绑定时
 * 覆盖 `setObject` 的目标 SQL 类型
 */
public class JdbcConfig internal constructor(
    public val namingStrategy: PropertyNamingStrategy,
    public val nullPolicy: JdbcNullPolicy,
    public val defaultNullSqlType: Int,
    columnMappings: Map<String, String>,
    private val convertersByLabel: Map<String, ColumnConverter>,
    private val convertersByIndex: Map<Int, ColumnConverter>,
    typeMappings: Map<Class<*>, Int>,
) {

    /** 显式列名映射：列名 -> 文档字段名。 */
    public val columnMappings: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(columnMappings))

    /** Java 类型 -> [java.sql.Types] 常量映射。 */
    public val typeMappings: Map<Class<*>, Int> = Collections.unmodifiableMap(LinkedHashMap(typeMappings))

    /**
     * 把列名转换为文档字段名。
     *
     * 显式 [columnMappings] 优先；否则按 [namingStrategy] 反向转换（拆分单词后转为
     * lowerCamelCase），[PropertyNamingStrategy.AS_IS] 时原样返回。
     *
     * @param columnLabel 列名/列标签
     * @return 文档字段名
     */
    public fun columnToFieldName(columnLabel: String): String {
        columnMappings[columnLabel]?.let { return it }
        if (namingStrategy == PropertyNamingStrategy.AS_IS) {
            return columnLabel
        }
        return PropertyNamingStrategy.LOWER_CAMEL.translate(columnLabel)
    }

    /**
     * 把文档字段名转换为列名。
     *
     * 显式 [columnMappings] 中存在相同目标字段名时优先使用其列名；否则按
     * [namingStrategy] 转换，[PropertyNamingStrategy.AS_IS] 时原样返回。
     *
     * @param fieldName 文档字段名
     * @return 列名
     */
    public fun fieldToColumnName(fieldName: String): String {
        for ((columnLabel, mappedField) in columnMappings) {
            if (mappedField == fieldName) {
                return columnLabel
            }
        }
        if (namingStrategy == PropertyNamingStrategy.AS_IS) {
            return fieldName
        }
        return namingStrategy.translate(fieldName)
    }

    /**
     * 解析 Java 类型对应的 JDBC 类型。
     *
     * 精确匹配优先，其次按父类/接口递归查找。
     *
     * @param javaType Java 类型；`null` 返回 `null`
     * @return [java.sql.Types] 常量；未配置时返回 `null`
     */
    public fun sqlTypeFor(javaType: Class<*>?): Int? = findTypeMapping(javaType, HashSet())

    private fun findTypeMapping(javaType: Class<*>?, visited: MutableSet<Class<*>>): Int? {
        if (javaType == null || !visited.add(javaType)) {
            return null
        }
        typeMappings[javaType]?.let { return it }
        findTypeMapping(javaType.superclass, visited)?.let { return it }
        for (interfaceType in javaType.interfaces) {
            findTypeMapping(interfaceType, visited)?.let { return it }
        }
        return null
    }

    /**
     * 返回指定列注册的自定义转换器。
     *
     * 列下标优先于列标签；列标签精确匹配优先于忽略大小写匹配。
     */
    internal fun converterFor(column: JdbcColumn): ColumnConverter? {
        convertersByIndex[column.index]?.let { return it }
        convertersByLabel[column.label]?.let { return it }
        for ((label, converter) in convertersByLabel) {
            if (label.equals(column.label, ignoreCase = true)) {
                return converter
            }
        }
        return null
    }

    public companion object {

        /**
         * 创建 JDBC 配置构建器。
         */
        @JvmStatic
        public fun builder(): JdbcConfigBuilder = JdbcConfigBuilder()
    }
}
