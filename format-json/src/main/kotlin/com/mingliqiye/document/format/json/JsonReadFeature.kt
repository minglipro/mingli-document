package com.mingliqiye.document.format.json

/**
 * JSON 读取特性，对齐 上游实现 2.15.3 的
 * `com.fasterxml.上游库.core.json.JsonReadFeature` 与
 * `com.fasterxml.上游库.core.StreamReadFeature` 中与 JSON 文本语法相关的项。
 *
 * 全部特性默认关闭（与 上游实现 一致），通过 [JsonConfigBuilder.enable] /
 * [JsonConfigBuilder.disable] / [JsonConfigBuilder.configure] 或
 * [JsonConfig.isEnabled] 配置与查询。历史选项 [JsonConfig.allowComments] 与
 * [JsonConfig.duplicateKeyPolicy] 与对应特性联动：
 *
 * - [ALLOW_JAVA_COMMENTS] 与 [JsonConfig.allowComments] 是同一个开关（本项目出于
 *   JSONC 历史行为默认开启，上游实现 默认关闭）；
 * - [STRICT_DUPLICATE_DETECTION] 等价于 [DuplicateKeyPolicy.FAIL]。
 *
 * 每个枚举常量通过 [legacyName] 暴露对应的 上游实现 旧版 `JsonParser.Feature` /
 * `StreamReadFeature` 名称，便于从 上游实现 配置迁移。
 *
 * @property legacyName 对应的 上游实现 旧版特性名称
 */
public enum class JsonReadFeature(
    /** 对应的 上游实现 旧版 `JsonParser.Feature` / `StreamReadFeature` 名称。 */
    public val legacyName: String,
) {

    /**
     * 允许 `//` 行注释与块注释（上游实现 的 `ALLOW_COMMENTS`），默认开启
     * （本项目 JSONC 兼容行为）。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_COMMENTS`；上游实现 默认关闭，
     * 本项目为兼容既有 JSONC 行为默认开启，可用
     * `disable(ALLOW_JAVA_COMMENTS)` 或 `allowComments(false)` 恢复严格 JSON。
     */
    ALLOW_JAVA_COMMENTS("ALLOW_COMMENTS"),

    /**
     * 允许 YAML 风格 `#` 行注释，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_YAML_COMMENTS`。
     */
    ALLOW_YAML_COMMENTS("ALLOW_YAML_COMMENTS"),

    /**
     * 允许字符串与字段名使用单引号，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_SINGLE_QUOTES`。
     */
    ALLOW_SINGLE_QUOTES("ALLOW_SINGLE_QUOTES"),

    /**
     * 允许对象字段名不加双引号，默认关闭。
     *
     * 未加引号的字段名由除空白、`:`、`,`、`}`、`]` 等结构字符之外的字符组成。
     * 对应 上游实现 `JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES`。
     */
    ALLOW_UNQUOTED_FIELD_NAMES("ALLOW_UNQUOTED_FIELD_NAMES"),

    /**
     * 允许字符串中包含未转义的控制字符（U+0000..U+001F），默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS`。
     */
    ALLOW_UNESCAPED_CONTROL_CHARS("ALLOW_UNQUOTED_CONTROL_CHARS"),

    /**
     * 允许任意字符使用反斜杠转义（未知转义按字面字符处理），默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER`。
     */
    ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER("ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER"),

    /**
     * 允许数字以 0 开头，如 `0123`，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS`。
     */
    ALLOW_LEADING_ZEROS_FOR_NUMBERS("ALLOW_NUMERIC_LEADING_ZEROS"),

    /**
     * 允许数字带前导正号，如 `+1`，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS`。
     */
    ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS("ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS"),

    /**
     * 允许小数省略整数部分，如 `.5`，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS`。
     */
    ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS("ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS"),

    /**
     * 允许小数省略小数部分，如 `1.`，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS`。
     */
    ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS("ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS"),

    /**
     * 允许 `NaN`、`Infinity`、`-Infinity`（以及 `+INF` / `-INF` / `+Infinity`）
     * 非数字字面量，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS`。Node 树中保留
     * 规范化后的 `raw`（`NaN` / `Infinity` / `-Infinity`），`NumberNode.value`
     * 无法用 `BigDecimal` 表示非有限值（访问会抛 `NumberFormatException`），
     * 需要数值时使用 `asNumber().toDouble()`；写出时由
     * [JsonWriteFeature.WRITE_NAN_AS_STRINGS] 决定是否加引号。
     */
    ALLOW_NON_NUMERIC_NUMBERS("ALLOW_NON_NUMERIC_NUMBERS"),

    /**
     * 允许数组中出现缺失值，如 `[1,,2]`、`[,1]`、`[1,]`，缺失槽读取为 `null`，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_MISSING_VALUES`。缺失槽的判定与
     * 上游实现 一致：`[1,,2]` 得到 `[1,null,2]`；`[1,]` 得到 `[1,null]`；
     * 同时开启 [ALLOW_TRAILING_COMMA] 时尾逗号优先，`[1,]` 得到 `[1]`。
     */
    ALLOW_MISSING_VALUES("ALLOW_MISSING_VALUES"),

    /**
     * 允许数组/对象末尾出现多余逗号，如 `[1,]`、`{"a":1,}`，默认关闭。
     *
     * 对应 上游实现 `JsonParser.Feature.ALLOW_TRAILING_COMMA`。
     */
    ALLOW_TRAILING_COMMA("ALLOW_TRAILING_COMMA"),

    /**
     * 严格检测对象重复 key：出现重复 key 时抛
     * [com.mingliqiye.document.format.json.exception.JsonParseException]，默认关闭。
     *
     * 对应 上游实现 `StreamReadFeature.STRICT_DUPLICATE_DETECTION`；与
     * [JsonConfig.duplicateKeyPolicy] 联动，等价于
     * [DuplicateKeyPolicy.FAIL]。
     */
    STRICT_DUPLICATE_DETECTION("STRICT_DUPLICATE_DETECTION"),
}
