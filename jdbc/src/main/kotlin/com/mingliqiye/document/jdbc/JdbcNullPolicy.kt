package com.mingliqiye.document.jdbc

/**
 * SQL NULL 处理策略。
 *
 * 读取时作用于 `ResultSet` 中值为 SQL NULL 的列；生成 `INSERT`/`UPDATE` 语句与绑定
 * 参数时作用于对象中值为 [com.mingliqiye.document.core.NullNode] 的字段。
 */
public enum class JdbcNullPolicy {

    /**
     * 保留 null：读取时 SQL NULL 列生成 NullNode；写出时以 SQL NULL 绑定。
     */
    INCLUDE,

    /**
     * 忽略 null：读取时 SQL NULL 列不写入对象节点；生成 `INSERT`/`UPDATE` 时跳过值为
     * null 的普通字段。
     *
     * 显式参数列表（`WHERE` 条件列、[JdbcDocumentWriter.bind] 传入的参数）仍必须绑定，
     * 遇到 null 时抛出
     * [com.mingliqiye.document.jdbc.exception.JdbcWriteException]，此时应改用
     * [INCLUDE]。
     */
    OMIT,
}
