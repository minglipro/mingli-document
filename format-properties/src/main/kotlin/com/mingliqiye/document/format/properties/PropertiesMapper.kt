package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * Java Properties 文档映射器。
 *
 * 平铺 key 使用 [PropertiesConfig] 定义的路径规则还原对象/数组。默认行为对齐
 * 上游实现 `Properties 底层库` 的 `JavaPropsSchema`：
 * - 路径分隔符 `.`，支持自定义/多字符分隔符与转义字符；
 * - 数组下标标记 `[index]` 与简单数字下标（`tags.1`）都可读取；
 * - 写出默认使用简单下标（`firstArrayOffset` 起），可切换标记写法；
 * - 支持前缀、键值分隔符、行结尾、缩进、头部注释；
 * - 本模块扩展：字段名百分号编码（默认开启，可通过特性关闭）。
 *
 * 解析深度限制：默认安全上限为 [DEFAULT_MAX_DEPTH]（512，根对象深度为 `1`），
 * 可通过 [PropertiesConfig.maxDepth]、[builder] 的 `maxDepth` 或
 * `PropertiesMapper(config, maxDepth)` 调整。路径深度超限时 Tree/Token 路径抛
 * [com.mingliqiye.document.format.properties.exception.PropertiesParseException]
 * （通用 `maxNodeDepth` 仍按 Node 限制语义抛
 * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]）。
 *
 * 说明：上游实现 2.15 的 Properties 模块没有 `PropertiesReadFeature` /
 * `PropertiesWriteFeature` 枚举，本模块提供同风格特性集合作为统一开关，映射关系见
 * [PropertiesReadFeature] / [PropertiesWriteFeature]。
 */
public open class PropertiesMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    propertiesConfig: PropertiesConfig,
) : DocumentMapper(
    "properties",
    { source -> PropertiesReader(source, propertiesConfig).read() },
    { node, _ -> PropertiesWriter(propertiesConfig).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    propertiesConfig,
) {

    private val config: PropertiesConfig = propertiesConfig

    /**
     * 创建默认 Properties 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, PropertiesConfig.empty())

    /**
     * 使用 Properties 配置创建映射器。
     *
     * @param config Properties 配置
     */
    public constructor(config: PropertiesConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用通用配置创建映射器。
     *
     * 保留通用配置（字段策略、文件选项、Node 限制、命名策略等），格式选项使用
     * [PropertiesConfig] 默认值，解析深度使用默认 [DEFAULT_MAX_DEPTH]。
     *
     * @param config Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) :
        this(emptyMap(), emptyMap(), null, null, PropertiesConfig.from(config))

    /**
     * 使用通用配置与显式解析深度创建映射器。
     *
     * @param config Mapper 配置
     * @param maxDepth 解析最大嵌套深度（根对象深度为 `1`），必须为正数
     */
    public constructor(config: DocumentMapperConfig, maxDepth: Int) :
        this(emptyMap(), emptyMap(), null, null, PropertiesConfig.from(config, maxDepth))

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, PropertiesConfig.empty())

    /**
     * 创建 Properties 原生 Token 读取器。
     *
     * 使用 [PropertiesTokenReader] 直接从平铺 key/value 生成 Token 流，不再先构建
     * 完整 Node 树；解析语义与 [PropertiesReader] 一致（同一轻量路径树）。
     * maxNodeDepth/maxNodeCount/maxStringLength 在生成 Token 时校验，语义与 Node 路径
     * 一致（超限抛
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]）；
     * 解析深度 [PropertiesConfig.maxDepth] 同时生效，超限抛
     * [com.mingliqiye.document.format.properties.exception.PropertiesParseException]。
     */
    override fun createTextTokenReader(source: String): DocumentTokenReader = PropertiesTokenReader(
        source,
        config,
        config.maxNodeDepth,
        config.maxNodeCount,
        config.maxStringLength,
    )

    public companion object {

        /**
         * 默认解析最大嵌套深度（根对象深度为 `1`），等价
         * [PropertiesConfig.DEFAULT_MAX_DEPTH]。
         */
        public const val DEFAULT_MAX_DEPTH: Int = PropertiesConfig.DEFAULT_MAX_DEPTH

        /**
         * 创建 Properties Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): PropertiesMapperBuilder = PropertiesMapperBuilder()
    }
}
