package com.mingliqiye.document.format.toml

/**
 * 游离注释（[TomlCommentKind.ORPHAN]）以及“关联键/表已不存在”的注释的写出策略。
 *
 * @see TomlCommentConfig.orphanCommentPolicy
 * @see TomlCommentPreservingWriter
 */
public enum class TomlOrphanCommentPolicy {

    /**
     * 保留，并放到关联表的末尾（关联表也存在时）。
     *
     * 根表级注释放到根表键值块末尾；关联表已被删除时放到文件末尾。这是默认策略。
     */
    PRESERVE_AT_END_OF_TABLE,

    /** 保留，统一放到文件末尾（文件尾注释之前）。 */
    PRESERVE_AT_END_OF_FILE,

    /** 丢弃游离注释以及与已删除键/表关联的注释。 */
    DROP,
}
