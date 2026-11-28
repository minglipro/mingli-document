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
 * 与 上游实现 的 `TomlMapper` 类似：继承通用 [DocumentMapper]，内置 TOML 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部读写实现。
 *
 * TOML 专属配置见 [TomlConfig]：
 * - 读取特性 [TomlReadFeature]：`PARSE_JAVA_TIME`（默认关闭）与 `VALIDATE_DATE_TIME`
 *   （默认关闭，上游实现 3.2 起）；
 * - 写出特性 [TomlWriteFeature]：`FAIL_ON_NULL_WRITE`（默认关闭，关闭时 null 写成 `""`）；
 * - 解析深度 [TomlConfig.maxDepth]（默认 [DEFAULT_MAX_DEPTH] = 512，根表深度为 `1`）；
 * - 注释保留写出配置 [TomlConfig.commentConfig]。
 *
 * 使用 [builder] 可便捷开启/关闭上述特性。Tree / Token / Bean 三条解析路径都会在
 * 超限时抛出 [com.mingliqiye.document.format.toml.exception.TomlParseException]。
 *
 * 注释保留（可选能力）：[parseCommented] / [formatCommented] 提供从 Mapper 直接访问
 * [TomlCommentPreservingReader] / [TomlCommentPreservingWriter] 的便捷入口，默认使用
 * 当前 Mapper 的 [TomlConfig.commentConfig] 与写出特性。
 */
public open class TomlMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    /** 当前 Mapper 的 TOML 配置。 */
    public val tomlConfig: TomlConfig,
) : DocumentMapper(
    "toml",
    { source -> TomlReader(source, tomlConfig.maxDepth, tomlConfig.readFeatures).read() },
    { node, _ -> TomlWriter(tomlConfig.writeFeatures).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    tomlConfig,
) {

    /**
     * 创建默认 TOML 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, TomlConfig(DocumentMapperConfig.empty()))

    /**
     * 使用 TOML 配置创建映射器。
     *
     * @param config TOML 配置
     */
    public constructor(config: TomlConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用通用字段配置创建映射器。
     *
     * 解析深度与 TOML 特性使用 [TomlConfig] 默认值。
     *
     * @param config 通用 Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, TomlConfig.from(config))

    /**
     * 使用通用字段配置与显式解析深度创建映射器。
     *
     * @param config 通用 Mapper 配置
     * @param maxDepth 解析最大嵌套深度（根表深度为 `1`），必须为正数
     */
    public constructor(config: DocumentMapperConfig, maxDepth: Int) : this(
        emptyMap(),
        emptyMap(),
        null,
        null,
        withMaxDepth(config, maxDepth),
    )

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, TomlConfig(DocumentMapperConfig.empty()))

    /**
     * 是否开启指定 TOML 读取特性。
     *
     * @param feature 读取特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: TomlReadFeature): Boolean = tomlConfig.isEnabled(feature)

    /**
     * 是否开启指定 TOML 写出特性。
     *
     * @param feature 写出特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: TomlWriteFeature): Boolean = tomlConfig.isEnabled(feature)

    /**
     * 创建 TOML 文本 Token Reader。
     *
     * 未配置 Node 树限制时使用原生 [TomlTokenReader]；配置了非零
     * `maxNodeDepth` / `maxNodeCount` / `maxStringLength` 时先经 [TomlReader] 构建 Node 并
     * 执行 [validateNodeLimits]，再回退 [NodeTokenReader]。
     *
     * @param source TOML 文本
     * @return Token Reader
     */
    override fun createTextTokenReader(source: String): DocumentTokenReader {
        if (tomlConfig.maxNodeDepth > 0 || tomlConfig.maxNodeCount > 0 || tomlConfig.maxStringLength > 0) {
            val node = TomlReader(source, tomlConfig.maxDepth, tomlConfig.readFeatures).read()
            validateNodeLimits(node)
            return NodeTokenReader(node)
        }
        return TomlTokenReader(source, tomlConfig.maxDepth, tomlConfig.readFeatures)
    }

    /**
     * 读取保留注释的 TOML 文档。
     *
     * 等价于
     * `TomlCommentPreservingReader(source, tomlConfig.maxDepth, tomlConfig.readFeatures).read()`；
     * 语法非法或深度超限时抛出
     * [com.mingliqiye.document.format.toml.exception.TomlParseException]。
     *
     * @param source TOML 文本
     * @return 保留注释的文档
     */
    public fun parseCommented(source: String): TomlCommentedDocument = TomlCommentPreservingReader(source, tomlConfig.maxDepth, tomlConfig.readFeatures).read()

    /**
     * 使用当前 Mapper 的注释写出配置写出保留注释的 TOML 文档。
     *
     * 等价于 `formatCommented(document, tomlConfig.commentConfig)`。
     *
     * @param document 保留注释的文档
     * @return TOML 文本
     */
    public fun formatCommented(document: TomlCommentedDocument): String = formatCommented(document, tomlConfig.commentConfig)

    /**
     * 使用指定注释写出配置写出保留注释的 TOML 文档。
     *
     * 写出特性沿用当前 Mapper 的 [TomlConfig.writeFeatures]（例如 null 处理）。
     *
     * @param document 保留注释的文档
     * @param config 注释写出配置
     * @return TOML 文本
     */
    public fun formatCommented(document: TomlCommentedDocument, config: TomlCommentConfig): String = TomlCommentPreservingWriter(config, tomlConfig.writeFeatures).write(document)

    public companion object {

        /**
         * 默认解析最大嵌套深度（根表深度为 `1`）。
         */
        public const val DEFAULT_MAX_DEPTH: Int = TomlConfig.DEFAULT_MAX_DEPTH

        /**
         * 创建 TOML Mapper 构建器。
         *
         * @return Mapper 构建器
         */
        @JvmStatic
        public fun builder(): TomlMapperBuilder = TomlMapperBuilder()
    }
}

/**
 * 用显式解析深度覆盖通用配置（已有 [TomlConfig] 时保留其特性/注释配置）。
 */
private fun withMaxDepth(config: DocumentMapperConfig, maxDepth: Int): TomlConfig {
    require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
    val base = TomlConfig.from(config)
    if (maxDepth == base.maxDepth) {
        return base
    }
    return TomlConfig(base, maxDepth, base.readFeatures, base.writeFeatures, base.commentConfig)
}
