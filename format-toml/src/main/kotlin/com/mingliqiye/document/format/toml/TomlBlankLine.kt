package com.mingliqiye.document.format.toml

/**
 * 一处空行及其锚点。
 *
 * 空行没有自己的内容，只记录“位于哪条语句之前”。写出时会先输出锚点语句前的空行，
 * 再输出锚点的行前注释，最后输出语句本身；`endOfFile = true` 表示空行位于文件末尾
 * （最后一条语句之后），写出时放到文件尾注释之前。
 *
 * @param line 空行所在行号（从 1 开始；手工创建时可为 `0`）
 * @param tablePath 锚点语句所属表路径，根表为空列表
 * @param keyPath 锚点语句的键路径；锚点为表头时为空列表
 * @param tableIndex 锚点语句所属数组表元素下标，`-1` 表示不在数组表中
 * @param endOfFile 是否位于文件末尾
 */
public data class TomlBlankLine(
    public val line: Int = 0,
    public val tablePath: List<String> = emptyList(),
    public val keyPath: List<String> = emptyList(),
    public val tableIndex: Int = -1,
    public val endOfFile: Boolean = false,
) {

    /** 空行锚点语句的完整路径（[tablePath] + [keyPath]）。 */
    public val path: List<String>
        get() = if (keyPath.isEmpty()) tablePath else tablePath + keyPath
}
