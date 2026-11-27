package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * CSV Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 CSV 分隔符与表头选项。
 */
public class CsvConfig internal constructor(
    base: DocumentMapperConfig,
    /** 字段分隔符。 */
    public val delimiter: Char,
    /** 是否使用表头。 */
    public val header: Boolean,
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
