package com.mingliqiye.document.format.toml

/**
 * TOML 读取特性，对齐 上游实现 `com.fasterxml.上游库.dataformat.toml.TomlReadFeature`。
 *
 * 上游实现 2.15 只有一个读取特性 [PARSE_JAVA_TIME]；上游实现 3.2 起新增
 * [VALIDATE_DATE_TIME]。两个特性默认都关闭，与 上游实现 默认值一致。
 */
public enum class TomlReadFeature {

    /**
     * 把 TOML 日期时间字面量解析为 `java.time` 类型，默认关闭。
     *
     * 上游实现 开启后以 `VALUE_EMBEDDED_OBJECT` Token 返回 `LocalDate` / `LocalTime` /
     * `LocalDateTime` / `OffsetDateTime`。本项目的 [com.mingliqiye.document.core.Node]
     * 与 `DocumentToken` 模型没有 embedded object / Temporal 类型，因此开启后读取结果
     * 使用 `java.time` 规范化后的 ISO-8601 字符串（与关闭时的“源码原文”字符串可区分），
     * 该差异已在 format-toml README 与 docs/formats/toml.md 中明确记录。
     */
    PARSE_JAVA_TIME,

    /**
     * 字符串模式下是否校验日期时间值（上游实现 3.2 起），默认关闭。
     *
     * 关闭时直接返回 TOML 源码中的日期时间原文；开启时额外用 `java.time` 复核一遍，
     * 非法值抛 [com.mingliqiye.document.format.toml.exception.TomlParseException]。
     * 当前底层 tomlj 解析器本身已严格校验日期时间，因此该开关主要用于显式对齐
     * 上游实现 3.x 配置并防御未来解析器放宽。
     */
    VALIDATE_DATE_TIME,
}
