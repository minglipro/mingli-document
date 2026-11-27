package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * CSV Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 CSV 分隔符、表头选项与可选的 [CsvSchema]。
 *
 * 配置了 [schema] 时：
 * - 列顺序、列名与单元格类型由 Schema 决定，[delimiter] 仍由本配置控制；
 * - 表头由 [CsvSchema.header] 决定，[header] 被忽略（Schema 优先）。
 */
public class CsvConfig internal constructor(
    base: DocumentMapperConfig,
    /** 字段分隔符。 */
    public val delimiter: Char,
    /** 是否使用表头；配置了 [schema] 时由 [CsvSchema.header] 决定。 */
    public val header: Boolean,
    /** CSV Schema；非 null 时按 Schema 顺序与类型读写。 */
    public val schema: CsvSchema? = null,
) : DocumentMapperConfig(base) {

    public companion object {

        /**
         * 创建 CSV 配置构建器。
         */
        @JvmStatic
        public fun builder(): CsvConfigBuilder = CsvConfigBuilder()
    }
}

/** 默认字段分隔符（逗号）。 */
internal const val DEFAULT_CSV_DELIMITER: Char = ','

/** 默认是否使用表头。 */
internal const val DEFAULT_CSV_HEADER: Boolean = true
