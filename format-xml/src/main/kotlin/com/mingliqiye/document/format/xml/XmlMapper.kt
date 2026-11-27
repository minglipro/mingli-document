package com.mingliqiye.document.format.xml

import com.mingliqiye.document.annotation.DocumentRootName
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * XML 文档映射器。
 *
 * 使用 [DocumentMapper] 的通用 Node 映射能力，并通过 `type` / `nil` 属性保存 XML
 * 无法原生表达的类型信息。
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
 * - 混合内容按 [XmlMixedContentPolicy] 保留到 `#text` 字段或拒绝。
 */
public open class XmlMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "xml",
    { source -> XmlReader.from(source, config as? XmlConfig).read() },
    { node, pretty -> XmlWriter.from(config as? XmlConfig).write(node, pretty) },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    private val xmlConfig: XmlConfig? = config as? XmlConfig

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

    private fun writeXml(value: Any?, pretty: Boolean): String {
        val rootName = value?.javaClass?.getAnnotation(DocumentRootName::class.java)?.value
        return XmlWriter.from(xmlConfig, rootName).write(toNode(value), pretty)
    }

    override fun createTextTokenReader(source: String): DocumentTokenReader = NodeTokenReader(XmlReader.from(source, xmlConfig).read())

    public companion object {

        /**
         * 创建 XML Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): XmlMapperBuilder = XmlMapperBuilder()
    }
}
