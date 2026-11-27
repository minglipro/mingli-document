package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.DocumentMapper
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
) : DocumentMapper(
    "json",
    { source -> JsonReader(source).read() },
    { node, pretty -> JsonWriter(pretty).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
) {

    /**
     * 创建默认 JSON 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null)

    override fun format(value: Any?): String = writeJson(value, pretty = false)

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
        public fun builder(): JsonMapperBuilder = JsonMapperBuilder()
    }
}
