package com.mingliqiye.document.format.xml

/**
 * XML 混合内容处理策略。
 *
 * 混合内容指元素同时包含直接文本与子元素。
 */
public enum class XmlMixedContentPolicy {

    /**
     * 保留直接文本（默认）。
     *
     * 直接文本拼接后写入 `textKey` 字段；只保留首尾空白裁剪后的文本，
     * 与子元素的相对顺序不保留（写出时文本固定在子元素之前）。
     */
    PRESERVE,

    /**
     * 拒绝混合内容。
     *
     * 读取到非空白直接文本与子元素并存时抛 [com.mingliqiye.document.format.xml.exception.XmlParseException]。
     */
    REJECT,
}
