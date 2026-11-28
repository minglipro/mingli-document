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
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * YAML 文档映射器。
 *
 * 与 上游实现 的 `YAMLMapper` 类似：继承通用 [DocumentMapper]，内置 YAML 编解码能力。
 * 外部只使用这个 Mapper，不需要接触内部 Codec SPI。
 *
 * 单文档 API（[parse]、[format]）保持原有语义；多文档读写使用
 * [parseAllToNodes]、[parseAll]、[formatAll]，文档之间以 `---` 分隔。
 *
 * `parse(String)` 默认通过 [YamlTokenReader] 直接消费 Token，不构建 Node 树；
 * 配置了非零 `maxNodeDepth`/`maxNodeCount`/`maxStringLength` 时回退
 * [NodeTokenReader] 路径以保持 Node 树限制语义。两条路径共享 [YamlConfig]
 * 的读取特性与 LoaderOptions（`maxAliases`/`allowCustomTags`/`allowDuplicateKeys`/
 * `codePointLimit`）；写出路径共享 [YamlConfig] 的写出特性与 DumperOptions。
 */
public open class YamlMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "yaml",
    { source -> createReader(source, config).read() },
    { node, pretty -> YamlWriter(YamlWriteSettings.of(config), pretty).write(node) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val yamlConfig: YamlConfig? = config as? YamlConfig
    private val mapperConfig: DocumentMapperConfig = config
    private val writeSettings: YamlWriteSettings = YamlWriteSettings.of(config)
    private val maxAliases: Int = maxAliasesOf(config)
    private val allowCustomTags: Boolean = allowCustomTagsOf(config)
    private val readFeatures: Set<YamlReadFeature> = readFeaturesOf(config)
    private val allowDuplicateKeys: Boolean = allowDuplicateKeysOf(config)
    private val codePointLimit: Int = codePointLimitOf(config)

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

    override fun format(value: Any?, output: OutputStream) {
        val writer = OutputStreamWriter(output, mapperConfig.fileCharset)
        YamlWriter(writeSettings, yamlConfig?.pretty == true).writeTo(toNode(value), writer)
        writer.flush()
        output.flush()
    }

    override fun formatPretty(value: Any?, output: OutputStream) {
        val writer = OutputStreamWriter(output, mapperConfig.fileCharset)
        YamlWriter(writeSettings, true).writeTo(toNode(value), writer)
        writer.flush()
        output.flush()
    }

    /**
     * 解析多文档 YAML，按文档顺序返回全部文档树。
     *
     * 空白或只含注释的文本返回空列表；显式的空文档返回
     * [com.mingliqiye.document.core.NullNode]。未知自定义 tag、读取特性与
     * LoaderOptions 的处理与 [parse] 一致。
     *
     * @param source YAML 文本
     * @return 按文档顺序排列的文档树列表
     */
    public fun parseAllToNodes(source: String): List<Node> = YamlCodec.parseAll(
        source,
        mapperConfig.maxNodeDepth,
        maxAliases,
        allowCustomTags,
        readFeatures,
        allowDuplicateKeys,
        codePointLimit,
    )

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
    public fun formatAll(values: Iterable<Any?>): String = YamlCodec.writeAll(
        values.map(::toNode),
        writeSettings,
        yamlConfig?.pretty == true,
    )

    /**
     * 创建文本 Token Reader。
     *
     * 默认使用原生 [YamlTokenReader]；配置了非零 Node 树限制
     * （`maxNodeDepth`/`maxNodeCount`/`maxStringLength`）时回退
     * [NodeTokenReader]，以保持 Node 路径的限制与行为。两条路径都使用
     * [YamlConfig] 的读取特性、LoaderOptions 与 [YamlConfig.allowCustomTags]。
     *
     * @param source YAML 文本
     * @return Token Reader
     */
    override fun createTextTokenReader(source: String): DocumentTokenReader {
        if (mapperConfig.maxNodeDepth > 0 || mapperConfig.maxNodeCount > 0 || mapperConfig.maxStringLength > 0) {
            val node = createReader(source, mapperConfig).read()
            validateNodeLimits(node)
            return NodeTokenReader(node)
        }
        return YamlTokenReader(
            source,
            mapperConfig.maxNodeDepth,
            maxAliases,
            mapperConfig.maxStringLength,
            allowCustomTags,
            readFeatures,
            allowDuplicateKeys,
            codePointLimit,
        )
    }

    public companion object {

        /**
         * 创建 YAML Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): YamlMapperBuilder = YamlMapperBuilder()

        /**
         * 按配置创建单文档读取器，集中 Node 路径参数，避免与 Token 路径漂移。
         */
        private fun createReader(source: String, config: DocumentMapperConfig): YamlReader = YamlReader(
            source,
            config.maxNodeDepth,
            maxAliasesOf(config),
            allowCustomTagsOf(config),
            readFeaturesOf(config),
            allowDuplicateKeysOf(config),
            codePointLimitOf(config),
        )

        private fun maxAliasesOf(config: DocumentMapperConfig): Int = (config as? YamlConfig)?.maxAliases ?: 0

        private fun allowCustomTagsOf(config: DocumentMapperConfig): Boolean = (config as? YamlConfig)?.allowCustomTags ?: false

        private fun readFeaturesOf(config: DocumentMapperConfig): Set<YamlReadFeature> = (config as? YamlConfig)?.readFeatures ?: YamlConfig.DEFAULT_READ_FEATURES

        private fun allowDuplicateKeysOf(config: DocumentMapperConfig): Boolean = (config as? YamlConfig)?.allowDuplicateKeys ?: false

        private fun codePointLimitOf(config: DocumentMapperConfig): Int = (config as? YamlConfig)?.codePointLimit ?: 0
    }
}
