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
 * 除通用 [DocumentMapperBuilder] 能力外，可通过 [maxDepth] 调整解析最大嵌套深度。
 */
public class TomlMapperBuilder internal constructor() : DocumentMapperBuilder<TomlMapper>() {

    private var maxDepth = TomlMapper.DEFAULT_MAX_DEPTH

    /**
     * 设置解析最大嵌套深度，默认 [TomlMapper.DEFAULT_MAX_DEPTH]（512）。
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
    ): TomlMapper = TomlMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver, config, maxDepth)
}
