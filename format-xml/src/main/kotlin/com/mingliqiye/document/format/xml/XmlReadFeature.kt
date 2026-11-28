package com.mingliqiye.document.format.xml

/**
 * XML 读取特性，对齐 Jackson XML 的 `XmlReadFeature` 中与 null/空元素相关的行为。
 */
public enum class XmlReadFeature {

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
