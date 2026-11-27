package com.mingliqiye.document.format.xml

/**
 * XML 混合内容处理策略。
 *
 * 混合内容指元素同时包含直接文本与子元素。
 */
public enum class XmlMixedContentPolicy {

    /**
     * 保留直接文本（默认，向后兼容）。
     *
     * 直接文本拼接后写入 `textKey` 字段；只保留首尾空白裁剪后的文本，
     * 与子元素的相对顺序不保留（写出时文本固定在子元素之前）。
     */
    PRESERVE,

    /**
     * 按文档顺序保留混合内容。
     *
     * 在 [PRESERVE] 的 `textKey` 与子元素字段兼容视图之外，额外输出 `contentKey`
     * 有序数组：文本项为 `StringNode`，子元素项为单字段 `ObjectNode`（`{元素名: 节点}`）。
     * 写出时按 `contentKey` 顺序交错输出文本与子元素；`contentKey` 存在时优先于
     * `textKey` 与普通子元素字段。只有非空白直接文本生成文本项，相邻文本节点合并。
     */
    PRESERVE_ORDERED,

    /**
     * 拒绝混合内容。
     *
     * 读取到非空白直接文本与子元素并存时抛 [com.mingliqiye.document.format.xml.exception.XmlParseException]。
     */
    REJECT,
}
