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

    private val serializers = LinkedHashMap<Class<*>, DocumentBaseSerializer<*>>()
    private val deserializers = LinkedHashMap<Class<*>, DocumentBaseDeserializer<*>>()
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
    public fun addSerializer(serializer: DocumentBaseSerializer<*>): DocumentMapperBuilder<M> {
        serializers[serializer.getType().getRawType()] = serializer
        return this
    }

    /**
     * 注册反序列化器。
     *
     * @param deserializer 反序列化器
     * @return 当前构建器
     */
    public fun addDeserializer(deserializer: DocumentBaseDeserializer<*>): DocumentMapperBuilder<M> {
        deserializers[deserializer.getType().getRawType()] = deserializer
        return this
    }

    /**
     * 注册双向转换器。
     *
     * @param converter 转换器
     * @return 当前构建器
     */
    public fun addConverter(converter: DocumentBaseConverter<*>): DocumentMapperBuilder<M> {
        addSerializer(converter)
        addDeserializer(converter)
        return this
    }

    /**
     * 添加单个模块。
     *
     * 模块按 [DocumentModule.moduleName] 去重：同名模块后注册的实例覆盖先注册的实例，
     * 但保持首次注册的顺序。模块在 [build] 时统一执行 `setupModule`。
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
     * 等价于按顺序调用 [addModule]；同名模块后者覆盖前者。与 [addModule] 混用时
     * 按实际调用顺序生效。
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
     * 设置属性元数据解析器。
     *
     * 用于补充 Java 反射不可见的属性注解（例如 Kotlin 无 use-site target 的属性
     * 注解）；模块通过 [ModuleSetupContext.setPropertyMetadataResolver] 注册的解析器
     * 优先于本方法设置的实例。
     *
     * @param resolver 属性元数据解析器；`null` 表示不注册
     * @return 当前构建器
     */
    public fun propertyMetadataResolver(resolver: PropertyMetadataResolver?): DocumentMapperBuilder<M> {
        propertyMetadataResolver = resolver
        return this
    }

    /**
     * 构建格式 Mapper。
     *
     * 注册顺序与覆盖规则（同一 `getType()` 原始类型为 key）：
     *
     * 1. 先收集 Builder 上直接 [addSerializer] / [addDeserializer] / [addConverter]
     *    注册的项；
     * 2. 再按模块注册顺序执行各模块的
     *    [DocumentModule.setupModule]，把 [ModuleSetupContext] 上注册的项覆盖合并到
     *    直接注册结果之上。因此**模块注册优先于直接注册**，后注册的模块优先于先注册
     *    的模块；
     * 3. 模块注册的 [ObjectInstantiator] / [PropertyMetadataResolver] 优先于 Builder
     *    上直接设置的实例，未注册时回退到 Builder 上的实例。
     *
     * 实际使用中应避免对同一类型同时注册序列化器/反序列化器，以免依赖合并顺序。
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
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
    ): M

    /**
     * 由格式模块创建具体 Mapper，并传入字段配置。
     *
     * 默认忽略配置以兼容旧实现；内置格式 Mapper 会重写此方法。
     */
    protected open fun createMapper(
        serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
        deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
        objectInstantiator: ObjectInstantiator?,
        propertyMetadataResolver: PropertyMetadataResolver?,
        config: DocumentMapperConfig,
    ): M = createMapper(serializers, deserializers, objectInstantiator, propertyMetadataResolver)
}
