package com.mingliqiye.document.format.json

/**
 * JSON 写出特性，对齐 上游实现 2.15.3 的
 * `com.fasterxml.上游库.core.json.JsonWriteFeature`、
 * `JsonGenerator.Feature` 与 `StreamWriteFeature` 中与 JSON 文本输出相关的项。
 *
 * 默认值与 上游实现 2.15.3 一致：[QUOTE_FIELD_NAMES]、[WRITE_NAN_AS_STRINGS]、
 * [WRITE_HEX_UPPER_CASE] 默认开启，其余默认关闭（见
 * [JsonConfig.DEFAULT_WRITE_FEATURES]）。通过 [JsonConfigBuilder.enable] /
 * [JsonConfigBuilder.disable] / [JsonConfigBuilder.configure] 或
 * [JsonConfig.isEnabled] 配置与查询。
 *
 * 每个枚举常量通过 [legacyName] 暴露对应的 上游实现 旧版 `JsonGenerator.Feature` /
 * `StreamWriteFeature` 名称，便于从 上游实现 配置迁移。
 *
 * @property legacyName 对应的 上游实现 旧版特性名称
 */
public enum class JsonWriteFeature(
    /** 对应的 上游实现 旧版 `JsonGenerator.Feature` / `StreamWriteFeature` 名称。 */
    public val legacyName: String,
) {

    /**
     * 对象字段名使用双引号包裹，默认开启。
     *
     * 关闭后按 上游实现 行为原样输出字段名（不校验合法性，可能产生非标准 JSON）。
     * 对应 上游实现 `JsonGenerator.Feature.QUOTE_FIELD_NAMES`。
     */
    QUOTE_FIELD_NAMES("QUOTE_FIELD_NAMES"),

    /**
     * `NaN` / `Infinity` / `-Infinity` 等非有限数字输出为 JSON 字符串，默认开启。
     *
     * 对应 上游实现 `JsonGenerator.Feature.QUOTE_NON_NUMERIC_NUMBERS`。关闭时输出
     * 未加引号的 `NaN` / `Infinity` / `-Infinity`（上游实现 行为，非标准 JSON）。
     * 本实现的流式写出路径（`format` / `formatBytes` / 流式写出）支持 Bean 的非有限
     * `Double` / `Float`；[com.mingliqiye.document.serialization.DocumentMapper.toNode]
     * 的 Node 树路径仍受 `NumberNode` 只能表示有限 `BigDecimal` 的限制。
     */
    WRITE_NAN_AS_STRINGS("QUOTE_NON_NUMERIC_NUMBERS"),

    /**
     * 所有数字输出为 JSON 字符串，默认关闭。
     *
     * 对应 上游实现 `JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS`，例如
     * `{"n":1}` 输出为 `{"n":"1"}`。
     */
    WRITE_NUMBERS_AS_STRINGS("WRITE_NUMBERS_AS_STRINGS"),

    /**
     * `BigDecimal` 使用普通十进制表示而不是科学计数法，默认关闭。
     *
     * 对应 上游实现 `JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN` /
     * `StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN`。本实现作用于 Node 树中带
     * 指数写法（如 `1E+5`）的 `NumberNode.raw`，开启后输出 `100000`；Bean 的
     * `BigDecimal` 在 serialization 层已经固定为 `toPlainString()`，因此对 Bean
     * 路径恒为普通表示。
     */
    WRITE_BIGDECIMAL_AS_PLAIN("WRITE_BIGDECIMAL_AS_PLAIN"),

    /**
     * 把 7 位 ASCII 之外的字符转义为 `\uXXXX`，默认关闭（直接输出 UTF-8 字符）。
     *
     * 对应 上游实现 `JsonGenerator.Feature.ESCAPE_NON_ASCII`，代理对（4 字节 UTF-8
     * 字符）会写成两个 `\uXXXX` 转义。
     */
    ESCAPE_NON_ASCII("ESCAPE_NON_ASCII"),

    /**
     * `\uXXXX` 转义中的十六进制字母使用大写，默认开启（与 上游实现 一致）。
     *
     * 对应 上游实现 `JsonGenerator.Feature.WRITE_HEX_UPPER_CASE`；关闭后使用小写。
     */
    WRITE_HEX_UPPER_CASE("WRITE_HEX_UPPER_CASE"),
}
