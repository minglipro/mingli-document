package com.mingliqiye.document.format.smile

import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [SmileMapper] 构建器。
 */
public class SmileMapperBuilder internal constructor() : DocumentMapperBuilder<SmileMapper>() {

    override fun createMapper(
        serializers: Map<Class<*>, JsonSerializer<*>>,
        deserializers: Map<Class<*>, JsonDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): SmileMapper = createMapper(
        serializers,
        deserializers,
        objectInstantiator,
        propertyMetadataResolver,
        DocumentMapperConfig.empty(),
    )

    override fun createMapper(
        serializers: Map<Class<*>, JsonSerializer<*>>,
        deserializers: Map<Class<*>, JsonDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
        config: DocumentMapperConfig,
    ): SmileMapper = SmileMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver, config)
}
