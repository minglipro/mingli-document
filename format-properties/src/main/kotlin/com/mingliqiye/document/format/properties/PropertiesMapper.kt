package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * Java Properties 文档映射器。
 *
 * 平铺 key 使用 `.` 表示对象路径，`[index]` 表示数组下标。
 */
public open class PropertiesMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
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

    public companion object {

        /**
         * 创建 Properties Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): PropertiesMapperBuilder = PropertiesMapperBuilder()
    }
}
