package com.mingliqiye.document.format.xml

import com.mingliqiye.document.annotation.DocumentRootName
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
 * XML 文档映射器。
 *
 * 使用 [DocumentMapper] 的通用 Node 映射能力；`nil` 属性默认保留，`type` 协议默认
 * 不输出，可通过 [XmlConfig.typeAttributes] 开启以严格保存 XML 无法原生表达的类型信息。
 *
 * 通过 [XmlConfig] 可额外启用属性映射与命名空间感知：
 * - 属性映射：`@DocumentName("@id")` 的字段写成 XML attribute `id="..."`，
 *   读取时 XML attribute 映射回 `@id` 字段；
 * - 带属性的标量/数组元素包装为 `{ 属性字段..., "#text": 标量 }` /
 *   `{ 属性字段..., "#items": 数组 }` 对象，Bean 侧可用 `@DocumentName("#text")` 等字段接收；
 * - 命名空间前缀模式：字段名可使用 `prefix:local` 形式（如 `ns:name`、`@ns:id`），
 *   `xmlns` 声明通过 `@xmlns:ns` 属性字段保留；
 * - Clark 模式（[XmlNamespaceMode.CLARK]）：字段名使用 `{uri}local`，写出时自动分配
 *   前缀并生成 `xmlns` 声明；
 * - 混合内容按 [XmlMixedContentPolicy] 处理：默认 `PRESERVE` 保留到 `#text`；
 *   `PRESERVE_ORDERED` 额外输出 `#content` 有序数组（文本项 `StringNode`、
 *   子元素项单字段 `ObjectNode`），写出时按该数组顺序交错输出文本与子元素；`REJECT` 抛异常。
 *
 * `parse(String)` 默认通过原生 [XmlTokenReader] 直接消费 Token，不构建 Node 树；
 * 非默认读取配置（属性映射、命名空间、自定义键名、有序/拒绝混合内容）或配置了
 * Node 树限制/`resolveReferences` 时回退 [NodeTokenReader] 路径。
 *
 * 解析深度限制：默认安全上限为 [XmlConfig.DEFAULT_MAX_DEPTH]（512，根元素深度为 `1`），
 * 可用 [XmlConfig.maxDepth] 或通用 `DocumentMapperConfig.maxNodeDepth` 调整；两者同时
 * 设置时取较小值。Tree/Token/Bean 三条解析路径都会在超限时抛出
 * [com.mingliqiye.document.format.xml.exception.XmlParseException]。
 */
public open class XmlMapper internal constructor(
    serializers: Map<Class<*>, DocumentBaseSerializer<*>>,
    deserializers: Map<Class<*>, DocumentBaseDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "xml",
    { source -> XmlReader.from(source, config as? XmlConfig, resolveMaxDepth(config)).read() },
    { node, pretty -> XmlWriter.from(config as? XmlConfig).write(node, pretty) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val xmlConfig: XmlConfig? = config as? XmlConfig
    private val mapperConfig: DocumentMapperConfig = config
    private val maxDepth: Int = resolveMaxDepth(config)

    /**
     * 创建默认 XML 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用 XML 配置创建映射器。
     *
     * @param config XML 配置
     */
    public constructor(config: XmlConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用通用字段配置创建映射器。
     *
     * @param config 通用 Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    override fun format(value: Any?): String = writeXml(value, pretty = false)

    override fun formatPretty(value: Any?): String = writeXml(value, pretty = true)

    override fun format(value: Any?, output: OutputStream) {
        val writer = OutputStreamWriter(output, mapperConfig.fileCharset)
        writeXmlTo(value, pretty = false, writer)
        writer.flush()
        output.flush()
    }

    override fun formatPretty(value: Any?, output: OutputStream) {
        val writer = OutputStreamWriter(output, mapperConfig.fileCharset)
        writeXmlTo(value, pretty = true, writer)
        writer.flush()
        output.flush()
    }

    private fun writeXml(value: Any?, pretty: Boolean): String {
        val rootName = value?.javaClass?.getAnnotation(DocumentRootName::class.java)?.value
        return XmlWriter.from(xmlConfig, rootName).write(toNode(value), pretty)
    }

    private fun writeXmlTo(value: Any?, pretty: Boolean, out: Appendable) {
        val rootName = value?.javaClass?.getAnnotation(DocumentRootName::class.java)?.value
        XmlWriter.from(xmlConfig, rootName).writeTo(toNode(value), pretty, out)
    }

    /**
     * 创建 XML 文本 Token Reader。
     *
     * 默认读取配置（[XmlConfig] 为 `null` 或 [XmlConfig.usesDefaultReadConfiguration]）且
     * 未配置 Node 树限制/`resolveReferences` 时使用原生 [XmlTokenReader]，直接从 DOM
     * 生成 Token，不构建 Node 树；其他配置回退 [NodeTokenReader]（先经 [XmlReader]
     * 构建 Node 并执行 [validateNodeLimits]），保持属性映射、命名空间、有序混合内容等语义。
     *
     * @param source XML 文本
     * @return Token Reader
     */
    override fun createTextTokenReader(source: String): DocumentTokenReader {
        if (usesNativeTokenReader()) {
            return XmlTokenReader(source, maxDepth)
        }
        val node = XmlReader.from(source, xmlConfig, maxDepth).read()
        validateNodeLimits(node)
        return NodeTokenReader(node)
    }

    /**
     * 是否可使用原生 Token 读取器。
     *
     * Node 树限制与 `resolveReferences` 只能在 Node 路径完整实现，因此配置后回退。
     */
    private fun usesNativeTokenReader(): Boolean {
        if (mapperConfig.maxNodeDepth > 0 ||
            mapperConfig.maxNodeCount > 0 ||
            mapperConfig.maxStringLength > 0 ||
            mapperConfig.resolveReferences
        ) {
            return false
        }
        return xmlConfig?.usesDefaultReadConfiguration() ?: true
    }

    public companion object {

        /**
         * 创建 XML Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): XmlMapperBuilder = XmlMapperBuilder()
    }
}

/**
 * 计算 XML 解析最大嵌套深度。
 *
 * 取 [XmlConfig.maxDepth]（配置了 [XmlConfig] 时）与通用
 * [DocumentMapperConfig.maxNodeDepth]（大于 `0` 时）的较小正值；都未设置时使用
 * [XmlConfig.DEFAULT_MAX_DEPTH]。两者都是“上限”，较小者优先，保证任一显式配置都能
 * 收紧限制。
 */
private fun resolveMaxDepth(config: DocumentMapperConfig): Int {
    val xmlConfig = config as? XmlConfig
    val candidates = ArrayList<Int>(2)
    if (xmlConfig != null) {
        candidates.add(xmlConfig.maxDepth)
    }
    if (config.maxNodeDepth > 0) {
        candidates.add(config.maxNodeDepth)
    }
    return candidates.minOrNull() ?: XmlConfig.DEFAULT_MAX_DEPTH
}
