package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapperBuilder
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * [CsvMapper] 构建器。
 *
 * 除通用配置外可通过 [schema] 指定 CSV Schema；同时通过 [config] 设置的
 * [CsvConfig.schema] 会被 [schema] 覆盖。
 */
public class CsvMapperBuilder internal constructor() : DocumentMapperBuilder<CsvMapper>() {

    private var csvSchema: CsvSchema? = null

    /**
     * 设置 CSV Schema。
     *
     * @param schema CSV Schema；null 表示不使用 Schema
     * @return 当前构建器
     */
    public fun schema(schema: CsvSchema?): CsvMapperBuilder {
        csvSchema = schema
        return this
    }

    override fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): CsvMapper = createMapper(
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
    ): CsvMapper = CsvMapper(
        serializers,
        deserializers,
        objectInstantiator,
        propertyMetadataResolver,
        withSchema(config),
    )

    private fun withSchema(config: DocumentMapperConfig): DocumentMapperConfig {
        val schema = csvSchema ?: return config
        val csvConfig = config as? CsvConfig
        if (csvConfig == null) {
            return CsvConfig.builder()
                .schema(schema)
                .missingFieldPolicy(config.missingFieldPolicy)
                .failOnUnknownFields(config.failOnUnknownFields)
                .writeNulls(config.writeNulls)
                .build()
        }
        return CsvConfig(csvConfig, csvConfig.delimiter, csvConfig.header, schema, csvConfig.nestedMode, csvConfig.maxDepth)
    }
}
