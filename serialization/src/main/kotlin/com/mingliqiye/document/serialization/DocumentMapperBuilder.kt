package com.mingliqiye.document.serialization

import java.util.LinkedHashMap

/**
 * 格式 Mapper 构建器基类。
 *
 * 负责收集序列化器、反序列化器、转换器和模块配置；具体格式 Mapper 通过
 * [createMapper] 创建实例。
 *
 * @param M Mapper 类型
 */
public abstract class DocumentMapperBuilder<M : DocumentMapper> {

    private val serializers = LinkedHashMap<Class<*>, JsonSerializer<*>>()
    private val deserializers = LinkedHashMap<Class<*>, JsonDeserializer<*>>()
    private val modules = LinkedHashMap<String, DocumentModule>()
    private var objectInstantiator: ObjectInstantiator? = null
    private var propertyMetadataResolver: PropertyMetadataResolver? = null
    private var config: DocumentMapperConfig = DocumentMapperConfig.empty()

    /**
     * 注册序列化器。
     *
     * @param serializer 序列化器
     * @return 当前构建器
     */
    public fun addSerializer(serializer: JsonSerializer<*>): DocumentMapperBuilder<M> {
        serializers[serializer.getType().getRawType()] = serializer
        return this
    }

    /**
     * 注册反序列化器。
     *
     * @param deserializer 反序列化器
     * @return 当前构建器
     */
    public fun addDeserializer(deserializer: JsonDeserializer<*>): DocumentMapperBuilder<M> {
        deserializers[deserializer.getType().getRawType()] = deserializer
        return this
    }

    /**
     * 注册双向转换器。
     *
     * @param converter 转换器
     * @return 当前构建器
     */
    public fun addConverter(converter: JsonConverter<*>): DocumentMapperBuilder<M> {
        addSerializer(converter)
        addDeserializer(converter)
        return this
    }

    /**
     * 添加单个模块。
     *
     * @param module 文档模块
     * @return 当前构建器
     */
    public fun addModule(module: DocumentModule): DocumentMapperBuilder<M> {
        modules[module.moduleName] = module
        return this
    }

    /**
     * 添加多个模块。
     *
     * @param modules 文档模块
     * @return 当前构建器
     */
    public fun addModules(vararg modules: DocumentModule): DocumentMapperBuilder<M> {
        for (module in modules) {
            addModule(module)
        }
        return this
    }

    /**
     * 设置 Mapper 配置。
     *
     * @param config Mapper 配置
     * @return 当前构建器
     */
    public fun config(config: DocumentMapperConfig): DocumentMapperBuilder<M> {
        this.config = config
        return this
    }

    /**
     * 设置对象实例化策略。
     *
     * @param instantiator 实例化策略
     * @return 当前构建器
     */
    public fun objectInstantiator(instantiator: ObjectInstantiator?): DocumentMapperBuilder<M> {
        objectInstantiator = instantiator
        return this
    }

    /**
     * 构建格式 Mapper。
     */
    public fun build(): M {
        val moduleContext = DefaultModuleSetupContext()
        for (module in modules.values) {
            module.setupModule(moduleContext)
        }

        val allSerializers = LinkedHashMap(serializers)
        allSerializers.putAll(moduleContext.serializers())
        val allDeserializers = LinkedHashMap(deserializers)
        allDeserializers.putAll(moduleContext.deserializers())

        return createMapper(
            allSerializers,
            allDeserializers,
            moduleContext.objectInstantiator() ?: objectInstantiator,
            moduleContext.propertyMetadataResolver() ?: propertyMetadataResolver,
            config,
        )
    }

    /**
     * 由格式模块创建具体 Mapper。
     */
    protected abstract fun createMapper(
        serializers: Map<Class<*>, JsonSerializer<*>>,
        deserializers: Map<Class<*>, JsonDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): M

    /**
     * 由格式模块创建具体 Mapper，并传入字段配置。
     *
     * 默认忽略配置以兼容旧实现；内置格式 Mapper 会重写此方法。
     */
    protected open fun createMapper(
        serializers: Map<Class<*>, JsonSerializer<*>>,
        deserializers: Map<Class<*>, JsonDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
        config: DocumentMapperConfig,
    ): M = createMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver)
}
