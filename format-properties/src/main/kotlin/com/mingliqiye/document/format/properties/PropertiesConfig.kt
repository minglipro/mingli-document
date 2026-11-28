package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * Properties Mapper 配置。
 *
 * 在通用 [DocumentMapperConfig]（字段策略、命名策略、文件/流选项、Node 限制、引用、
 * 反序列化特性等）基础上，增加与 上游实现 `Properties 底层库` 的
 * `JavaPropsSchema` 对齐的格式选项：
 *
 * - 路径：`pathSeparator`、`pathSeparatorEscapeChar`；
 * - 数组：`indexMarker`、`parseSimpleIndexes`（读取特性）
 *   [PropertiesReadFeature.PARSE_SIMPLE_INDEXES]、
 *   `firstArrayOffset`、`writeIndexUsingMarkers`（写出特性）
 *   [PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS]；
 * - 前缀：`prefix`；
 * - 输出格式：`keyValueSeparator`、`lineEnding`、`lineIndentation`、`header`；
 * - 本模块扩展：百分号编码路径段（默认开启，见
 *   [PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS] /
 *   [PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS]）；
 * - 安全限制：[maxDepth]。
 *
 * 上游实现 2.15 并没有 `PropertiesReadFeature` / `PropertiesWriteFeature` 枚举
 * （其格式配置集中在 `JavaPropsSchema`）；本模块仍提供同风格的特性集合与
 * enable/disable/configure API，便于统一使用。所有 上游实现 同名选项的默认值与
 * 上游实现 `JavaPropsSchema` 一致；仅本模块扩展特性按“向后兼容”默认开启，
 * 关闭后即为纯 上游实现 语义（见各特性说明）。
 *
 * 配置不可变，通过 [builder] 构建。
 */
public class PropertiesConfig internal constructor(
    base: DocumentMapperConfig,
    /**
     * 逻辑路径分隔符，默认 `.`（上游实现 默认）。
     *
     * 空字符串表示不按分隔符拆分嵌套（等价 [PropertiesConfigBuilder.withoutPathSeparator]）；
     * 多字符分隔符受支持（例如 `->`）。
     */
    public val pathSeparator: String = DEFAULT_PATH_SEPARATOR,
    /**
     * 路径分隔符转义字符，默认 `'\u0000'`（关闭，上游实现 默认）。
     *
     * 仅在 [pathSeparator] 为单个字符时生效：`a#.b` 在分隔符 `.`、转义字符 `#` 下
     * 解析为字段 `a.b`。注意 JDK `Properties.load` 会先处理反斜杠转义，使用 `\`
     * 作为转义字符时需注意双重转义。
     */
    public val pathSeparatorEscapeChar: Char = DEFAULT_PATH_SEPARATOR_ESCAPE_CHAR,
    /**
     * 数组下标标记，默认 `[` `]`（上游实现 默认）；`null` 表示不识别/不使用下标标记。
     */
    public val indexMarker: PropertiesIndexMarker? = DEFAULT_INDEX_MARKER,
    /**
     * 数组下标起始编号（仅写出），默认 `1`（上游实现 默认）。
     *
     * 关闭 [PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS] 时元素 `i` 写成
     * `i + firstArrayOffset`；开启时写成 `[i + firstArrayOffset]`。读取时不使用该值，
     * 下标只用于排序（与 上游实现 相同）。
     */
    public val firstArrayOffset: Int = DEFAULT_FIRST_ARRAY_OFFSET,
    /**
     * 键与值之间的分隔字符串（仅写出），默认 `=`（上游实现 默认）。
     *
     * 可包含空白或使用 `:` 等 Properties 合法分隔符。
     */
    public val keyValueSeparator: String = DEFAULT_KEY_VALUE_SEPARATOR,
    /**
     * 每行结尾字符串（仅写出），默认 `"\n"`（上游实现 默认）。
     */
    public val lineEnding: String = DEFAULT_LINE_ENDING,
    /**
     * 每行前缀缩进字符串（仅写出），默认空串（上游实现 默认）。
     */
    public val lineIndentation: String = DEFAULT_LINE_INDENTATION,
    /**
     * 输出头部原始文本（仅写出），默认空串（上游实现 默认）。
     *
     * 内容原样输出在所有条目之前，不追加换行；通常用于写入 `#` 注释行，
     * 需要自行在末尾包含换行（上游实现 语义）。
     */
    public val header: String = DEFAULT_HEADER,
    /**
     * 写出前缀（写出时加到根路径前）与读取前缀（读取时从 key 剥离该前缀），
     * 默认 `null`（不启用，上游实现 默认）。非空时表现为 `prefix + pathSeparator + ...`。
     */
    public val prefix: String? = null,
    /**
     * 解析时允许的最大键路径嵌套深度（根对象深度为 `1`），默认 [DEFAULT_MAX_DEPTH]（512）。
     *
     * 超限时 Node 路径与 Token 路径都抛
     * [com.mingliqiye.document.format.properties.exception.PropertiesParseException]；
     * 与 [DocumentMapperConfig.maxNodeDepth] 同时设置时二者分别生效（前者按路径深度，
     * 后者按 Node 树限制抛
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]）。
     */
    public val maxDepth: Int = DEFAULT_MAX_DEPTH,
    /**
     * 读取特性集合，默认 [DEFAULT_READ_FEATURES]：
     * [PropertiesReadFeature.PARSE_SIMPLE_INDEXES]、
     * [PropertiesReadFeature.ALLOW_INDEX_MARKERS]、
     * [PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS]。
     */
    public val readFeatures: Set<PropertiesReadFeature> = DEFAULT_READ_FEATURES,
    /**
     * 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]：
     * [PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS]。
     */
    public val writeFeatures: Set<PropertiesWriteFeature> = DEFAULT_WRITE_FEATURES,
) : DocumentMapperConfig(base) {

    init {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
    }

    /** 是否把纯数字路径段解析为数组下标（读取特性）。 */
    public val parseSimpleIndexes: Boolean
        get() = PropertiesReadFeature.PARSE_SIMPLE_INDEXES in readFeatures

    /** 是否允许 [indexMarker] 下标标记（读取特性）。 */
    public val allowIndexMarkers: Boolean
        get() = PropertiesReadFeature.ALLOW_INDEX_MARKERS in readFeatures

    /** 是否解码路径段中的百分号编码（读取特性，本模块扩展）。 */
    public val decodePercentEscapedSegments: Boolean
        get() = PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS in readFeatures

    /** 数组下标是否使用 [indexMarker] 标记写出（写出特性；未配置标记时始终为 `false`）。 */
    public val writeIndexUsingMarkers: Boolean
        get() = PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS in writeFeatures && indexMarker != null

    /** 是否把字段名中的 `%`、`.`、`[`、`]` 写成百分号编码（写出特性，本模块扩展）。 */
    public val escapePercentEncodedSegments: Boolean
        get() = PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS in writeFeatures

    /** 是否把非 ASCII 字符写成 `\uXXXX`（写出特性）。 */
    public val escapeNonAscii: Boolean
        get() = PropertiesWriteFeature.ESCAPE_NON_ASCII in writeFeatures

    /** 读取侧实际生效的下标标记；关闭 [PropertiesReadFeature.ALLOW_INDEX_MARKERS] 时为 `null`。 */
    internal val readIndexMarker: PropertiesIndexMarker?
        get() = if (allowIndexMarkers) indexMarker else null

    public companion object {

        /** 默认路径分隔符：`.`（上游实现 默认）。 */
        public const val DEFAULT_PATH_SEPARATOR: String = "."

        /** 默认路径分隔符转义字符：`'\u0000'`，即关闭（上游实现 默认）。 */
        public const val DEFAULT_PATH_SEPARATOR_ESCAPE_CHAR: Char = '\u0000'

        /** 默认数组起始下标：`1`（上游实现 默认）。 */
        public const val DEFAULT_FIRST_ARRAY_OFFSET: Int = 1

        /** 默认键值分隔符：`=`（上游实现 默认）。 */
        public const val DEFAULT_KEY_VALUE_SEPARATOR: String = "="

        /** 默认行结尾：`"\n"`（上游实现 默认）。 */
        public const val DEFAULT_LINE_ENDING: String = "\n"

        /** 默认行缩进：空串（上游实现 默认）。 */
        public const val DEFAULT_LINE_INDENTATION: String = ""

        /** 默认头部：空串（上游实现 默认）。 */
        public const val DEFAULT_HEADER: String = ""

        /** 默认最大解析深度：512（本模块安全上限，上游实现 无此 schema 选项）。 */
        public const val DEFAULT_MAX_DEPTH: Int = 512

        /** 默认下标标记：`[` `]`（上游实现 默认）。 */
        @JvmField
        public val DEFAULT_INDEX_MARKER: PropertiesIndexMarker = PropertiesIndexMarker.DEFAULT

        /** 默认读取特性：简单下标 + 下标标记 + 百分号解码扩展。 */
        @JvmField
        public val DEFAULT_READ_FEATURES: Set<PropertiesReadFeature> = setOf(
            PropertiesReadFeature.PARSE_SIMPLE_INDEXES,
            PropertiesReadFeature.ALLOW_INDEX_MARKERS,
            PropertiesReadFeature.DECODE_PERCENT_ESCAPED_SEGMENTS,
        )

        /** 默认写出特性：百分号编码扩展。 */
        @JvmField
        public val DEFAULT_WRITE_FEATURES: Set<PropertiesWriteFeature> = setOf(
            PropertiesWriteFeature.ESCAPE_PERCENT_ENCODED_SEGMENTS,
        )

        /**
         * 创建 Properties 配置构建器。
         */
        @JvmStatic
        public fun builder(): PropertiesConfigBuilder = PropertiesConfigBuilder()

        /**
         * 默认 Properties 配置（含默认格式选项与安全深度）。
         */
        @JvmStatic
        public fun empty(): PropertiesConfig = PropertiesConfig(DocumentMapperConfig.empty())

        /**
         * 把通用配置包装成 Properties 配置。
         *
         * 已经是 [PropertiesConfig] 时原样返回；否则保留通用配置并套用默认格式选项。
         */
        internal fun from(base: DocumentMapperConfig): PropertiesConfig = base as? PropertiesConfig ?: PropertiesConfig(base)

        /**
         * 用指定解析深度包装通用配置。
         *
         * 已经是 [PropertiesConfig] 时保留其格式选项，仅覆盖 [maxDepth]。
         */
        internal fun from(base: DocumentMapperConfig, maxDepth: Int): PropertiesConfig {
            if (base !is PropertiesConfig) {
                return PropertiesConfig(base, maxDepth = maxDepth)
            }
            return PropertiesConfig(
                base,
                pathSeparator = base.pathSeparator,
                pathSeparatorEscapeChar = base.pathSeparatorEscapeChar,
                indexMarker = base.indexMarker,
                firstArrayOffset = base.firstArrayOffset,
                keyValueSeparator = base.keyValueSeparator,
                lineEnding = base.lineEnding,
                lineIndentation = base.lineIndentation,
                header = base.header,
                prefix = base.prefix,
                maxDepth = maxDepth,
                readFeatures = base.readFeatures,
                writeFeatures = base.writeFeatures,
            )
        }
    }
}
