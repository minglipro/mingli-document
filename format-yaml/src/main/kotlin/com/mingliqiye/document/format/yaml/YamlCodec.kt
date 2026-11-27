package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.Node

/**
 * YAML 文本编解码内部实现，仅供 [YamlMapper] 与同模块测试使用。
 */
internal object YamlCodec {

    /** 格式名称。 */
    val format: String = "yaml"

    /** 解析 YAML 文本。 */
    fun parse(source: String): Node = YamlReader(source).read()

    /** 解析多文档 YAML 文本，按文档顺序返回全部文档树。 */
    fun parseAll(source: String, maxDepth: Int = 0, maxAliases: Int = 0): List<Node> = YamlMultiDocumentReader(source, maxDepth, maxAliases).readAll()

    /** 写出 YAML 文本。 */
    fun write(node: Node, pretty: Boolean): String = YamlWriter(pretty).write(node)

    /** 写出 `---` 分隔的 YAML 多文档文本。 */
    fun writeAll(nodes: Iterable<Node>, pretty: Boolean): String = YamlWriter(pretty).writeAll(nodes)
}
