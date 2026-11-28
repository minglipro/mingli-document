package com.mingliqiye.document.format.toml

/**
 * TOML 注释的布局类型。
 *
 * 类型由 [TomlCommentPreservingReader] 根据注释在源文本中的位置推断，
 * [TomlCommentPreservingWriter] 依据类型把注释重新放回对应位置。
 */
public enum class TomlCommentKind {

    /** 文件头注释：文件中第一组注释（位于第一条语句之前）。 */
    FILE_HEADER,

    /** 行前注释：直接位于某个表头或键值行上方的注释。 */
    LEADING,

    /** 行尾注释：与表头或键值行在同一行的注释。 */
    INLINE,

    /** 多行值内部的注释：写出时提升为所属键值行上方的独立注释行。 */
    INTERIOR,

    /** 文件尾注释：位于最后一条语句之后的注释。 */
    FILE_TRAILER,

    /**
     * 游离注释：没有直接关联到语句的注释，例如被空行与后续语句隔开，
     * 或所关联的键/表在写出时已不存在。具体保留方式见 [TomlOrphanCommentPolicy]。
     */
    ORPHAN,
}
