package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * YAML Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 YAML 专属选项：
 * - 上游实现 YAML 对齐的读写特性：[readFeatures] / [writeFeatures]（默认值对齐
 *   上游实现 YAML 2.15 的 `YAMLParser.Feature` / `YAMLGenerator.Feature`）；
 * - SnakeYAML LoaderOptions/DumperOptions 透传：[maxAliases]、[allowCustomTags]、
 *   [allowDuplicateKeys]、[codePointLimit]、[indent]、[lineWidth]、[allowUnicode]、
 *   [explicitEnd]、[defaultFlowStyle]；
 * - 本库历史选项：[pretty]（默认块格式布局开关）。
 */
public class YamlConfig internal constructor(
    base: DocumentMapperConfig,
    /** `format()` 是否默认输出块格式 YAML。 */
    public val pretty: Boolean,
    /** 单文档允许的非标量 alias 最大数量；`0` 表示使用 SnakeYAML 默认值。 */
    public val maxAliases: Int = 0,
    /**
     * 是否允许未知自定义 tag（如 `!foo`、`!env`、`!!foo` 与
     * `!<tag:example.com,2024:foo>`）。
     *
     * 默认 `false`，保持 SnakeYAML `SafeConstructor` 行为：未知 tag 抛出
     * [com.mingliqiye.document.format.yaml.exception.YamlParseException]。
     * 开启后按节点种类还原：标量 -> StringNode，序列 -> ArrayNode，
     * 映射 -> ObjectNode；同时作用于 Node 路径（[YamlReader]）与原生 Token 路径
     * （[YamlMapper.parse]）。构造阶段只还原基础类型，不会实例化任意 Java 类型。
     */
    public val allowCustomTags: Boolean = false,
    /**
     * 读取特性集合，默认 [DEFAULT_READ_FEATURES]（对齐 上游实现 YAML 2.15）。
     */
    public val readFeatures: Set<YamlReadFeature> = DEFAULT_READ_FEATURES,
    /**
     * 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]（对齐 上游实现 YAML 2.15）。
     */
    public val writeFeatures: Set<YamlWriteFeature> = DEFAULT_WRITE_FEATURES,
    /**
     * 是否允许重复 mapping key，默认 `false`（重复 key 作为格式错误上报）。
     *
     * 对应 SnakeYAML `LoaderOptions.setAllowDuplicateKeys`；开启后后出现的值覆盖
     * 先出现的值。
     */
    public val allowDuplicateKeys: Boolean = false,
    /**
     * 单次输入最大 code point 数量，`0` 表示使用 SnakeYAML 默认值（3 MiB）。
     *
     * 对应 SnakeYAML `LoaderOptions.setCodePointLimit`。
     */
    public val codePointLimit: Int = 0,
    /**
     * 块式输出缩进空格数，默认 `2`（SnakeYAML 允许 2..9）。
     */
    public val indent: Int = 2,
    /**
     * 输出行宽（best width），`0` 表示使用 SnakeYAML 默认值（80）。
     *
     * 对应 SnakeYAML `DumperOptions.setWidth`。
     */
    public val lineWidth: Int = 0,
    /**
     * 是否直接输出 Unicode 字符（`false` 时转义），默认 `true`。
     *
     * 对应 SnakeYAML `DumperOptions.setAllowUnicode`。
     */
    public val allowUnicode: Boolean = true,
    /**
     * 是否在文档末尾输出显式结束标记 `...`，默认 `false`。
     *
     * 对应 SnakeYAML `DumperOptions.setExplicitEnd`。
     */
    public val explicitEnd: Boolean = false,
    /**
     * 块式/流式布局；`null` 时由 [pretty] 推导，保持历史行为。
     *
     * 对应 SnakeYAML `DumperOptions.setDefaultFlowStyle`。上游实现 YAML 始终使用
     * block 布局，需要完全对齐时可配置 [YamlFlowStyle.BLOCK]。
     */
    public val defaultFlowStyle: YamlFlowStyle? = null,
) : DocumentMapperConfig(base) {

    init {
        require(maxAliases >= 0) { "maxAliases must be >= 0" }
        require(codePointLimit >= 0) { "codePointLimit must be >= 0" }
        require(indent in MIN_INDENT..MAX_INDENT) { "indent must be in $MIN_INDENT..$MAX_INDENT, but was $indent" }
        require(lineWidth >= 0) { "lineWidth must be >= 0" }
    }

    /** 是否启用指定读取特性。 */
    public fun isReadEnabled(feature: YamlReadFeature): Boolean = feature in readFeatures

    /** 是否启用指定写出特性。 */
    public fun isWriteEnabled(feature: YamlWriteFeature): Boolean = feature in writeFeatures

    /** 空 plain 标量是否读取为 null。 */
    public val emptyStringAsNull: Boolean
        get() = YamlReadFeature.EMPTY_STRING_AS_NULL in readFeatures

    /** 布尔词是否按字符串读取。 */
    public val parseBooleanLikeWordsAsStrings: Boolean
        get() = YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS in readFeatures

    public companion object {

        /** 块式输出最小缩进（SnakeYAML 限制）。 */
        public const val MIN_INDENT: Int = 2

        /** 块式输出最大缩进（SnakeYAML 限制）。 */
        public const val MAX_INDENT: Int = 9

        /**
         * 默认读取特性，对齐 上游实现 YAML 2.15 `YAMLParser.Feature` 默认值：
         * `EMPTY_STRING_AS_NULL` 默认开启。
         */
        @JvmField
        public val DEFAULT_READ_FEATURES: Set<YamlReadFeature> = setOf(YamlReadFeature.EMPTY_STRING_AS_NULL)

        /**
         * 默认写出特性，对齐 上游实现 YAML 2.15 `YAMLGenerator.Feature` 默认值：
         * `WRITE_DOC_START_MARKER`、`SPLIT_LINES`、`USE_NATIVE_OBJECT_ID`、
         * `USE_NATIVE_TYPE_ID` 默认开启。
         */
        @JvmField
        public val DEFAULT_WRITE_FEATURES: Set<YamlWriteFeature> = setOf(
            YamlWriteFeature.WRITE_DOC_START_MARKER,
            YamlWriteFeature.SPLIT_LINES,
            YamlWriteFeature.USE_NATIVE_OBJECT_ID,
            YamlWriteFeature.USE_NATIVE_TYPE_ID,
        )

        /**
         * 创建 YAML 配置构建器。
         */
        @JvmStatic
        public fun builder(): YamlConfigBuilder = YamlConfigBuilder()
    }
}
