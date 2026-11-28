package com.mingliqiye.document.format.yaml

/**
 * YAML 读取特性，对齐 上游实现 YAML（2.15）的 `YAMLParser.Feature`。
 *
 * 默认值同样对齐 上游实现 2.15：[YamlConfig.DEFAULT_READ_FEATURES] 包含
 * [EMPTY_STRING_AS_NULL]，不包含 [PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS]。
 */
public enum class YamlReadFeature {

    /**
     * 空的 plain 标量（如 `key:`、`- `）读取为 null（`NullNode` / `NULL` Token）。
     *
     * 上游实现 2.15 默认开启。关闭后空 plain 标量读取为空字符串 `""`
     * （显式引号空串 `""` 始终是空字符串）。
     *
     * 对应 上游实现 `YAMLParser.Feature.EMPTY_STRING_AS_NULL`（默认 `true`）。
     */
    EMPTY_STRING_AS_NULL,

    /**
     * `yes` / `no` / `on` / `off` 等 YAML 1.1 布尔词按字符串读取。
     *
     * 关闭（上游实现 默认）时按 `!!bool` 解析为 `BooleanNode` / `BOOLEAN`；
     * 开启后只有 `true` / `false` 解析为布尔，其余布尔词读取为字符串。
     *
     * 对应 上游实现 `YAMLParser.Feature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS`
     * （默认 `false`，上游实现 2.15 新增）。
     */
    PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS,
}
