package com.mingliqiye.document.format.toml

/**
 * 源文档中一条语句的路径（用于按源顺序重排节点树）。
 *
 * @param path 语句的完整绝对路径；表头语语句就是表路径，键值语句是表路径 + 键路径
 * @param tableIndex 语句所在的数组表元素下标，`-1` 表示不在数组表中
 */
internal data class TomlPathEvent(
    val path: List<String>,
    val tableIndex: Int,
)

/**
 * 源文档中出现的表结构信息。
 *
 * 节点树本身无法区分 `[table]` 表节与内联 table（两者都解析成 [com.mingliqiye.document.core.ObjectNode]），
 * 也无法区分 `[[table]]` 数组表与普通内联 table 数组。读取时记录这些路径，写出时据此还原结构；
 * 该信息仅用于注释保留 API，不影响 [TomlReader] / [TomlWriter] 的行为。
 *
 * @param tablePaths 以表头（`[a.b]`）形式出现的路径
 * @param arrayTablePaths 以数组表头（`[[a.b]]`）形式出现的路径
 */
internal data class TomlDocumentStructure(
    val tablePaths: Set<List<String>> = emptySet(),
    val arrayTablePaths: Set<List<String>> = emptySet(),
) {

    /** 是否存在以 [path] 为前缀的表/数组表路径。 */
    fun hasDescendantSection(path: List<String>): Boolean = tablePaths.any { it.isDescendantOf(path) } || arrayTablePaths.any { it.isDescendantOf(path) }

    private fun List<String>.isDescendantOf(prefix: List<String>): Boolean = size > prefix.size && subList(0, prefix.size) == prefix

    companion object {
        val EMPTY: TomlDocumentStructure = TomlDocumentStructure()
    }
}
