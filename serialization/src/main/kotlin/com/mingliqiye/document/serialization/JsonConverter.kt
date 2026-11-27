package com.mingliqiye.document.serialization

/**
 * 双向转换器：同时具备序列化和反序列化能力。
 *
 * 字段注解、字段名等上下文通过 [SerializationContext.property] /
 * [DeserializationContext.property] 获取。
 *
 * @param T 转换类型
 */
public interface JsonConverter<T> :
    JsonSerializer<T>,
    JsonDeserializer<T>
