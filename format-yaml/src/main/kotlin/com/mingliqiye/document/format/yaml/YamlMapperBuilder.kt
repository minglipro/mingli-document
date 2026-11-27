package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [YamlMapper] 构建器。
 */
public class YamlMapperBuilder internal constructor() : DocumentMapperBuilder<YamlMapper>() {

    override fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): YamlMapper = createMapper(
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
    ): YamlMapper = YamlMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver, config)
}
