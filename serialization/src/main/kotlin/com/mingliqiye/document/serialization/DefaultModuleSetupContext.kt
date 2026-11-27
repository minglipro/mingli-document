package com.mingliqiye.document.serialization

import java.util.LinkedHashMap

/**
 * [ModuleSetupContext] 默认实现。
 */
internal class DefaultModuleSetupContext : ModuleSetupContext {

    private val serializers = LinkedHashMap<Class<*>, JsonSerializer<*>>()
    private val deserializers = LinkedHashMap<Class<*>, JsonDeserializer<*>>()
    private var objectInstantiator: ObjectInstantiator? = null
    private var propertyMetadataResolver: PropertyMetadataResolver? = null

    override fun addSerializer(serializer: JsonSerializer<*>) {
        serializers[serializer.getType().getRawType()] = serializer
    }

    override fun addDeserializer(deserializer: JsonDeserializer<*>) {
        deserializers[deserializer.getType().getRawType()] = deserializer
    }

    override fun addConverter(converter: JsonConverter<*>) {
        addSerializer(converter)
        addDeserializer(converter)
    }

    override fun setObjectInstantiator(instantiator: ObjectInstantiator?) {
        objectInstantiator = instantiator
    }

    override fun setPropertyMetadataResolver(resolver: PropertyMetadataResolver?) {
        propertyMetadataResolver = resolver
    }

    fun serializers(): Map<Class<*>, JsonSerializer<*>> = serializers

    fun deserializers(): Map<Class<*>, JsonDeserializer<*>> = deserializers

    fun objectInstantiator(): ObjectInstantiator? = objectInstantiator

    fun propertyMetadataResolver(): PropertyMetadataResolver? = propertyMetadataResolver
}
