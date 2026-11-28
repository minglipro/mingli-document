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
     * @param readFeatures 读取特性集合
     * @param allowDuplicateKeys 是否允许重复 mapping key
     * @param codePointLimit 单次输入最大 code point 数量；`0` 使用 SnakeYAML 默认值
     */
    fun parseAll(
        source: String,
        maxDepth: Int = 0,
        maxAliases: Int = 0,
        allowCustomTags: Boolean = false,
        readFeatures: Set<YamlReadFeature> = YamlConfig.DEFAULT_READ_FEATURES,
        allowDuplicateKeys: Boolean = false,
        codePointLimit: Int = 0,
    ): List<Node> = YamlMultiDocumentReader(
        source,
        maxDepth,
        maxAliases,
        allowCustomTags,
        readFeatures,
        allowDuplicateKeys,
        codePointLimit,
    ).readAll()

    /** 使用默认写出特性写出 YAML 文本。 */
    fun write(node: Node, pretty: Boolean): String = write(node, YamlWriteSettings.DEFAULT, pretty)

    /** 按指定写出特性写出 YAML 文本。 */
    fun write(node: Node, settings: YamlWriteSettings, pretty: Boolean): String = YamlWriter(settings, pretty).write(node)

    /** 使用默认写出特性写出 `---` 分隔的 YAML 多文档文本。 */
    fun writeAll(nodes: Iterable<Node>, pretty: Boolean): String = writeAll(nodes, YamlWriteSettings.DEFAULT, pretty)

    /** 按指定写出特性写出 `---` 分隔的 YAML 多文档文本。 */
    fun writeAll(nodes: Iterable<Node>, settings: YamlWriteSettings, pretty: Boolean): String = YamlWriter(settings, pretty).writeAll(nodes)
}
