package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * CSV Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 CSV 分隔符、表头选项、可选的 [CsvSchema] 与嵌套值读写方式。
 *
 * 配置了 [schema] 时：
 * - 列顺序、列名与单元格类型由 Schema 决定，[delimiter] 仍由本配置控制；
 * - 表头由 [CsvSchema.header] 决定，[header] 被忽略（Schema 优先）。
 *
 * 配置了 [nestedMode] 时启用嵌套单元格：
 * - [CsvNestedMode.JSON]：`{...}` / `[...]` 单元格解析为嵌套节点，嵌套节点写出为 JSON 文本；
 * - [CsvNestedMode.COLUMNS]：点分表头（`address.city`）读写为嵌套对象，写出时展开为点分列名。
 *
 * [nestedMode] 为 `null`（默认）时不支持嵌套，保持旧版本行为；声明了嵌套子 Schema 的字段
 * 不受该开关限制（由 Schema 自身启用）。
 *
 * 解析深度：[maxDepth] 限制解析结果的节点嵌套深度（根数组深度为 `1`，行对象为 `2`，
 * 单元格值为 `3`），默认 [DEFAULT_MAX_DEPTH]（512）。JSON 文本单元格内部的嵌套同样受限：
 * 单元格根的可用相对深度为 `maxDepth - 2`。超限时解析抛出
 * [com.mingliqiye.document.format.csv.exception.CsvParseException]。
 */
public class CsvConfig internal constructor(
    base: DocumentMapperConfig,
    /** 字段分隔符。 */
    public val delimiter: Char,
    /** 是否使用表头；配置了 [schema] 时由 [CsvSchema.header] 决定。 */
    public val header: Boolean,
    /** CSV Schema；非 null 时按 Schema 顺序与类型读写。 */
    public val schema: CsvSchema? = null,
    /** 嵌套值读写方式；`null` 表示不支持嵌套（默认）。 */
    public val nestedMode: CsvNestedMode? = null,
    /**
     * 解析最大嵌套深度，默认 [DEFAULT_MAX_DEPTH]。
     *
     * 根数组深度为 `1`、行对象为 `2`、单元格标量为 `3`；点分列名/嵌套 Schema 每多一层
     * 加 `1`，JSON 文本单元格内部嵌套从 `3` 起算。超限抛
     * [com.mingliqiye.document.format.csv.exception.CsvParseException]。
     */
    public val maxDepth: Int = DEFAULT_MAX_DEPTH,
) : DocumentMapperConfig(base) {

    init {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
    }

    public companion object {

        /**
         * 默认解析最大嵌套深度。
         */
        public const val DEFAULT_MAX_DEPTH: Int = 512

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
