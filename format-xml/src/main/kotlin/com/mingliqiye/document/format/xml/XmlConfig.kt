package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * XML Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 XML 根元素名、属性映射与命名空间开关。
 *
 * 属性使用“前缀 + 名称”的普通对象字段表示（例如 `@id`），不引入新的节点类型；
 * 命名空间使用“前缀限定名 + `xmlns` 属性字段”表示（例如 `ns:name`、`@xmlns:ns`）。
 * 默认全部关闭，保持原有 `type` / `nil` 协议与普通 XML 推断行为。
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
     * 是否启用命名空间感知读取/写出。
     *
     * 开启后读取保留前缀限定名与 `xmlns` 属性字段，写出允许 `prefix:local` 形式的
     * 元素/属性名。默认关闭，保持原有 `type` / `nil` 协议行为。
     */
    public val namespaceAware: Boolean,
) : DocumentMapperConfig(base) {

    /** 是否启用属性映射，等价于 [attributePrefix] 不为 `null`。 */
    public val attributeMode: Boolean
        get() = attributePrefix != null

    public companion object {

        /** 属性映射默认前缀。 */
        public const val DEFAULT_ATTRIBUTE_PREFIX: String = "@"

        /**
         * 创建 XML 配置构建器。
         */
        @JvmStatic
        public fun builder(): XmlConfigBuilder = XmlConfigBuilder()
    }
}
