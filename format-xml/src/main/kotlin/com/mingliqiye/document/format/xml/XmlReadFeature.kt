package com.mingliqiye.document.format.xml

/**
 * XML 读取特性，对齐 上游实现 XML 3.x 的 `XmlReadFeature`（2.x 对应项见各项说明）。
 *
 * 通过 [XmlConfigBuilder.enable] / [XmlConfigBuilder.disable] / [XmlConfigBuilder.configure]
 * 开关；默认值对齐 上游实现 XML 3.x，可用 [XmlConfigBuilder.上游库2Defaults] 切到 上游实现 2.x 默认。
 */
public enum class XmlReadFeature {

    /**
     * 自动识别 `xsi:type` 并按 XSD 内建类型读取，默认开启（上游实现 XML 3.x 默认；2.x 默认关闭）。
     *
     * 本库的 Node 模型不携带 Java 类信息，因此该特性不执行多态子类解析，而是把
     * `xsi:type="xs:string|xs:boolean|xs:int|xs:decimal|xs:double|xs:base64Binary|..."` 等
     * XSD 标量类型映射为对应的 Node 类型，保证与 [XmlWriteFeature.AUTO_DETECT_XSI_TYPE]
     * 写出结果可往返。未知 XSD 类型回退到结构推断。
     */
    AUTO_DETECT_XSI_TYPE,

    /**
     * 把空元素（如 `<age/>`）读取为 null，默认关闭。
     *
     * 关闭时空元素读取为空字符串 `""`。
     */
    EMPTY_ELEMENT_AS_NULL,

    /**
     * 识别 `xsi:nil="true"` 并读取为 null，默认开启。
     */
    PROCESS_XSI_NIL,
}
