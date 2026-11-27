package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.DocumentBaseDeserializer
import com.mingliqiye.document.serialization.DocumentBaseSerializer
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * YAML 文档映射器。
 *
 * 与 Jackson 的 `YAMLMapper` 类似：继承通用 [DocumentMapper]，内置 YAML 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部 Codec SPI。
 *
 * 单文档 API（[parse]、[format]）保持原有语义；多文档读写使用
 * [parseAllToNodes]、[parseAll]、[formatAll]，文档之间以 `---` 分隔。
 */
public open class YamlMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "yaml",
    { source -> YamlReader(source, config.maxNodeDepth).read() },
    { node, pretty -> YamlWriter(pretty).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val yamlConfig: YamlConfig? = config as? YamlConfig
    private val maxNodeDepth: Int = config.maxNodeDepth

    /**
     * 创建默认 YAML 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用字段配置创建 YAML 映射器。
     *
     * @param config Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    override fun format(value: Any?): String = if (yamlConfig?.pretty == true) {
        super.formatPretty(value)
    } else {
        super.format(value)
    }

    /**
     * 解析多文档 YAML，按文档顺序返回全部文档树。
     *
     * 空白或只含注释的文本返回空列表；显式的空文档返回
     * [com.mingliqiye.document.core.NullNode]。
     *
     * @param source YAML 文本
     * @return 按文档顺序排列的文档树列表
     */
    public fun parseAllToNodes(source: String): List<Node> = YamlCodec.parseAll(source)

    /**
     * 解析多文档 YAML，按文档顺序反序列化为指定类型。
     *
     * @param source YAML 文本
     * @param targetType 每个文档的目标类型
     * @return 按文档顺序反序列化得到的实体列表
     */
    public fun <T> parseAll(
        source: String,
        targetType: Class<T>,
    ): List<T> = parseAllToNodes(source).map { fromNode(it, targetType) }

    /**
     * 解析多文档 YAML，按文档顺序反序列化为指定泛型类型。
     *
     * @param source YAML 文本
     * @param targetType 每个文档的目标类型引用
     * @return 按文档顺序反序列化得到的实体列表
     */
    public fun <T> parseAll(
        source: String,
        targetType: DocumentTypeReference<T>,
    ): List<T> = parseAllToNodes(source).map { fromNode(it, targetType) }

    /**
     * 将多个实体写成 `---` 分隔的 YAML 多文档文本。
     *
     * 文档内容经 [toNode] 转换为文档树后写出；空 [Iterable] 返回空字符串。
     * 输出默认使用 [YamlConfig.pretty] 指定风格，与 [format] 保持一致。
     *
     * @param values 要写出的实体、Map、集合或文档树
     * @return YAML 多文档文本
     */
    public fun formatAll(values: Iterable<Any?>): String = YamlCodec.writeAll(values.map(::toNode), yamlConfig?.pretty == true)

    override fun createTextTokenReader(source: String): DocumentTokenReader = NodeTokenReader(YamlReader(source, maxNodeDepth).read())

    public companion object {

        /**
         * 创建 YAML Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): YamlMapperBuilder = YamlMapperBuilder()
    }
}
