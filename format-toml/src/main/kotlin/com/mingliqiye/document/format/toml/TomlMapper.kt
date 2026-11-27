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
 */
public open class TomlMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "toml",
    { source -> TomlReader(source).read() },
    { node, _ -> TomlWriter().write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    /**
     * 创建默认 TOML 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用字段配置创建映射器。
     *
     * @param config 通用 Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    override fun createTextTokenReader(source: String): DocumentTokenReader {
        val node = TomlReader(source).read()
        validateNodeLimits(node)
        return NodeTokenReader(node)
    }

    public companion object {

        /**
         * 创建 TOML Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): TomlMapperBuilder = TomlMapperBuilder()
    }
}
