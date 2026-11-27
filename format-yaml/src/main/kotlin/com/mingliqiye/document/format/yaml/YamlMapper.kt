package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * YAML 文档映射器。
 *
 * 与 Jackson 的 `YAMLMapper` 类似：继承通用 [DocumentMapper]，内置 YAML 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部 Codec SPI。
 */
public open class YamlMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
) : DocumentMapper(
    "yaml",
    { source -> YamlReader(source).read() },
    { node, pretty -> YamlWriter(pretty).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
) {

    /**
     * 创建默认 YAML 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null)

    public companion object {

        /**
         * 创建 YAML Mapper 构建器。
         */
        public fun builder(): YamlMapperBuilder = YamlMapperBuilder()
    }
}
