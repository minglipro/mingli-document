package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.JsonConverter
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import java.util.LinkedHashMap

/**
 * JSON 文档映射器。
 *
 * 与 Jackson 的 `JsonMapper` 类似：继承通用 [DocumentMapper]，内置 JSON 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部 Codec SPI。
 */
public class JsonMapper private constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
) : DocumentMapper(
    "json",
    { source -> JsonReader(source).read() },
    { node, pretty -> JsonWriter(pretty).write(node) },
    serializers,
    deserializers,
) {

    /**
     * 创建默认 JSON 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap())

    public companion object {

        /**
         * 创建 JSON Mapper 构建器。
         */
        public fun builder(): Builder = Builder()
    }

    /**
     * [JsonMapper] 构建器。
     */
    public class Builder internal constructor() {

        private val serializers = LinkedHashMap<Class<*>, JsonSerializer<*>>()
        private val deserializers = LinkedHashMap<Class<*>, JsonDeserializer<*>>()

        /**
         * 注册序列化器。
         */
        public fun addSerializer(serializer: JsonSerializer<*>): Builder {
            serializers[serializer.getType().getRawType()] = serializer
            return this
        }

        /**
         * 注册反序列化器。
         */
        public fun addDeserializer(deserializer: JsonDeserializer<*>): Builder {
            deserializers[deserializer.getType().getRawType()] = deserializer
            return this
        }

        /**
         * 注册双向转换器。
         */
        public fun addConverter(converter: JsonConverter<*>): Builder {
            addSerializer(converter)
            addDeserializer(converter)
            return this
        }

        /**
         * 构建 JSON Mapper。
         */
        public fun build(): JsonMapper = JsonMapper(serializers, deserializers)
    }
}
