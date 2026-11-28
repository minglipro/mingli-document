package com.mingliqiye.document.format.properties

/**
 * Properties 写出特性。
 *
 * 说明：上游实现 2.15 的 `Properties 底层库` **没有** `PropertiesWriteFeature`
 * 枚举，写出行为通过 `JavaPropsSchema` 选项控制。本枚举把其中与写出相关的布尔选项
 * 以及本模块的兼容扩展暴露为 上游实现 风格的 enable/disable/configure 开关；
 * 默认值与 上游实现 `JavaPropsSchema` 默认值一致（扩展特性除外，见各项说明）。
 *
 * 对应关系：
 * - [WRITE_INDEX_USING_MARKERS] ↔ `JavaPropsSchema.writeIndexUsingMarkers()`
 *   （默认 `false`，即用 `a.1` 而非 `a[1]`）
 * - [ESCAPE_PERCENT_ENCODED_SEGMENTS] 为本模块扩展，上游实现 无对应配置
 * - [ESCAPE_NON_ASCII] 用于对齐 上游实现 基于 Latin-1 的 `\uXXXX` 转义字面输出
 */
public enum class PropertiesWriteFeature {

    /**
     * 数组下标使用 [PropertiesConfig.indexMarker] 标记写出（默认关闭，对齐 上游实现
     * `writeIndexUsingMarkers=false`）。
     *
     * 关闭时使用“简单下标”写法：`firstArrayOffset` 起连续编号作为路径段，
     * 例如 `tags.1`、`tags.2`；开启后写成 `tags[1]`、`tags[2]`。
     * 未配置 [PropertiesConfig.indexMarker] 时该特性不生效。
     */
    WRITE_INDEX_USING_MARKERS,

    /**
     * 把对象字段名中的 `%`、`.`、`[`、`]` 写成百分号编码（默认开启；本模块扩展）。
     *
     * 开启时字段名 `user.name` 写成 `user%2Ename`，保证可往返；
     * 关闭后按 上游实现 语义直接拼接路径段（字段名含路径分隔符时会产生歧义）。
     * 对应读取特性 [PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS]。
     */
    ESCAPE_PERCENT_ENCODED_SEGMENTS,

    /**
     * 非 ASCII 字符（`>` `0x7F`）写出为 `\uXXXX`（默认关闭）。
     *
     * 关闭时按 Unicode 原样写出（输出编码由 `fileCharset` 决定，默认 UTF-8）；
     * 开启后字面输出与 上游实现 基于 Latin-1 的默认转义一致。
     */
    ESCAPE_NON_ASCII,
}
