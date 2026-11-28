package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ObjectNode

/**
 * 保留注释的 TOML 文档。
 *
 * 由 [TomlCommentPreservingReader] 读取产生，包含三部分：
 * - [root]：与 [TomlReader] 值一致的文档树（对象字段按源文本语句顺序排列）；
 * - [comments]：按源文本顺序排列的注释（含关联的表/键、行号与布局类型）；
 * - [blankLines]：空行的锚点信息（位于哪条语句或文件末尾之前）。
 *
 * 通过 [withRoot] 可以替换文档树（例如重排字段、删除字段），注释会按路径重新关联；
 * 关联不上的注释按 [TomlCommentConfig.orphanCommentPolicy] 处理。
 *
 * 写出使用 [toTomlString]（等价于 [TomlCommentPreservingWriter]），所有方法都是只读操作，
 * 不修改本对象。
 */
public class TomlCommentedDocument internal constructor(
    /** 文档树，与 [TomlReader] 的值一致；对象字段顺序按源文本语句顺序。 */
    public val root: ObjectNode,
    comments: List<TomlComment>,
    blankLines: List<TomlBlankLine>,
    internal val structure: TomlDocumentStructure,
) {

    /** 按源文本顺序排列的注释。 */
    public val comments: List<TomlComment> = comments.toList()

    /** 空行锚点，按源文本顺序排列。 */
    public val blankLines: List<TomlBlankLine> = blankLines.toList()

    /**
     * 使用新的文档树创建文档，注释、空行与表结构信息保持不变。
     *
     * 主要用于“读取 -> 修改节点树 -> 写出”的流程；被删除的键/表所关联的注释
     * 在写出时按孤立注释策略处理。
     *
     * @param root 新文档树根节点
     * @return 新文档
     */
    public fun withRoot(root: ObjectNode): TomlCommentedDocument = TomlCommentedDocument(root, comments, blankLines, structure)

    /**
     * 使用默认配置写出 TOML 文本。
     *
     * @return TOML 文本
     */
    public fun toTomlString(): String = TomlCommentPreservingWriter().write(this)

    /**
     * 使用默认注释配置与指定 TOML 写出特性写出 TOML 文本。
     *
     * @param writeFeatures TOML 写出特性集合
     * @return TOML 文本
     */
    public fun toTomlString(writeFeatures: Set<TomlWriteFeature>): String = toTomlString(TomlCommentConfig(), writeFeatures)

    /**
     * 使用指定配置写出 TOML 文本。
     *
     * @param config 写出配置
     * @return TOML 文本
     */
    public fun toTomlString(config: TomlCommentConfig): String = TomlCommentPreservingWriter(config).write(this)

    /**
     * 使用指定注释配置与 TOML 写出特性写出 TOML 文本。
     *
     * @param config 写出配置
     * @param writeFeatures TOML 写出特性集合
     * @return TOML 文本
     */
    public fun toTomlString(config: TomlCommentConfig, writeFeatures: Set<TomlWriteFeature>): String = TomlCommentPreservingWriter(config, writeFeatures).write(this)

    public companion object {

        /**
         * 解析保留注释的 TOML 文本。
         *
         * @param source TOML 文本
         * @param maxDepth 解析最大嵌套深度，默认 [TomlConfig.DEFAULT_MAX_DEPTH]
         * @param readFeatures TOML 读取特性集合，默认 [TomlConfig.DEFAULT_READ_FEATURES]
         * @return 保留注释的文档
         * @throws com.mingliqiye.document.format.toml.exception.TomlParseException TOML 文本非法或
         *   嵌套深度超过 [maxDepth] 时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun parse(
            source: String,
            maxDepth: Int = TomlConfig.DEFAULT_MAX_DEPTH,
            readFeatures: Set<TomlReadFeature> = TomlConfig.DEFAULT_READ_FEATURES,
        ): TomlCommentedDocument = TomlCommentPreservingReader(source, maxDepth, readFeatures).read()

        /**
         * 从文档树创建不带注释与空行信息的文档。
         *
         * 所有对象字段按内联 table 写出（没有源表头结构信息）。
         *
         * @param root 文档树根节点
         * @return 保留注释的文档（无注释）
         */
        @JvmStatic
        public fun of(root: ObjectNode): TomlCommentedDocument = TomlCommentedDocument(root, emptyList(), emptyList(), TomlDocumentStructure.EMPTY)
    }
}

/**
 * 解析保留注释的 TOML 文本。
 *
 * @param maxDepth 解析最大嵌套深度，默认 [TomlConfig.DEFAULT_MAX_DEPTH]
 * @param readFeatures TOML 读取特性集合，默认 [TomlConfig.DEFAULT_READ_FEATURES]
 * @return 保留注释的文档
 * @throws com.mingliqiye.document.format.toml.exception.TomlParseException TOML 文本非法或
 *   嵌套深度超过 [maxDepth] 时抛出
 */
public fun String.toTomlCommentedDocument(
    maxDepth: Int = TomlConfig.DEFAULT_MAX_DEPTH,
    readFeatures: Set<TomlReadFeature> = TomlConfig.DEFAULT_READ_FEATURES,
): TomlCommentedDocument = TomlCommentPreservingReader(this, maxDepth, readFeatures).read()
