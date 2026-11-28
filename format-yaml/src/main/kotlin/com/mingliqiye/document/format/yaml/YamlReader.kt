package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.yaml.exception.YamlParseException

/**
 * YAML 单文档读取入口。
 *
 * 使用 SnakeYAML 的安全构造器，只还原标准 YAML 标量、集合和映射，
 * 不实例化任意 Java 类型。`!!binary` 解码为
 * [com.mingliqiye.document.core.BinaryNode] 而不是 Base64 字符串节点。
 *
 * 默认拒绝未知自定义 tag；将 [allowCustomTags] 设为 `true` 后，未知 tag 按节点
 * 种类还原：标量 -> [com.mingliqiye.document.core.StringNode]，序列 ->
 * [com.mingliqiye.document.core.ArrayNode]，映射 ->
 * [com.mingliqiye.document.core.ObjectNode]，anchor/merge/alias/深度限制保持有效。
 *
 * 读取特性（[readFeatures]）与 SnakeYAML LoaderOptions（[allowDuplicateKeys]、
 * [codePointLimit]）对齐 上游实现 YAML 2.15，详见 [YamlConfig]。
 *
 * 输入包含多个文档时请使用 [YamlMultiDocumentReader] 或 [YamlMapper.parseAllToNodes]。
 *
 * @param source YAML 文本
 * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
 * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
 * @param allowCustomTags 是否允许未知自定义 tag，默认 `false`（保持 SafeConstructor 行为）
 * @param readFeatures 读取特性集合，默认 [YamlConfig.DEFAULT_READ_FEATURES]
 * @param allowDuplicateKeys 是否允许重复 mapping key，默认 `false`
 * @param codePointLimit 单次输入最大 code point 数量；`0` 使用 SnakeYAML 默认值
 */
public class YamlReader @JvmOverloads constructor(
    private val source: String,
    private val maxDepth: Int = 0,
    private val maxAliases: Int = 0,
    private val allowCustomTags: Boolean = false,
    private val readFeatures: Set<YamlReadFeature> = YamlConfig.DEFAULT_READ_FEATURES,
    private val allowDuplicateKeys: Boolean = false,
    private val codePointLimit: Int = 0,
) {

    /**
     * 解析并返回文档树。
     *
     * 空文本或只含注释的文本返回 [com.mingliqiye.document.core.NullNode]；
     * 多个文档的输入会作为格式错误抛出，多文档读取请使用 [YamlMultiDocumentReader]。
     *
     * @throws YamlParseException YAML 格式非法或包含不支持的类型时抛出
     */
    public fun read(): Node = YamlNodeMapping.readRoot(
        source,
        maxDepth,
        maxAliases,
        allowCustomTags,
        readFeatures,
        allowDuplicateKeys,
        codePointLimit,
    )

    public companion object {

        /**
         * 解析 YAML 字符串。
         *
         * @param source YAML 文本
         * @return 文档树
         * @throws YamlParseException YAML 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = YamlReader(source).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @return 文档树
 * @throws YamlParseException YAML 格式非法时抛出
 */
public fun String.toYamlNode(): Node = YamlReader(this).read()
