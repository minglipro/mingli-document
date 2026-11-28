package com.mingliqiye.document.format.toml

/**
 * [TomlCommentConfig] 构建器。
 */
public class TomlCommentConfigBuilder {

    private var orphanCommentPolicy: TomlOrphanCommentPolicy =
        TomlOrphanCommentPolicy.PRESERVE_AT_END_OF_TABLE
    private var preserveBlankLines: Boolean = true
    private var trailingNewline: Boolean = true

    /**
     * 设置游离注释与“关联键/表已不存在”的注释的保留策略。
     *
     * @param policy 保留策略
     * @return 当前构建器
     */
    public fun orphanCommentPolicy(policy: TomlOrphanCommentPolicy): TomlCommentConfigBuilder {
        this.orphanCommentPolicy = policy
        return this
    }

    /**
     * 设置是否输出空行。
     *
     * @param preserve 是否保留空行
     * @return 当前构建器
     */
    public fun preserveBlankLines(preserve: Boolean): TomlCommentConfigBuilder {
        this.preserveBlankLines = preserve
        return this
    }

    /**
     * 设置非空输出是否以换行符结尾。
     *
     * @param write 是否输出结尾换行
     * @return 当前构建器
     */
    public fun trailingNewline(write: Boolean): TomlCommentConfigBuilder {
        this.trailingNewline = write
        return this
    }

    /** 构建配置。 */
    public fun build(): TomlCommentConfig = TomlCommentConfig(orphanCommentPolicy, preserveBlankLines, trailingNewline)
}
