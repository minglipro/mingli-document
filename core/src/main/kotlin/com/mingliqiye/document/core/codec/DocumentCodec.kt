package com.mingliqiye.document.core.codec

import com.mingliqiye.document.core.Node

/**
 * 文档解析器，负责把某一种格式的文本解析为统一的 [Node] 树。
 */
public fun interface DocumentParser {

    /**
     * 解析文本。
     *
     * @param source 格式文本
     * @return 文档树
     */
    public fun parse(source: String): Node
}

/**
 * 文档序列化器，负责把统一的 [Node] 树写成某一种格式的文本。
 */
public fun interface DocumentWriter {

    /**
     * 序列化文档树。
     *
     * @param node 文档树
     * @param pretty 是否格式化输出
     * @return 格式文本
     */
    public fun write(node: Node, pretty: Boolean): String
}

/**
 * 使用默认的压缩模式序列化文档树。
 *
 * @param node 文档树
 * @return 格式文本
 */
public fun DocumentWriter.write(node: Node): String = write(node, false)

/**
 * 某一种具体格式的编解码器。
 *
 * 例如 JSON、YAML、XML 各自实现一个 [DocumentCodec]；核心树模型不依赖任何具体格式。
 */
public interface DocumentCodec :
    DocumentParser,
    DocumentWriter {

    /** 格式名称，例如 `json`、`yaml`、`xml`。 */
    public val format: String
}
