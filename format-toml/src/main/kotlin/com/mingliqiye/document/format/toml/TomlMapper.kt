package com.mingliqiye.document.format.toml

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * TOML 文档映射器。
 *
 * 与 Jackson 的格式 Mapper 类似：继承通用 [DocumentMapper]，内置 TOML 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部读写实现。
 *
 * 解析深度限制：默认安全上限为 [DEFAULT_MAX_DEPTH]（512，根表深度为 `1`），可通过
 * [builder] 的 `maxDepth` 或 `TomlMapper(config, maxDepth)` 调整。Tree / Token / Bean
 * 三条解析路径都会在超限时抛出
 * [com.mingliqiye.document.format.toml.exception.TomlParseException]。
 *
 * 注释保留（可选能力）：[parseCommented] / [formatCommented] 提供从 Mapper 直接访问
 * [TomlCommentPreservingReader] / [TomlCommentPreservingWriter] 的便捷入口，不影响
 * 普通 [parse] / [format] 行为。
 */
public open class TomlMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) : DocumentMapper(
    "toml",
    { source -> TomlReader(source, maxDepth).read() },
    { node, _ -> TomlWriter().write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    init {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
    }

    private val mapperConfig: DocumentMapperConfig = config

    /**
     * 创建默认 TOML 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用字段配置创建映射器。
     *
     * 解析深度使用默认值 [DEFAULT_MAX_DEPTH]。
     *
     * @param config 通用 Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用通用字段配置与显式解析深度创建映射器。
     *
     * @param config 通用 Mapper 配置
     * @param maxDepth 解析最大嵌套深度（根表深度为 `1`），必须为正数
     */
    public constructor(config: DocumentMapperConfig, maxDepth: Int) :
        this(emptyMap(), emptyMap(), null, null, config, maxDepth)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    override fun createTextTokenReader(source: String): DocumentTokenReader {
        if (mapperConfig.maxNodeDepth > 0 || mapperConfig.maxNodeCount > 0 || mapperConfig.maxStringLength > 0) {
            val node = TomlReader(source, maxDepth).read()
            validateNodeLimits(node)
            return NodeTokenReader(node)
        }
        return TomlTokenReader(source, maxDepth)
    }

    /**
     * 读取保留注释的 TOML 文档。
     *
     * 等价于 `TomlCommentPreservingReader(source, maxDepth).read()`，解析深度沿用当前
     * Mapper 的 [maxDepth]；语法非法或深度超限时抛出
     * [com.mingliqiye.document.format.toml.exception.TomlParseException]。
     *
     * @param source TOML 文本
     * @return 保留注释的文档
     */
    public fun parseCommented(source: String): TomlCommentedDocument = TomlCommentPreservingReader(source, maxDepth).read()

    /**
     * 写出保留注释的 TOML 文档。
     *
     * 等价于 `TomlCommentPreservingWriter(config).write(document)`；只影响注释/空行的
     * 布局策略，与普通 [format] 行为独立。
     *
     * @param document 保留注释的文档
     * @param config 注释写出配置；默认使用 [TomlCommentConfig]
     * @return TOML 文本
     */
    @JvmOverloads
    public fun formatCommented(
        document: TomlCommentedDocument,
        config: TomlCommentConfig = TomlCommentConfig(),
    ): String = TomlCommentPreservingWriter(config).write(document)

    public companion object {

        /**
         * 默认解析最大嵌套深度（根表深度为 `1`）。
         */
        public const val DEFAULT_MAX_DEPTH: Int = 512

        /**
         * 创建 TOML Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): TomlMapperBuilder = TomlMapperBuilder()
    }
}
