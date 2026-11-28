package com.mingliqiye.document.format.xml

/**
 * XML 写出特性，对齐 上游实现 XML 3.x 的 `XmlWriteFeature`（2.x 中对应 `ToXmlGenerator.Feature`）。
 *
 * 通过 [XmlConfigBuilder.enable] / [XmlConfigBuilder.disable] / [XmlConfigBuilder.configure]
 * 开关；默认值对齐 上游实现 XML 3.x，可用 [XmlConfigBuilder.上游库2Defaults] 切到 上游实现 2.x 默认。
 */
public enum class XmlWriteFeature {

    /**
     * 输出 XML 声明，默认关闭。
     *
     * 声明形如 `<?xml version="1.0" encoding="UTF-8"?>`；编码取自
     * [com.mingliqiye.document.serialization.DocumentMapperConfig.fileCharset]。
     */
    WRITE_XML_DECLARATION,

    /**
     * 输出 XML 1.1 声明，默认关闭。
     *
     * 开启时声明版本为 `1.1`（并自动写出声明）；文本中的控制字符会写成字符引用，
     * 以符合 XML 1.1 词法规则。
     */
    WRITE_XML_1_1,

    /**
     * 在 XML 声明中加入 `standalone="yes"`，默认关闭。
     *
     * 与 上游实现 一致：只有同时写出声明（[WRITE_XML_DECLARATION] 或 [WRITE_XML_1_1]）时生效。
     */
    WRITE_STANDALONE_YES_TO_XML_DECLARATION,

    /**
     * null 值写出为 `xsi:nil="true"`，默认开启（上游实现 XML 3.x 默认；2.x 默认关闭）。
     *
     * 关闭时 null 写成空元素，如 `<age/>`。
     */
    WRITE_NULLS_AS_XSI_NIL,

    /**
     * 根对象为单字段 `ObjectNode` 时，用其字段名作为根元素名并直接写出该字段内容，默认开启
     * （上游实现 XML 3.x 默认；2.x 无此特性）。
     *
     * 仅作用于“以 Node 树显式写出”的路径（`format(node)` / `Node.toXmlString`）：Bean 序列化
     * 与显式配置了根元素名（`XmlConfig.rootName` 非默认值或类上 `@DocumentRootName`）时不展开。
     */
    UNWRAP_ROOT_OBJECT_NODE,

    /**
     * 写出时按需生成 `xsi:type`，默认开启（上游实现 XML 3.x 默认；2.x 默认关闭）。
     *
     * 本库 Node 模型不携带 Java 多态类型信息，因此行为替代为：仅当标量类型无法通过文本
     * 推断保真时写出 XSD 内建类型（如看起来像数字/布尔的字符串写 `xs:string`、二进制写
     * `xs:base64Binary`、特殊浮点写 `xs:double`），保证
     * [XmlReadFeature.AUTO_DETECT_XSI_TYPE] 可往返。开启 [XmlConfig.typeAttributes] 时
     * 类型已由 `type` 协议保存，不再写 `xsi:type`。
     */
    AUTO_DETECT_XSI_TYPE,

    /**
     * 按 XML Schema 规则写出浮点特殊值，默认开启（上游实现 XML 3.x 默认；2.x 默认关闭）。
     *
     * `Infinity` / `+Infinity` 写成 `INF`，`-Infinity` 写成 `-INF`，`NaN` 保持 `NaN`。
     */
    WRITE_XML_SCHEMA_CONFORMING_FLOATS,
}
