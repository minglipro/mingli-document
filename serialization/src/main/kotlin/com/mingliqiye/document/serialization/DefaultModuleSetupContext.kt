package com.mingliqiye.document.serialization

import java.util.LinkedHashMap

/**
 * [ModuleSetupContext] 默认实现。
 */
internal class DefaultModuleSetupContext : ModuleSetupContext {

    private val serializers = LinkedHashMap<Class<*>, DocumentBaseSerializer<*>>()
    private val deserializers = LinkedHashMap<Class<*>, DocumentBaseDeserializer<*>>()
    private var objectInstantiator: ObjectInstantiator? = null
    private var propertyMetadataResolver: PropertyMetadataResolver? = null

    override fun addSerializer(serializer: DocumentBaseSerializer<*>) {
        serializers[serializer.getType().getRawType()] = serializer
    }

    override fun addDeserializer(deserializer: DocumentBaseDeserializer<*>) {
        deserializers[deserializer.getType().getRawType()] = deserializer
    }

    override fun addConverter(converter: DocumentBaseConverter<*>) {
        addSerializer(converter)
        addDeserializer(converter)
    }

    override fun setObjectInstantiator(instantiator: ObjectInstantiator?) {
        objectInstantiator = instantiator
    }

    override fun setPropertyMetadataResolver(resolver: PropertyMetadataResolver?) {
        propertyMetadataResolver = resolver
    }

    fun serializers(): Map<Class<*>, DocumentBaseSerializer<*>> = serializers

    fun deserializers(): Map<Class<*>, DocumentBaseDeserializer<*>> = deserializers

    fun objectInstantiator(): ObjectInstantiator? = objectInstantiator

    fun propertyMetadataResolver(): PropertyMetadataResolver? = propertyMetadataResolver
}
