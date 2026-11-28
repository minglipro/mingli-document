package com.mingliqiye.document.format.toml

/**
 * 一条 TOML 注释及其关联位置。
 *
 * [text] 是 `#` 之后的原始文本（保留原空格，例如 `# hello` 的文本是 `" hello"`），
 * 写出时按 `"#" + text` 原样还原，因此注释里的 `[`、`#`、`"` 等字符不会被重新解析。
 *
 * 注释的关联位置由 [tablePath]、[keyPath] 与 [tableIndex] 描述：
 * - 文件头/文件尾/游离注释没有关联路径（三个字段为空值）；
 * - 表头注释：[keyPath] 为空，[tablePath] 为表路径；
 * - 键值行注释：[tablePath] 为所属表（根表为空列表），[keyPath] 为键路径；
 * - 数组表元素内的注释：[tableIndex] 为元素下标（`-1` 表示不在数组表中）。
 *
 * @param text `#` 之后的原始注释文本
 * @param kind 注释布局类型
 * @param line 注释所在行号（从 1 开始；手工创建的注释可为 `0`）
 * @param tablePath 关联的表路径，根表为空列表
 * @param keyPath 关联的键路径（相对 [tablePath]），表头注释为空列表
 * @param tableIndex 关联的数组表元素下标，`-1` 表示不在数组表中
 */
public data class TomlComment(
    public val text: String,
    public val kind: TomlCommentKind,
    public val line: Int = 0,
    public val tablePath: List<String> = emptyList(),
    public val keyPath: List<String> = emptyList(),
    public val tableIndex: Int = -1,
) {

    /** 注释关联的完整路径（[tablePath] + [keyPath]）；文件级注释为空列表。 */
    public val path: List<String>
        get() = if (keyPath.isEmpty()) tablePath else tablePath + keyPath

    /** 关联键的最后一段；表头/文件级注释为 `null`。 */
    public val key: String?
        get() = keyPath.lastOrNull()

    /** 去掉了 `#` 之后首尾空白的注释内容。 */
    public val content: String
        get() = text.trim()
}
