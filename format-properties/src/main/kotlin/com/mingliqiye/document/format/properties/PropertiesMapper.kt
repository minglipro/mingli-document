package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * Java Properties 文档映射器。
 *
 * 平铺 key 使用 `.` 表示对象路径，`[index]` 表示数组下标。
 */
public open class PropertiesMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "properties",
    { source -> PropertiesReader(source).read() },
    { node, _ -> PropertiesWriter().write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val mapperConfig: DocumentMapperConfig = config

    /**
     * 创建默认 Properties 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用配置创建映射器。
     *
     * @param config Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    /**
     * 创建 Properties 原生 Token 读取器。
     *
     * 使用 [PropertiesTokenReader] 直接从平铺 key/value 生成 Token 流，不再先构建
     * 完整 Node 树；maxNodeDepth/maxNodeCount/maxStringLength 在生成 Token 时校验，
     * 语义与 Node 路径一致（超限抛
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]）。
     */
    override fun createTextTokenReader(source: String): DocumentTokenReader = PropertiesTokenReader(
        source,
        mapperConfig.maxNodeDepth,
        mapperConfig.maxNodeCount,
        mapperConfig.maxStringLength,
    )

    public companion object {

        /**
         * 创建 Properties Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): PropertiesMapperBuilder = PropertiesMapperBuilder()
    }
}
