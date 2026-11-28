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

    /**
     * 解析多文档 YAML 文本，按文档顺序返回全部文档树。
     *
     * @param source YAML 文本
     * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
     * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
     * @param allowCustomTags 是否允许未知自定义 tag
     */
    fun parseAll(source: String, maxDepth: Int = 0, maxAliases: Int = 0, allowCustomTags: Boolean = false): List<Node> = YamlMultiDocumentReader(
        source,
        maxDepth,
        maxAliases,
        allowCustomTags,
    ).readAll()

    /** 写出 YAML 文本。 */
    fun write(node: Node, pretty: Boolean): String = YamlWriter(pretty).write(node)

    /** 写出 `---` 分隔的 YAML 多文档文本。 */
    fun writeAll(nodes: Iterable<Node>, pretty: Boolean): String = YamlWriter(pretty).writeAll(nodes)
}
