package com.mingliqiye.document.jdbc

import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.util.LinkedHashMap

/**
 * [JdbcConfig] 构建器。
 */
public class JdbcConfigBuilder {

    private var namingStrategy: PropertyNamingStrategy = PropertyNamingStrategy.AS_IS
    private var nullPolicy: JdbcNullPolicy = JdbcNullPolicy.INCLUDE
    private var defaultNullSqlType: Int = DEFAULT_NULL_SQL_TYPE
    private val columnMappings = LinkedHashMap<String, String>()
    private val convertersByLabel = LinkedHashMap<String, ColumnConverter>()
    private val convertersByIndex = LinkedHashMap<Int, ColumnConverter>()
    private val typeMappings = LinkedHashMap<Class<*>, Int>()

    /**
     * 设置列名 <-> 文档字段名命名策略。
     *
     * @param strategy 命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): JdbcConfigBuilder {
        namingStrategy = strategy
        return this
    }

    /**
     * 设置 SQL NULL 处理策略。
     *
     * @param policy null 策略
     * @return 当前构建器
     */
    public fun nullPolicy(policy: JdbcNullPolicy): JdbcConfigBuilder {
        nullPolicy = policy
        return this
    }

    /**
     * 设置驱动未提供参数元数据时绑定 SQL NULL 使用的 JDBC 类型。
     *
     * @param sqlType [java.sql.Types] 常量
     * @return 当前构建器
     */
    public fun defaultNullSqlType(sqlType: Int): JdbcConfigBuilder {
        defaultNullSqlType = sqlType
        return this
    }

    /**
     * 注册显式列名映射（列名 -> 文档字段名），优先于 [namingStrategy]。
     *
     * @param columnLabel 列名/列标签
     * @param fieldName 文档字段名
     * @return 当前构建器
     */
    public fun columnMapping(columnLabel: String, fieldName: String): JdbcConfigBuilder {
        require(columnLabel.isNotEmpty()) { "columnLabel must not be empty" }
        require(fieldName.isNotEmpty()) { "fieldName must not be empty" }
        columnMappings[columnLabel] = fieldName
        return this
    }

    /**
     * 为指定列注册自定义转换器，优先于内置类型推断。
     *
     * @param columnLabel 列名/列标签
     * @param converter 列转换器
     * @return 当前构建器
     */
    public fun columnConverter(columnLabel: String, converter: ColumnConverter): JdbcConfigBuilder {
        require(columnLabel.isNotEmpty()) { "columnLabel must not be empty" }
        convertersByLabel[columnLabel] = converter
        return this
    }

    /**
     * 为指定列下标注册自定义转换器。
     *
     * 列下标优先于列标签。
     *
     * @param columnIndex 1 起始的列下标
     * @param converter 列转换器
     * @return 当前构建器
     */
    public fun columnConverter(columnIndex: Int, converter: ColumnConverter): JdbcConfigBuilder {
        require(columnIndex >= 1) { "columnIndex must be >= 1" }
        convertersByIndex[columnIndex] = converter
        return this
    }

    /**
     * 注册 Java 类型 -> JDBC 类型映射。
     *
     * 写出非 null 参数时，若参数对象的 Java 类型存在映射，则以映射的
     * [java.sql.Types] 常量作为 `setObject` 的目标类型。查找时精确匹配优先，
     * 其次按父类/接口递归匹配。
     *
     * @param javaType Java 类型
     * @param sqlType [java.sql.Types] 常量
     * @return 当前构建器
     */
    public fun typeMapping(javaType: Class<*>, sqlType: Int): JdbcConfigBuilder {
        typeMappings[javaType] = sqlType
        return this
    }

    /**
     * 构建不可变配置。
     */
    public fun build(): JdbcConfig = JdbcConfig(
        namingStrategy,
        nullPolicy,
        defaultNullSqlType,
        LinkedHashMap(columnMappings),
        LinkedHashMap(convertersByLabel),
        LinkedHashMap(convertersByIndex),
        LinkedHashMap(typeMappings),
    )
}

/** 默认 null 参数 SQL 类型。 */
internal const val DEFAULT_NULL_SQL_TYPE: Int = java.sql.Types.NULL
