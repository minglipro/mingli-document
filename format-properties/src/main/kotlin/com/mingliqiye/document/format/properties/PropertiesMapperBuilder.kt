package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [PropertiesMapper] 构建器。
 */
public class PropertiesMapperBuilder internal constructor() : DocumentMapperBuilder<PropertiesMapper>() {

    override fun createMapper(
        serializers: Map<Class<*>, JsonSerializer<*>>,
        deserializers: Map<Class<*>, JsonDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): PropertiesMapper = createMapper(
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
    ): PropertiesMapper = PropertiesMapper(
        serializers,
        deserializers,
        objectInstantiator,
        propertyMetadataResolver,
        config,
    )
}
