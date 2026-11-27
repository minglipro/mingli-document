package com.mingliqiye.document.format.avro

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver
import org.apache.avro.Schema

/**
 * [AvroMapper] 构建器。
 *
 * @param writerSchema writer Schema
 * @param readerSchema reader Schema；与 writer Schema 相同表示不做演进
 */
public class AvroMapperBuilder internal constructor(
    private val writerSchema: Schema,
    private val readerSchema: Schema,
) : DocumentMapperBuilder<AvroMapper>() {

    override fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): AvroMapper = createMapper(
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
    ): AvroMapper = AvroMapper(
        writerSchema,
        readerSchema,
        serializers,
        deserializers,
        objectInstantiator,
        propertyMetadataResolver,
        config,
    )
}
