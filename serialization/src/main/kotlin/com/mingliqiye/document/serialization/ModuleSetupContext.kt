package com.mingliqiye.document.serialization

/**
 * 模块配置上下文。
 */
public interface ModuleSetupContext {

    /**
     * 注册序列化器。
     */
    public fun addSerializer(serializer: JsonSerializer<*>)

    /**
     * 注册反序列化器。
     */
    public fun addDeserializer(deserializer: JsonDeserializer<*>)

    /**
     * 注册双向转换器。
     */
    public fun addConverter(converter: JsonConverter<*>)

    /**
     * 设置对象实例化策略。
     */
    public fun setObjectInstantiator(instantiator: ObjectInstantiator?)
}
