package com.mingliqiye.document.format.properties

/**
 * Properties 读取特性。
 *
 * 说明：上游实现 2.15 的 `Properties 底层库` **没有** `PropertiesReadFeature`
 * 枚举（`JavaPropsFactory` 注释明确写着 “No format-specific configuration, yet”），
 * 读取行为通过 `JavaPropsSchema` 的布尔选项控制。本枚举把其中与读取相关的布尔选项
 * 以及本模块的兼容扩展暴露为 上游实现 风格的 enable/disable/configure 开关；
 * 默认值与 上游实现 `JavaPropsSchema` 默认值一致（扩展特性除外，见各项说明）。
 *
 * 对应关系：
 * - [PARSE_SIMPLE_INDEXES] ↔ `JavaPropsSchema.parseSimpleIndexes()`（默认 `true`）
 * - [ALLOW_INDEX_MARKERS] ↔ `JavaPropsSchema.indexMarker()` 是否生效（默认生效）
 * - [DECODE_PERCENT_ESCAPED_SEGMENTS] 为本模块扩展，上游实现 无对应配置
 */
public enum class PropertiesReadFeature {

    /**
     * 把“纯数字”路径段解析为数组下标（默认开启，对齐 上游实现 `parseSimpleIndexes=true`）。
     *
     * 只识别 1~9 位非负十进制数字（与 上游实现 相同）；关闭后数字段作为对象字段名，
     * 例如 `server.1=...` 读成 `{"server":{"1":...}}`。
     */
    PARSE_SIMPLE_INDEXES,

    /**
     * 允许使用 [PropertiesConfig.indexMarker] 标记数组下标（默认开启）。
     *
     * 关闭等价于 上游实现 `JavaPropsSchema.withoutIndexMarker()`：`tags[0]` 不再表示
     * 数组下标，而是字面字段名。
     */
    ALLOW_INDEX_MARKERS,

    /**
     * 解码路径段中的百分号编码（默认开启；本模块扩展，上游实现 无此配置）。
     *
     * 开启时 `%2E` / `%5B` / `%5D` / `%25` 分别还原为 `.` / `[` / `]` / `%`，
     * 使字段名可以包含路径分隔符与下标标记；对应写出特性
     * [PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS]。
     * 关闭后百分号序列按字面文本处理，相当于纯 上游实现 语义。
     */
    DECODE_PERCENT_ESCAPED_SEGMENTS,
}
