package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [JsonMapper] 构建器。
 */
public class JsonMapperBuilder internal constructor() : DocumentMapperBuilder<JsonMapper>() {

    override fun createMapper(
        serializers: Map<Class<*>, JsonSerializer<*>>,
        deserializers: Map<Class<*>, JsonDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): JsonMapper = JsonMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver)
}
