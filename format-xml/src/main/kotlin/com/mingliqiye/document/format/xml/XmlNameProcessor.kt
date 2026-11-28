package com.mingliqiye.document.format.xml

/**
 * XML 名称处理器，对齐 上游实现 XML 的 `XmlNameProcessor`。
 *
 * 在元素/属性名与 Node 字段名之间做可逆或规范化转换，例如把含空格/特殊字符的字段名
 * 转成合法 XML 名称。写出时先调用 [encode]，读取时对 XML 名称调用 [decode]。
 * 默认实现 [XmlNameProcessors.passthrough] 不做转换。
 */
public fun interface XmlNameProcessor {

    /**
     * 把 Node 字段名编码为 XML 名称片段（元素/属性本地名）。
     *
     * 返回值随后仍会经过本库的 `_xHHHH_` 非法字符转义；实现可以返回合法 XML 名称。
     *
     * @param name 字段名或元素/属性本地名
     * @return 编码后的名称
     */
    public fun encode(name: String): String

    /**
     * 把 XML 名称片段解码回 Node 字段名。
     *
     * 不可逆的处理器（如 replacement）直接返回原名称；默认原样返回。
     *
     * @param encodedName XML 名称
     * @return 解码后的名称
     */
    public fun decode(encodedName: String): String = encodedName
}
