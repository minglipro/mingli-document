package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * XML 文档映射器。
 *
 * 使用 [DocumentMapper] 的通用 Node 映射能力，并通过 `type` / `nil` 属性保存 XML
 * 无法原生表达的类型信息。
 */
public open class XmlMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "xml",
    { source -> XmlReader(source).read() },
    { node, pretty ->
        val rootName = (config as? XmlConfig)?.rootName ?: "root"
        XmlWriter(rootName).write(node, pretty)
    },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    /**
     * 创建默认 XML 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用 XML 配置创建映射器。
     *
     * @param config XML 配置
     */
    public constructor(config: XmlConfig) : this(emptyMap(), emptyMap(), null, null, config)

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

    public companion object {

        /**
         * 创建 XML Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): XmlMapperBuilder = XmlMapperBuilder()
    }
}
