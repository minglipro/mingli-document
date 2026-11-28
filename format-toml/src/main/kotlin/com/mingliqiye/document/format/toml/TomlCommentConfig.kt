package com.mingliqiye.document.format.toml

/**
 * [TomlCommentPreservingWriter] 的写出配置。
 */
public class TomlCommentConfig @JvmOverloads constructor(
    /** 游离注释与“关联键/表已不存在”的注释的保留策略。 */
    public val orphanCommentPolicy: TomlOrphanCommentPolicy = TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_TABLE,
    /** 是否按 [TomlCommentedDocument.blankLines] 输出空行。 */
    public val preserveBlankLines: Boolean = true,
    /** 非空输出是否以换行符结尾。 */
    public val trailingNewline: Boolean = true,
) {

    public companion object {

        /** 创建写出配置构建器。 */
        @JvmStatic
        public fun builder(): TomlCommentConfigBuilder = TomlCommentConfigBuilder()
    }
}
