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
 * 平铺 key 使用 `.` 表示对象路径，`[index]` 表示数组下标。
 *
 * 解析深度限制：默认安全上限为 [DEFAULT_MAX_DEPTH]（512，根对象深度为 `1`），
 * 可通过 [builder] 的 `maxDepth`、`PropertiesMapper(config, maxDepth)` 或通用
 * [DocumentMapperConfig.maxNodeDepth] 调整。路径深度超限时 Tree/Token 路径抛
 * [com.mingliqiye.document.format.properties.exception.PropertiesParseException]
 * （通用 `maxNodeDepth` 仍按 Node 限制语义抛
 * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]）。
 */
public open class PropertiesMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) : DocumentMapper(
    "properties",
    { source -> PropertiesReader(source, maxDepth).read() },
    { node, _ -> PropertiesWriter().write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    init {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
    }

    private val mapperConfig: DocumentMapperConfig = config

    /**
     * 创建默认 Properties 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用配置创建映射器。
     *
     * 解析深度使用默认值 [DEFAULT_MAX_DEPTH]。
     *
     * @param config Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用通用配置与显式解析深度创建映射器。
     *
     * @param config Mapper 配置
     * @param maxDepth 解析最大嵌套深度（根对象深度为 `1`），必须为正数
     */
    public constructor(config: DocumentMapperConfig, maxDepth: Int) :
        this(emptyMap(), emptyMap(), null, null, config, maxDepth)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    /**
     * 创建 Properties 原生 Token 读取器。
     *
     * 使用 [PropertiesTokenReader] 直接从平铺 key/value 生成 Token 流，不再先构建
     * 完整 Node 树；maxNodeDepth/maxNodeCount/maxStringLength 在生成 Token 时校验，
     * 语义与 Node 路径一致（超限抛
     * [com.mingliqiye.document.serialization.exception.DocumentSerializationException]）。
     * 解析深度 [maxDepth] 同时生效，超限抛
     * [com.mingliqiye.document.format.properties.exception.PropertiesParseException]。
     */
    override fun createTextTokenReader(source: String): DocumentTokenReader = PropertiesTokenReader(
        source,
        mapperConfig.maxNodeDepth,
        mapperConfig.maxNodeCount,
        mapperConfig.maxStringLength,
        maxDepth,
    )

    public companion object {

        /**
         * 默认解析最大嵌套深度（根对象深度为 `1`）。
         */
        public const val DEFAULT_MAX_DEPTH: Int = 512

        /**
         * 创建 Properties Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): PropertiesMapperBuilder = PropertiesMapperBuilder()
    }
}
