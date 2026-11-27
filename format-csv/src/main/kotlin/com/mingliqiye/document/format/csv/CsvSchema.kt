package com.mingliqiye.document.format.csv

import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField

/**
 * CSV Schema。
 *
 * 字段声明顺序即列顺序：写出时按该顺序输出列，读取无表头数据时按该顺序对应列。
 * 字段类型使用 [com.mingliqiye.document.schema.SchemaType]，读取时按类型解析单元格。
 *
 * 表头由 [header] 控制；当 Mapper 同时配置了 [CsvConfig] 与本 Schema 时，Schema 的
 * [header] 优先于 [CsvConfig.header]。分隔符始终由 [CsvConfig.delimiter] 控制。
 *
 * 读取规则：
 * - 有表头时按列名映射，无表头时按字段顺序对应；
 * - 单元格按字段类型解析，无法解析时抛出
 *   [com.mingliqiye.document.format.csv.exception.CsvParseException]；
 * - 空单元格在 [SchemaField.nullable] 时读作 null，`STRING` 读作空串，其余必填字段报错；
 * - [SchemaField.required] 的列缺失（表头中没有该列、无表头数据行过短）时报错；
 * - 未在 Schema 中声明的列由 [failOnUnknownColumns] 控制：默认忽略，为 true 时报错。
 *
 * 写出规则：
 * - 只输出 Schema 声明的列；行中未声明的列由 [failOnUnknownColumns] 控制；
 * - 行中缺少 [SchemaField.required] 的列时抛出
 *   [com.mingliqiye.document.format.csv.exception.CsvException]。
 *
 * @param name Schema 名称
 * @param fields 字段列表，顺序即列顺序
 * @param header 是否读写表头行
 * @param failOnUnknownColumns 遇到 Schema 未声明的列时是否抛出异常
 */
public class CsvSchema internal constructor(
    override val name: String,
    override val fields: List<SchemaField>,
    /** 是否读写表头行。 */
    public val header: Boolean,
    /** 遇到 Schema 未声明的列时是否抛出异常。 */
    public val failOnUnknownColumns: Boolean,
) : DocumentSchema {

    private val byName: Map<String, SchemaField> = fields.associateBy { it.name }

    /** 列名列表，顺序与 [fields] 一致。 */
    public val columnNames: List<String>
        get() = fields.map { it.name }

    override fun field(name: String): SchemaField? = byName[name]

    public companion object {

        /**
         * 创建 CSV Schema 构建器。
         *
         * @param name Schema 名称
         * @return 构建器
         */
        @JvmStatic
        public fun builder(name: String): CsvSchemaBuilder = CsvSchemaBuilder(name)

        /**
         * 使用字段定义创建 CSV Schema。
         *
         * @param name Schema 名称
         * @param fields 字段定义，顺序即列顺序
         * @return CSV Schema
         */
        @JvmStatic
        public fun of(name: String, vararg fields: SchemaField): CsvSchema {
            val builder = CsvSchemaBuilder(name)
            for (field in fields) {
                builder.addField(field)
            }
            return builder.build()
        }
    }
}

/** 默认是否读写表头行。 */
internal const val DEFAULT_CSV_SCHEMA_HEADER: Boolean = true

/** 默认是否在遇到未声明列时抛出异常。 */
internal const val DEFAULT_CSV_SCHEMA_FAIL_ON_UNKNOWN_COLUMNS: Boolean = false
