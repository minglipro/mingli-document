package com.mingliqiye.document.format.yaml

/**
 * YAML 写出特性，对齐 上游实现 YAML（2.15）的 `YAMLGenerator.Feature`。
 *
 * 默认值同样对齐 上游实现 2.15：[YamlConfig.DEFAULT_WRITE_FEATURES] 包含
 * [WRITE_DOC_START_MARKER]、[SPLIT_LINES]、[USE_NATIVE_OBJECT_ID] 与
 * [USE_NATIVE_TYPE_ID]。
 */
public enum class YamlWriteFeature {

    /**
     * 写出显式文档起始标记 `---`。
     *
     * 对应 上游实现 `WRITE_DOC_START_MARKER`（默认 `true`）。
     * 关闭后 `format` / `formatAll` 不输出起始 `---`。
     */
    WRITE_DOC_START_MARKER,

    /**
     * 使用 YAML 原生对象 id（anchor/alias）表示对象引用。
     *
     * 对应 上游实现 `USE_NATIVE_OBJECT_ID`（默认 `true`）。
     * 本库的对象引用来自通用配置 `writeReferences`，Node 模型不携带 上游实现 风格的
     * type id/object id，因此该开关当前不改变写出结果（见 README「未支持项」）。
     */
    USE_NATIVE_OBJECT_ID,

    /**
     * 使用 YAML 原生 type id（`!!com.example.Type` tag）表示类型。
     *
     * 对应 上游实现 `USE_NATIVE_TYPE_ID`（默认 `true`）。
     * 本库多态类型使用通用 type 属性，Node 模型不携带 type id，因此该开关当前不改变
     * 写出结果（见 README「未支持项」）。
     */
    USE_NATIVE_TYPE_ID,

    /**
     * 使用 SnakeYAML canonical 输出（显式 tag + flow 布局）。
     *
     * 对应 上游实现 `CANONICAL_OUTPUT`（默认 `false`）。
     * 开启后忽略 [YamlConfig.pretty] 与 [YamlConfig.defaultFlowStyle] 的布局选择。
     */
    CANONICAL_OUTPUT,

    /**
     * 对过长文本自动折行（交给 SnakeYAML `splitLines`）。
     *
     * 对应 上游实现 `SPLIT_LINES`（默认 `true`）。
     */
    SPLIT_LINES,

    /**
     * 最小化字符串引号：能安全使用 plain 风格的字符串不加引号。
     *
     * 对应 上游实现 `MINIMIZE_QUOTES`（默认 `false`）。关闭时字符串值一律用双引号
     * 输出（上游实现 默认行为）；开启后按 上游实现 的 quoting checker 规则选择
     * plain / 双引号 / 字面量块（多行）。
     */
    MINIMIZE_QUOTES,

    /**
     * 与 [MINIMIZE_QUOTES] 配合：看起来像数字的字符串仍然加引号。
     *
     * 对应 上游实现 `ALWAYS_QUOTE_NUMBERS_AS_STRINGS`（默认 `false`）。
     * 关闭时 `"123"` 可能输出为 plain `123`，读取时会被解析为数字。
     */
    ALWAYS_QUOTE_NUMBERS_AS_STRINGS,

    /**
     * 含换行的字符串使用字面量块（literal block）风格。
     *
     * 对应 上游实现 `LITERAL_BLOCK_STYLE`（默认 `false`）。开启 [MINIMIZE_QUOTES]
     * 时多行字符串自动使用字面量块，无需再单独开启本项。
     */
    LITERAL_BLOCK_STYLE,

    /**
     * 数组元素额外缩进（SnakeYAML `indicatorIndent=1`）。
     *
     * 对应 上游实现 `INDENT_ARRAYS`（默认 `false`）。
     * [INDENT_ARRAYS_WITH_INDICATOR] 开启时优先生效。
     */
    INDENT_ARRAYS,

    /**
     * 数组元素缩进且 `-` 指示符缩进（SnakeYAML `indentWithIndicator`）。
     *
     * 对应 上游实现 `INDENT_ARRAYS_WITH_INDICATOR`（默认 `false`）。
     */
    INDENT_ARRAYS_WITH_INDICATOR,

    /**
     * 换行符使用当前平台换行符（Windows 为 `\r\n`）。
     *
     * 对应 上游实现 `USE_PLATFORM_LINE_BREAKS`（默认 `false`，即统一 `\n`）。
     */
    USE_PLATFORM_LINE_BREAKS,

    /**
     * 允许长度超过 128 的简单 mapping key（上限提升到 1024）。
     *
     * 对应 上游实现 `ALLOW_LONG_KEYS`（默认 `false`）。
     */
    ALLOW_LONG_KEYS,
}
