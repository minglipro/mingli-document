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
 * Schema 可由三类来源提供（优先级从高到低）：
 * 1. [schema] / [readerSchema] 显式设置；
 * 2. [config] 传入 [AvroConfig] 中的 [AvroConfig.schema] / [AvroConfig.readerSchema]；
 * 3. 只有 writer Schema 时 reader 等于 writer。
 *
 * [AvroConfig] 中的 上游实现 Avro 特性（`AVRO_BUFFERING`、`AVRO_FILE_OUTPUT`、
 * `ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA`）与 codec 同样生效；如果配置了
 * [AvroSchema] 视图，构建时按 `ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA` 转换。
 */
public class AvroMapperBuilder internal constructor(
    initialWriterSchema: Schema?,
    initialReaderSchema: Schema?,
    initialWriterView: AvroSchema?,
    initialReaderView: AvroSchema?,
    initialConfig: AvroConfig?,
) : DocumentMapperBuilder<AvroMapper>() {

    private var writerSchema: Schema? = initialWriterSchema
    private var readerSchema: Schema? = initialReaderSchema
    private var writerView: AvroSchema? = initialWriterView
    private var readerView: AvroSchema? = initialReaderView

    init {
        if (initialConfig != null) {
            super.config(initialConfig)
        }
    }

    /**
     * 设置 writer Schema。
     *
     * @param schema Apache Avro record Schema
     * @return 当前构建器
     */
    public fun schema(schema: Schema): AvroMapperBuilder {
        writerSchema = schema
        writerView = null
        return this
    }

    /**
     * 设置 writer Schema（[AvroSchema] 视图）。
     *
     * @param schema Avro Schema 视图
     * @return 当前构建器
     */
    public fun schema(schema: AvroSchema): AvroMapperBuilder {
        writerView = schema
        writerSchema = null
        return this
    }

    /**
     * 设置 reader Schema（Schema 演进）。
     *
     * @param readerSchema Apache Avro record Schema
     * @return 当前构建器
     */
    public fun readerSchema(readerSchema: Schema): AvroMapperBuilder {
        this.readerSchema = readerSchema
        readerView = null
        return this
    }

    /**
     * 设置 reader Schema（[AvroSchema] 视图）。
     *
     * @param readerSchema Avro Schema 视图
     * @return 当前构建器
     */
    public fun readerSchema(readerSchema: AvroSchema): AvroMapperBuilder {
        readerView = readerSchema
        this.readerSchema = null
        return this
    }

    /**
     * 设置 [AvroConfig]（含 Schema、codec 与 上游实现 Avro 特性）。
     *
     * 与基类 `config(DocumentMapperConfig)` 的区别：本重载接受 [AvroConfig]，
     * 返回 [AvroMapperBuilder] 以支持链式调用。
     *
     * @param config Avro 配置
     * @return 当前构建器
     */
    public fun config(config: AvroConfig): AvroMapperBuilder {
        super.config(config)
        return this
    }

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
        AvroConfig.from(DocumentMapperConfig.empty()),
    )

    override fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
        config: DocumentMapperConfig,
    ): AvroMapper {
        val avroConfig = AvroConfig.from(config)
        val addNullAsDefaultValue = avroConfig.addNullAsDefaultValueInSchema
        val writer = writerSchema
            ?: writerView?.toAvroSchema(addNullAsDefaultValue)
            ?: avroConfig.schema
            ?: throw IllegalArgumentException(
                "AvroMapperBuilder requires a record writer schema; " +
                    "use schema(...) or AvroConfig.builder().schema(...)",
            )
        val reader = readerSchema
            ?: readerView?.toAvroSchema(addNullAsDefaultValue)
            ?: avroConfig.readerSchema
            ?: writer
        return AvroMapper(
            writer,
            reader,
            serializers,
            deserializers,
            objectInstantiator,
            propertyMetadataResolver,
            avroConfig,
        )
    }
}
