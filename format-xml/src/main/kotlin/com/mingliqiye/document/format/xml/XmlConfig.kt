package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * XML Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 XML 根元素名、属性映射、命名空间、混合内容、
 * 上游实现 XML 风格读/写特性与名称处理选项。
 *
 * 属性使用“前缀 + 名称”的普通对象字段表示（例如 `@id`），不引入新的节点类型；
 * 带属性的标量元素用 `{ 属性字段..., textKey: 标量 }` 包装对象表示，
 * 带属性的数组元素用 `{ 属性字段..., itemsKey: 数组 }` 包装对象表示。
 * 命名空间默认使用“前缀限定名 + `xmlns` 属性字段”表示，也可切换为 Clark 名称模式。
 * 混合内容默认只保留 `textKey` 文本；[XmlMixedContentPolicy.PRESERVE_ORDERED] 时
 * 额外输出 `contentKey` 有序数组以保留文本/子元素相对顺序。
 *
 * 读/写特性默认值对齐 上游实现 XML 3.x，可用 [XmlConfigBuilder.上游库2Defaults] 切到
 * 上游实现 2.x 默认；两者差异项见 [XmlReadFeature]、[XmlWriteFeature] 各项说明。
 */
public class XmlConfig internal constructor(
    base: DocumentMapperConfig,
    /** 写出时的根元素名。 */
    public val rootName: String,
    /**
     * 属性字段前缀；`null` 表示不启用属性映射。
     *
     * 启用后读取时 XML attribute 映射为对象字段 `attributePrefix + localName`，
     * 写出时对象中带该前缀的标量字段写成 XML attribute。
     * Bean 字段名由 `@DocumentName` 决定，因此注解中的前缀需要与本配置一致。
     */
    public val attributePrefix: String?,
    /**
     * 是否启用命名空间感知读取/写出（前缀限定名模式）。
     *
     * 开启后读取保留前缀限定名与 `xmlns` 属性字段，写出允许 `prefix:local` 形式的
     * 元素/属性名。默认关闭，保持原有 `type` / `nil` 协议行为。
     */
    public val namespaceAware: Boolean,
    /** 命名空间名称表示模式；[XmlNamespaceMode.CLARK] 会强制启用命名空间感知。 */
    public val namespaceMode: XmlNamespaceMode,
    /**
     * 混合内容中直接文本的字段名；同时用于标量元素属性包装对象的文本字段。
     *
     * 默认 `#text`。该键在写出时保留给包装结构，不作为普通子元素名。
     */
    public val textKey: String,
    /**
     * 带属性数组元素的数组内容字段名；默认 `#items`。
     *
     * 该键在写出时保留给包装结构，不作为普通子元素名。
     */
    public val itemsKey: String,
    /**
     * 有序混合内容的数组字段名；默认 `#content`。
     *
     * [XmlMixedContentPolicy.PRESERVE_ORDERED] 读取时输出：文本项为 `StringNode`，
     * 子元素项为单字段对象 `{元素名: 节点}`；写出时该字段存在则按其顺序交错输出。
     * 该键在写出时保留给混合内容结构，不作为普通子元素名。
     */
    public val contentKey: String,
    /** 混合内容处理策略；默认 [XmlMixedContentPolicy.PRESERVE]。 */
    public val mixedContentPolicy: XmlMixedContentPolicy,
    /**
     * 解析时允许的最大 XML 元素嵌套深度（根元素深度为 `1`），默认 [DEFAULT_MAX_DEPTH]。
     *
     * 超限时 Node 路径与 Token 路径都抛出
     * [com.mingliqiye.document.format.xml.exception.XmlParseException]；
     * 与 [DocumentMapperConfig.maxNodeDepth] 同时设置时取两者较小值。
     */
    public val maxDepth: Int = DEFAULT_MAX_DEPTH,
    /**
     * 是否在写出时生成 `type="..."` 协议属性，默认 `false`。
     *
     * 默认输出更接近普通业务的 XML（null 写成 `xsi:nil="true"`），读取侧按
     * 元素结构或标量文本推断类型：
     * - 数字/布尔文本会还原为 `NumberNode` / `BooleanNode`；
     * - 看起来像数字的字符串、二进制等无法仅靠文本区分的类型会按推断结果处理，
     *   需要严格保真时开启该选项。
     */
    public val typeAttributes: Boolean = false,
    /**
     * XML 读取特性，默认 [DEFAULT_READ_FEATURES]（对齐 上游实现 XML 3.x）。
     *
     * 对齐 上游实现 XML 的 `XmlReadFeature`。
     */
    public val readFeatures: Set<XmlReadFeature> = DEFAULT_READ_FEATURES,
    /**
     * XML 写出特性，默认 [DEFAULT_WRITE_FEATURES]（对齐 上游实现 XML 3.x）。
     *
     * 对齐 上游实现 XML 的 `XmlWriteFeature`。
     */
    public val writeFeatures: Set<XmlWriteFeature> = DEFAULT_WRITE_FEATURES,
    /**
     * 集合/数组字段写出时是否默认使用同名的包装元素，默认 `true`（上游实现 默认）。
     *
     * - `true`：`tags` 字段写成 `<tags type="array"?><item>...</item></tags>`；
     * - `false`：写成无包装的重复元素 `<tags>a</tags><tags>b</tags>`（空数组不写出）。
     *
     * 读取端两种形式都接受（同名重复元素会合并为 `ArrayNode`）；无包装形式下
     * 单元素数组与标量在 XML 上不可区分，需要严格往返时保持默认 `true`。
     */
    public val defaultUseWrapper: Boolean = true,
    /**
     * XML 名称处理器，默认 [XmlNameProcessors.passthrough]。
     *
     * 写出时对元素/属性本地名调用 [XmlNameProcessor.encode]，读取时调用
     * [XmlNameProcessor.decode]；之后仍会应用本库的 `_xHHHH_` 非法字符转义。
     */
    public val nameProcessor: XmlNameProcessor = XmlNameProcessors.passthrough(),
) : DocumentMapperConfig(base) {

    init {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        require(textKey.isNotEmpty()) { "textKey must not be empty" }
        require(itemsKey.isNotEmpty()) { "itemsKey must not be empty" }
        require(contentKey.isNotEmpty()) { "contentKey must not be empty" }
    }

    /** 是否启用属性映射，等价于 [attributePrefix] 不为 `null`。 */
    public val attributeMode: Boolean
        get() = attributePrefix != null

    /** 是否使用 Clark 名称模式。 */
    public val clarkNames: Boolean
        get() = namespaceMode == XmlNamespaceMode.CLARK

    /**
     * 是否使用默认读取语义，可走原生 Token 读取器（[XmlTokenReader]）。
     *
     * 只检查影响读取结果的 XML 选项：无属性映射、无命名空间感知、默认名称模式、
     * 默认 `textKey`/`itemsKey`/`contentKey`、[XmlMixedContentPolicy.PRESERVE] 混合内容、
     * 默认读取特性与名称处理器。`rootName`/`defaultUseWrapper` 只影响写出，不影响判断；
     * Node 树限制与非默认通用配置由 Mapper 另行判断回退。
     */
    internal fun usesDefaultReadConfiguration(): Boolean = attributePrefix == null &&
        !namespaceAware &&
        namespaceMode == XmlNamespaceMode.PREFIX &&
        textKey == DEFAULT_TEXT_KEY &&
        itemsKey == DEFAULT_ITEMS_KEY &&
        contentKey == DEFAULT_CONTENT_KEY &&
        mixedContentPolicy == XmlMixedContentPolicy.PRESERVE &&
        readFeatures == DEFAULT_READ_FEATURES &&
        nameProcessor === XmlNameProcessors.passthrough()

    /** 是否启用空元素读取为 null。 */
    public val emptyElementAsNull: Boolean
        get() = XmlReadFeature.EMPTY_ELEMENT_AS_NULL in readFeatures

    /** 是否处理 `xsi:nil`。 */
    public val processXsiNil: Boolean
        get() = XmlReadFeature.PROCESS_XSI_NIL in readFeatures

    /** 是否按 `xsi:type` 自动识别类型。 */
    public val autoDetectXsiType: Boolean
        get() = XmlReadFeature.AUTO_DETECT_XSI_TYPE in readFeatures

    /** 是否把 null 写成 `xsi:nil`。 */
    public val writeNullsAsXsiNil: Boolean
        get() = XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL in writeFeatures

    /** 是否写出 XML 声明。 */
    public val writeXmlDeclaration: Boolean
        get() = XmlWriteFeature.WRITE_XML_DECLARATION in writeFeatures

    /** 是否使用 XML 1.1 声明。 */
    public val writeXml11: Boolean
        get() = XmlWriteFeature.WRITE_XML_1_1 in writeFeatures

    /** 是否在 XML 声明中加入 `standalone="yes"`。 */
    public val writeStandaloneYesToXmlDeclaration: Boolean
        get() = XmlWriteFeature.WRITE_STANDALONE_YES_TO_XML_DECLARATION in writeFeatures

    /** 是否展开单字段根对象节点。 */
    public val unwrapRootObjectNode: Boolean
        get() = XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE in writeFeatures

    /** 是否按需写出 `xsi:type`。 */
    public val writeAutoDetectXsiType: Boolean
        get() = XmlWriteFeature.AUTO_DETECT_XSI_TYPE in writeFeatures

    /** 是否按 XML Schema 规则写出浮点特殊值。 */
    public val writeSchemaConformingFloats: Boolean
        get() = XmlWriteFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS in writeFeatures

    public companion object {

        /** null/空元素读取默认特性（对齐 上游实现 XML 3.x）。 */
        public val DEFAULT_READ_FEATURES: Set<XmlReadFeature> = setOf(
            XmlReadFeature.AUTO_DETECT_XSI_TYPE,
            XmlReadFeature.PROCESS_XSI_NIL,
        )

        /** null 写出默认特性（对齐 上游实现 XML 3.x）。 */
        public val DEFAULT_WRITE_FEATURES: Set<XmlWriteFeature> = setOf(
            XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL,
            XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE,
            XmlWriteFeature.AUTO_DETECT_XSI_TYPE,
            XmlWriteFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS,
        )

        /** `xsi:nil` 使用的命名空间。 */
        public const val XSI_NAMESPACE: String = "http://www.w3.org/2001/XMLSchema-instance"

        /** `xsi:type` 中 XSD 内建类型使用的命名空间。 */
        public const val XSD_NAMESPACE: String = "http://www.w3.org/2001/XMLSchema"

        /** 属性映射默认前缀。 */
        public const val DEFAULT_ATTRIBUTE_PREFIX: String = "@"

        /** 混合内容/标量属性包装的默认文本字段名。 */
        public const val DEFAULT_TEXT_KEY: String = "#text"

        /** 带属性数组包装的默认数组字段名。 */
        public const val DEFAULT_ITEMS_KEY: String = "#items"

        /** 有序混合内容的默认数组字段名。 */
        public const val DEFAULT_CONTENT_KEY: String = "#content"

        /** 根元素默认名称，也是判断是否显式配置根名的基准。 */
        public const val DEFAULT_ROOT_NAME: String = "root"

        /**
         * 默认最大 XML 元素嵌套深度（根元素深度为 `1`）。
         *
         * 512 层足以覆盖常见业务文档，同时避免恶意超深输入耗尽调用栈。
         */
        public const val DEFAULT_MAX_DEPTH: Int = 512

        /**
         * 创建 XML 配置构建器。
         */
        @JvmStatic
        public fun builder(): XmlConfigBuilder = XmlConfigBuilder()
    }
}
