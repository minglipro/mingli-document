package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * JSON 文档映射器。
 *
 * 与 Jackson 的 `JsonMapper` 类似：继承通用 [DocumentMapper]，内置 JSON 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部 Codec SPI。
 */
public open class JsonMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "json",
    { source -> JsonReader(source).read() },
    { node, pretty -> JsonWriter(pretty).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val jsonConfig: JsonConfig? = config as? JsonConfig

    /**
     * 创建默认 JSON 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用字段配置创建 JSON 映射器。
     *
     * @param config Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    override fun <T> parse(source: String, targetType: Class<T>): T = parseDirect(source, DocumentTypeReference.of(targetType))

    override fun <T> parse(source: String, targetType: DocumentTypeReference<T>): T = parseDirect(source, targetType)

    private fun <T> parseDirect(source: String, targetType: DocumentTypeReference<T>): T {
        val reader = JsonValueReader(source, jsonConfig?.allowComments ?: true)
        val result = readFrom(reader, targetType)
        reader.ensureEnd()
        return result
    }

    override fun format(value: Any?): String = writeJson(value, pretty = jsonConfig?.pretty ?: false)

    override fun formatPretty(value: Any?): String = writeJson(value, pretty = true)

    private fun writeJson(value: Any?, pretty: Boolean): String {
        val writer = JsonStreamWriter(pretty)
        writeTo(value, writer)
        return writer.toString()
    }
    public companion object {

        /**
         * 创建 JSON Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): JsonMapperBuilder = JsonMapperBuilder()
    }
}
