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
    /** 混合内容处理策略；默认 [XmlMixedContentPolicy.PRESERVE]。 */
    public val mixedContentPolicy: XmlMixedContentPolicy,
) : DocumentMapperConfig(base) {

    /** 是否启用属性映射，等价于 [attributePrefix] 不为 `null`。 */
    public val attributeMode: Boolean
        get() = attributePrefix != null

    /** 是否使用 Clark 名称模式。 */
    public val clarkNames: Boolean
        get() = namespaceMode == XmlNamespaceMode.CLARK

    public companion object {

        /** 属性映射默认前缀。 */
        public const val DEFAULT_ATTRIBUTE_PREFIX: String = "@"

        /** 混合内容/标量属性包装的默认文本字段名。 */
        public const val DEFAULT_TEXT_KEY: String = "#text"

        /** 带属性数组包装的默认数组字段名。 */
        public const val DEFAULT_ITEMS_KEY: String = "#items"

        /**
         * 创建 XML 配置构建器。
         */
        @JvmStatic
        public fun builder(): XmlConfigBuilder = XmlConfigBuilder()
    }
}
