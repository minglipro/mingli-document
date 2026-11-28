package com.mingliqiye.document.format.csv

/**
 * CSV 读取特性，对齐 上游实现 `CsvParser.Feature`（2.x）与 `CsvReadFeature`（3.x）。
 *
 * 名称与默认值以 上游实现 官方枚举为准；本库额外覆盖 上游实现 2.15 之后版本新增的条目，
 * 保证不弱于 上游实现。KDoc 中标注条目的引入版本。
 *
 * 默认值见 [enabledByDefault] 与 [CsvConfig.DEFAULT_READ_FEATURES]。
 */
public enum class CsvReadFeature(
    private val defaultState: Boolean,
) {

    /**
     * 是否自动裁剪分隔符两侧的无引号空白；双引号内的空白不受影响。
     *
     * 默认关闭（RFC-4180；上游实现 2.15 `CsvParser.Feature.TRIM_SPACES`）。
     */
    TRIM_SPACES(false),

    /**
     * 是否把文档暴露为数组（上游实现 2.15 `WRAP_AS_ARRAY`）。
     *
     * 本库的 Node 模型固定把 CSV 文档表示为根数组（一行一个对象），等价于 上游实现
     * 开启该特性；该条目用于配置对照，读取结果恒为数组，关闭不改变模型。
     */
    WRAP_AS_ARRAY(false),

    /**
     * 是否忽略 Schema 之外的“多余列”（上游实现 2.15 `IGNORE_TRAILING_UNMAPPABLE`）。
     *
     * 开启后强制忽略未映射列；关闭时由 [CsvSchema.failOnUnknownColumns] 决定（默认忽略）。
     */
    IGNORE_TRAILING_UNMAPPABLE(false),

    /**
     * 是否跳过完全空白（仅空白字符）的行（上游实现 2.15 `SKIP_EMPTY_LINES`）。
     *
     * 默认关闭：空行会被解析为只有一个空单元格的记录。
     */
    SKIP_EMPTY_LINES(false),

    /**
     * 是否允许行尾多出一个空列（尾逗号）（上游实现 2.15 `ALLOW_TRAILING_COMMA`）。
     *
     * 默认开启；关闭后尾逗号产生的空列按普通空单元格处理。
     */
    ALLOW_TRAILING_COMMA(true),

    /**
     * 是否允许 `#` 开头的注释行（上游实现 2.15 `ALLOW_COMMENTS`）。
     *
     * 默认关闭；Schema 上的 [CsvSchema.allowComments] 也可按 Schema 开启。
     */
    ALLOW_COMMENTS(false),

    /**
     * 数据行单元格数少于 Schema 声明列数时是否抛出 [com.mingliqiye.document.format.csv.exception.CsvParseException]
     * （上游实现 2.15 `FAIL_ON_MISSING_COLUMNS`）。
     *
     * 默认关闭；开启后优先于 [INSERT_NULLS_FOR_MISSING_COLUMNS]。
     */
    FAIL_ON_MISSING_COLUMNS(false),

    /**
     * 表头列数少于 Schema 声明列数时是否抛出 [com.mingliqiye.document.format.csv.exception.CsvParseException]
     * （上游实现 2.15 `FAIL_ON_MISSING_HEADER_COLUMNS`，默认开启）。
     *
     * 本库按“必填列是否缺失”执行该校验；关闭后 Schema 缺列时不再报错。
     */
    FAIL_ON_MISSING_HEADER_COLUMNS(true),

    /**
     * 数据行单元格数少于 Schema 声明列数时，为缺失列插入 null 值
     * （上游实现 2.15 `INSERT_NULLS_FOR_MISSING_COLUMNS`）。
     *
     * 默认关闭；[FAIL_ON_MISSING_COLUMNS] 开启时不生效。
     */
    INSERT_NULLS_FOR_MISSING_COLUMNS(false),

    /**
     * 是否把空字符串单元格读取为 null（上游实现 2.15 `EMPTY_STRING_AS_NULL`）。
     *
     * 默认关闭。
     */
    EMPTY_STRING_AS_NULL(false),

    /**
     * 是否只把表头单元格两侧空白裁剪掉（上游实现 2.16+ `TRIM_HEADER_SPACES`）。
     *
     * 默认开启。
     */
    TRIM_HEADER_SPACES(true),

    /**
     * 是否把无引号空单元格读取为 null（上游实现 2.16+ `EMPTY_UNQUOTED_STRING_AS_NULL`）。
     *
     * 说明：Commons CSV 不暴露单元格是否带引号，本库无法区分 `""` 与空单元格，
     * 因此该特性与 [EMPTY_STRING_AS_NULL] 行为相同，见文档“未支持/差异项”。
     */
    EMPTY_UNQUOTED_STRING_AS_NULL(false),

    /**
     * 是否只把无引号且内容等于 Schema `nullValue` 的单元格读取为 null
     * （上游实现 2.18+/3.1 `ONLY_UNQUOTED_NULL_VALUES_AS_NULL`）。
     *
     * 说明：同样无法区分引号，开启后按“内容等于 nullValue 即 null”处理。
     */
    ONLY_UNQUOTED_NULL_VALUES_AS_NULL(false),

    /**
     * 是否把无引号空单元格按“缺失列”处理（不产生字段）
     * （上游实现 3.2+ `EMPTY_UNQUOTED_STRING_AS_MISSING`）。
     *
     * 默认关闭；同样不区分引号。
     */
    EMPTY_UNQUOTED_STRING_AS_MISSING(false),

    /**
     * 是否跳过仅由列分隔符组成的空行（如 `,,`）
     * （上游实现 3.2+ `SKIP_EMPTY_ROWS`）。
     */
    SKIP_EMPTY_ROWS(false),

    /**
     * 表头出现重复列名时是否抛出 [com.mingliqiye.document.format.csv.exception.CsvParseException]
     * （上游实现 3.2+ `FAIL_ON_DUPLICATE_HEADER_COLUMNS`，默认开启）。
     *
     * 关闭后重复列名可共存，按名称访问时以最后一列为准。
     */
    FAIL_ON_DUPLICATE_HEADER_COLUMNS(true),

    /**
     * 表头列名与 Schema 列名匹配时是否忽略大小写
     * （上游实现 3.2+ `CASE_INSENSITIVE_HEADERS`）。
     */
    CASE_INSENSITIVE_HEADERS(false),
    ;

    /** 该特性在 上游实现 中的默认状态。 */
    public val enabledByDefault: Boolean
        get() = defaultState
}
