package com.mingliqiye.document.format.toml

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [TomlMapper] 构建器。
 *
 * 除通用 [DocumentMapperBuilder] 能力外，提供：
 * - TOML 读取/写出特性 [TomlReadFeature] / [TomlWriteFeature] 的
 *   `enable` / `disable` / `configure`；
 * - 解析最大嵌套深度 [maxDepth]；
 * - 注释保留写出配置（[orphanCommentPolicy] / [preserveBlankLines] /
 *   [trailingNewline] / [commentConfig]），默认值来自 [TomlConfig]，
 *   若通过 [DocumentMapperBuilder.config] 传入 [TomlConfig]，则以传入值为基线再应用这里的覆盖。
 */
public class TomlMapperBuilder internal constructor() : DocumentMapperBuilder<TomlMapper>() {

    private var maxDepth: Int? = null
    private val readFeatureStates = LinkedHashMap<TomlReadFeature, Boolean>()
    private val writeFeatureStates = LinkedHashMap<TomlWriteFeature, Boolean>()
    private var orphanCommentPolicy: TomlOrphanCommentPolicy? = null
    private var preserveBlankLines: Boolean? = null
    private var trailingNewline: Boolean? = null

    /**
     * 设置解析最大嵌套深度，默认 [TomlConfig.DEFAULT_MAX_DEPTH]（512）。
     *
     * 根表深度为 `1`，`[a.b]` 中 `b` 的深度为 `3`；超限时解析抛出
     * [com.mingliqiye.document.format.toml.exception.TomlParseException]。
     *
     * @param maxDepth 最大嵌套深度，必须为正数
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于等于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): TomlMapperBuilder {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        this.maxDepth = maxDepth
        return this
    }

    /**
     * 开启 TOML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: TomlReadFeature): TomlMapperBuilder {
        readFeatureStates[feature] = true
        return this
    }

    /**
     * 关闭 TOML 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: TomlReadFeature): TomlMapperBuilder {
        readFeatureStates[feature] = false
        return this
    }

    /**
     * 设置 TOML 读取特性开关。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: TomlReadFeature, enabled: Boolean): TomlMapperBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 TOML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: TomlWriteFeature): TomlMapperBuilder {
        writeFeatureStates[feature] = true
        return this
    }

    /**
     * 关闭 TOML 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: TomlWriteFeature): TomlMapperBuilder {
        writeFeatureStates[feature] = false
        return this
    }

    /**
     * 设置 TOML 写出特性开关。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: TomlWriteFeature, enabled: Boolean): TomlMapperBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 设置游离注释与“关联键/表已不存在”的注释的保留策略。
     *
     * @param policy 保留策略
     * @return 当前构建器
     */
    public fun orphanCommentPolicy(policy: TomlOrphanCommentPolicy): TomlMapperBuilder {
        this.orphanCommentPolicy = policy
        return this
    }

    /**
     * 设置保留注释写出时是否输出空行。
     *
     * @param preserve 是否保留空行
     * @return 当前构建器
     */
    public fun preserveBlankLines(preserve: Boolean): TomlMapperBuilder {
        this.preserveBlankLines = preserve
        return this
    }

    /**
     * 设置保留注释写出时非空输出是否以换行符结尾。
     *
     * @param write 是否输出结尾换行
     * @return 当前构建器
     */
    public fun trailingNewline(write: Boolean): TomlMapperBuilder {
        this.trailingNewline = write
        return this
    }

    /**
     * 整体合并一份注释写出配置（与逐项设置等价，后设置者优先）。
     *
     * @param config 注释写出配置
     * @return 当前构建器
     */
    public fun commentConfig(config: TomlCommentConfig): TomlMapperBuilder {
        this.orphanCommentPolicy = config.orphanCommentPolicy
        this.preserveBlankLines = config.preserveBlankLines
        this.trailingNewline = config.trailingNewline
        return this
    }

    override fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): TomlMapper = createMapper(
        serializers,
        deserializers,
        objectInstantiator,
        propertyMetadataResolver,
        DocumentMapperConfig.empty(),
    )

    override fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
        config: DocumentMapperConfig,
    ): TomlMapper = TomlMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver, resolve(config))

    /**
     * 把构建器覆盖项合并到 base 配置上；base 为 [TomlConfig] 时以其特性/注释配置为基线。
     */
    private fun resolve(config: DocumentMapperConfig): TomlConfig {
        val base = TomlConfig.from(config)
        val readFeatures = LinkedHashSet(base.readFeatures)
        for ((feature, enabled) in readFeatureStates) {
            if (enabled) {
                readFeatures.add(feature)
            } else {
                readFeatures.remove(feature)
            }
        }
        val writeFeatures = LinkedHashSet(base.writeFeatures)
        for ((feature, enabled) in writeFeatureStates) {
            if (enabled) {
                writeFeatures.add(feature)
            } else {
                writeFeatures.remove(feature)
            }
        }
        val baseComment = base.commentConfig
        val commentConfig = TomlCommentConfig(
            orphanCommentPolicy ?: baseComment.orphanCommentPolicy,
            preserveBlankLines ?: baseComment.preserveBlankLines,
            trailingNewline ?: baseComment.trailingNewline,
        )
        return TomlConfig(base, maxDepth ?: base.maxDepth, readFeatures, writeFeatures, commentConfig)
    }
}
