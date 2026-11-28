package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [PropertiesMapper] 构建器。
 *
 * 除通用 [DocumentMapperBuilder] 能力外：
 * - 通过 [maxDepth] 调整解析最大嵌套深度；
 * - 通过 [config] 传入 [PropertiesConfig]（路径分隔符、下标标记、前缀、输出格式、
 *   读写特性等）。
 */
public class PropertiesMapperBuilder internal constructor() : DocumentMapperBuilder<PropertiesMapper>() {

    private var maxDepth = PropertiesMapper.DEFAULT_MAX_DEPTH
    private var maxDepthConfigured = false

    /**
     * 设置解析最大嵌套深度，默认 [PropertiesMapper.DEFAULT_MAX_DEPTH]（512）。
     *
     * 根对象深度为 `1`，`a.b.c=1` 的最深层级为 `4`；超限时解析抛出
     * [com.mingliqiye.document.format.properties.exception.PropertiesParseException]。
     * 通过 [config] 传入 [PropertiesConfig] 时，本方法显式设置的值优先于配置中的
     * `maxDepth`。
     *
     * @param maxDepth 最大嵌套深度，必须为正数
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于等于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): PropertiesMapperBuilder {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        this.maxDepth = maxDepth
        maxDepthConfigured = true
        return this
    }

    /**
     * 设置 Properties 配置。
     *
     * @param config Properties 配置
     * @return 当前构建器
     */
    public fun config(config: PropertiesConfig): PropertiesMapperBuilder {
        super.config(config)
        return this
    }

    override fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): PropertiesMapper = createMapper(
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
    ): PropertiesMapper {
        val propertiesConfig = when {
            config is PropertiesConfig && !maxDepthConfigured -> config
            else -> PropertiesConfig.from(config, maxDepth)
        }
        return PropertiesMapper(
            serializers,
            deserializers,
            objectInstantiator,
            propertyMetadataResolver,
            propertiesConfig,
        )
    }
}
