package com.mingliqiye.document.format.xml

/**
 * XML 写出特性，对齐 Jackson XML 的 `XmlWriteFeature` 中与 null 相关的行为。
 */
public enum class XmlWriteFeature {

    /**
     * null 值写出为 `xsi:nil="true"`，默认开启。
     *
     * 关闭时 null 写成空元素，如 `<age/>`。
     */
    WRITE_NULLS_AS_XSI_NIL,
}
