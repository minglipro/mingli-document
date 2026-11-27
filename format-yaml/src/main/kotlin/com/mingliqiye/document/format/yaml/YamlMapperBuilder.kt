package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [YamlMapper] 构建器。
 */
public class YamlMapperBuilder internal constructor() : DocumentMapperBuilder<YamlMapper>() {

    override fun createMapper(
        serializers: Map<Class<*>, JsonSerializer<*>>,
        deserializers: Map<Class<*>, JsonDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): YamlMapper = YamlMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver)
}
