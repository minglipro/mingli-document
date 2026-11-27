package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.codec.DocumentCodec
import java.util.LinkedHashMap

/**
 * [DocumentMapperApi] 构建器接口。
 */
public interface DocumentMapperBuilder {

    /**
     * 设置文本格式编解码器。
     *
     * @param codec 编解码器
     * @return 当前构建器
     */
    public fun codec(codec: DocumentCodec?): DocumentMapperBuilder

    /**
     * 注册序列化器。
     *
     * @param serializer 序列化器
     * @return 当前构建器
     */
    public fun addSerializer(serializer: JsonSerializer<*>): DocumentMapperBuilder

    /**
     * 注册反序列化器。
     *
     * @param deserializer 反序列化器
     * @return 当前构建器
     */
    public fun addDeserializer(deserializer: JsonDeserializer<*>): DocumentMapperBuilder

    /**
     * 注册双向转换器。
     *
     * @param converter 转换器
     * @return 当前构建器
     */
    public fun addConverter(converter: JsonConverter<*>): DocumentMapperBuilder

    /**
     * 构建 Mapper。
     */
    public fun build(): DocumentMapperApi
}

/**
 * [DocumentMapperBuilder] 默认实现。
 */
internal class DefaultDocumentMapperBuilder : DocumentMapperBuilder {

    private var codec: DocumentCodec? = null
    private val serializers = LinkedHashMap<Class<*>, JsonSerializer<*>>()
    private val deserializers = LinkedHashMap<Class<*>, JsonDeserializer<*>>()

    override fun codec(codec: DocumentCodec?): DocumentMapperBuilder {
        this.codec = codec
        return this
    }

    override fun addSerializer(serializer: JsonSerializer<*>): DocumentMapperBuilder {
        serializers[serializer.getType().getRawType()] = serializer
        return this
    }

    override fun addDeserializer(deserializer: JsonDeserializer<*>): DocumentMapperBuilder {
        deserializers[deserializer.getType().getRawType()] = deserializer
        return this
    }

    override fun addConverter(converter: JsonConverter<*>): DocumentMapperBuilder {
        addSerializer(converter)
        addDeserializer(converter)
        return this
    }

    override fun build(): DocumentMapperApi = DefaultDocumentMapper(codec, serializers, deserializers)
}
