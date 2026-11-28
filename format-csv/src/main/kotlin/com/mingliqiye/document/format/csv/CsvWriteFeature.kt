package com.mingliqiye.document.format.csv

/**
 * CSV 写出特性，对齐 上游实现 `CsvGenerator.Feature`（2.x）与 `CsvWriteFeature`（3.x）。
 *
 * 名称与默认值以 上游实现 官方枚举为准；本库额外覆盖 上游实现 2.15 之后版本新增的条目，
 * 保证不弱于 上游实现。KDoc 中标注条目的引入版本。
 *
 * 默认值见 [enabledByDefault] 与 [CsvConfig.DEFAULT_WRITE_FEATURES]。
 */
public enum class CsvWriteFeature(
    private val defaultState: Boolean,
) {

    /**
     * 是否对每个值做完整的“严格最小引号”判断（上游实现 2.15 `STRICT_CHECK_FOR_QUOTING`）。
     *
     * 默认关闭：使用更保守、更快的宽松检查，可能对部分无需引号的值也加引号。
     * 开启后只在确实需要时加引号。
     */
    STRICT_CHECK_FOR_QUOTING(false),

    /**
     * 是否省略行尾缺失列（上游实现 2.15 `OMIT_MISSING_TAIL_COLUMNS`）。
     *
     * 默认关闭：缺失列仍写出为 null 值文本（默认空串）。
     */
    OMIT_MISSING_TAIL_COLUMNS(false),

    /**
     * 是否强制给字符串值加引号（上游实现 2.15 `ALWAYS_QUOTE_STRINGS`）。
     *
     * 优先级高于 [STRICT_CHECK_FOR_QUOTING]；不影响数字/布尔值。
     */
    ALWAYS_QUOTE_STRINGS(false),

    /**
     * 是否强制给空字符串加引号（上游实现 2.15 `ALWAYS_QUOTE_EMPTY_STRINGS`）。
     */
    ALWAYS_QUOTE_EMPTY_STRINGS(false),

    /**
     * 是否用转义字符（[CsvSchema.escapeChar]）转义引号，而不是把引号写成两个
     * （上游实现 2.15 `ESCAPE_QUOTE_CHAR_WITH_ESCAPE_CHAR`）。
     */
    ESCAPE_QUOTE_CHAR_WITH_ESCAPE_CHAR(false),

    /**
     * 是否用转义字符转义控制字符（如 `\n`、`\r`、制表符等）
     * （上游实现 2.15 `ESCAPE_CONTROL_CHARS_WITH_ESCAPE_CHAR`）。
     */
    ESCAPE_CONTROL_CHARS_WITH_ESCAPE_CHAR(false),

    /**
     * 是否强制给数字值加引号（上游实现 2.16+ `ALWAYS_QUOTE_NUMBERS`）。
     */
    ALWAYS_QUOTE_NUMBERS(false),

    /**
     * 最后一行之后是否还写出换行符（上游实现 2.17+ `WRITE_LINEFEED_AFTER_LAST_ROW`，默认开启）。
     *
     * 关闭后输出不以 [CsvSchema.lineSeparator] 结尾。
     */
    WRITE_LINEFEED_AFTER_LAST_ROW(true),

    /**
     * 是否给首/尾为空白字符（码点小于等于 `0x20`）的字符串值加引号
     * （上游实现 3.2+ `QUOTE_STRINGS_WITH_LEADING_TRAILING_WHITESPACE`）。
     */
    QUOTE_STRINGS_WITH_LEADING_TRAILING_WHITESPACE(false),
    ;

    /** 该特性在 上游实现 中的默认状态。 */
    public val enabledByDefault: Boolean
        get() = defaultState
}
