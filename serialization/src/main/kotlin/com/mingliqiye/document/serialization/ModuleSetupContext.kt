package com.mingliqiye.document.serialization

/**
 * 模块配置上下文。
 *
 * 模块通过该上下文注册序列化器/反序列化器/转换器与实例化策略；注册项由
 * [DocumentMapperBuilder.build] 在模块 setup 阶段收集，并覆盖合并到 Builder 上
 * 直接注册的项（模块注册优先）。
 */
public interface ModuleSetupContext {

    /**
     * 注册序列化器。
     *
     * 相同 `getType()` 原始类型的序列化器后注册者覆盖先注册者。
     *
     * @param serializer 序列化器
     */
    public fun addSerializer(serializer: DocumentBaseSerializer<*>)

    /**
     * 注册反序列化器。
     *
     * 相同 `getType()` 原始类型的反序列化器后注册者覆盖先注册者。
     *
     * @param deserializer 反序列化器
     */
    public fun addDeserializer(deserializer: DocumentBaseDeserializer<*>)

    /**
     * 注册双向转换器。
     *
     * 等价于同时调用 [addSerializer] 与 [addDeserializer]，分别进入序列化器/
     * 反序列化器注册表。
     *
     * @param converter 双向转换器
     */
    public fun addConverter(converter: DocumentBaseConverter<*>)

    /**
     * 设置对象实例化策略。
     *
     * 优先于 [DocumentMapperBuilder.objectInstantiator]。
     *
     * @param instantiator 实例化策略；`null` 表示回退到 Builder 配置
     */
    public fun setObjectInstantiator(instantiator: ObjectInstantiator?)

    /**
     * 设置属性元数据解析器。
     *
     * 优先于 [DocumentMapperBuilder.propertyMetadataResolver]。
     *
     * @param resolver 属性元数据解析器；`null` 表示回退到 Builder 配置
     */
    public fun setPropertyMetadataResolver(resolver: PropertyMetadataResolver?)
}
