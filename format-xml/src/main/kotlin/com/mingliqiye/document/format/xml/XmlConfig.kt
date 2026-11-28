package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * XML Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 XML 根元素名、属性映射、命名空间与混合内容选项。
 *
 * 属性使用“前缀 + 名称”的普通对象字段表示（例如 `@id`），不引入新的节点类型；
 * 带属性的标量元素用 `{ 属性字段..., textKey: 标量 }` 包装对象表示，
 * 带属性的数组元素用 `{ 属性字段..., itemsKey: 数组 }` 包装对象表示。
 * 命名空间默认使用“前缀限定名 + `xmlns` 属性字段”表示，也可切换为 Clark 名称模式。
 * 混合内容默认只保留 `textKey` 文本；[XmlMixedContentPolicy.PRESERVE_ORDERED] 时
 * 额外输出 `contentKey` 有序数组以保留文本/子元素相对顺序。
 * 属性映射、Clark 模式与混合内容保留都存在默认值，保持原有 `type` / `nil` 协议兼容。
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
) : DocumentMapperConfig(base) {

    init {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
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
     * 默认 `textKey`/`itemsKey`/`contentKey`、[XmlMixedContentPolicy.PRESERVE] 混合内容。
     * `rootName` 只影响写出，不影响判断；Node 树限制与非默认通用配置由 Mapper 另行判断回退。
     */
    internal fun usesDefaultReadConfiguration(): Boolean = attributePrefix == null &&
        !namespaceAware &&
        namespaceMode == XmlNamespaceMode.PREFIX &&
        textKey == DEFAULT_TEXT_KEY &&
        itemsKey == DEFAULT_ITEMS_KEY &&
        contentKey == DEFAULT_CONTENT_KEY &&
        mixedContentPolicy == XmlMixedContentPolicy.PRESERVE

    public companion object {

        /** 属性映射默认前缀。 */
        public const val DEFAULT_ATTRIBUTE_PREFIX: String = "@"

        /** 混合内容/标量属性包装的默认文本字段名。 */
        public const val DEFAULT_TEXT_KEY: String = "#text"

        /** 带属性数组包装的默认数组字段名。 */
        public const val DEFAULT_ITEMS_KEY: String = "#items"

        /** 有序混合内容的默认数组字段名。 */
        public const val DEFAULT_CONTENT_KEY: String = "#content"

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
