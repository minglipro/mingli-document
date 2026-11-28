package com.mingliqiye.document.format.yaml

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.AbstractConstruct
import org.yaml.snakeyaml.constructor.Construct
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.inspector.TagInspector
import org.yaml.snakeyaml.nodes.AnchorNode
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.nodes.Node as YamlNode

/**
 * 带可选自定义 tag 与 上游实现 YAML 读取特性的 SnakeYAML 安全构造器。
 *
 * 默认（[allowCustomTags] 为 `false`）行为与 [SafeConstructor] 一致：未知 tag
 * 由 `ConstructUndefined` 抛出 `ConstructorException`，最终归一为
 * [com.mingliqiye.document.format.yaml.exception.YamlParseException]。
 *
 * 开启 [allowCustomTags] 后，没有注册构造器的未知 tag（如 `!foo`、`!env`、`!!foo`
 * 及 `!<tag:example.com,2024:foo>` 等全局自定义 tag）按节点种类还原为基础类型：
 * - 标量 -> [String]（原始标量文本，不做 `!!int`/`!!bool` 等隐式解析）
 * - 序列 -> [List]
 * - 映射 -> [Map]（先执行重复 key 校验与 merge key 展开，与标准 `!!map` 一致）
 *
 * 此外按 [readFeatures] 对齐 上游实现 YAML 2.15 的读取行为：
 * - [YamlReadFeature.EMPTY_STRING_AS_NULL]：空 plain 标量 -> null（默认开启）；
 *   关闭后空 plain 标量 -> 空字符串 `""`；
 * - [YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS]：布尔词按字符串读取；
 *   关闭时 `yes`/`no`/`on`/`off`/`true`/`false` 解析为布尔，开启后仅
 *   `true`/`false` 为布尔，其余为字符串；显式 `!!bool` 无法匹配的值按字符串
 *   （上游实现 行为）。
 *
 * 该构造器仍然继承 [SafeConstructor]：自定义 tag 与读取特性只影响
 * "tag -> 基础类型"这一步，不会根据 tag 实例化任意 Java 类型。anchor/alias/
 * 深度限制仍由 SnakeYAML 执行，但以自定义 tag 节点为目标的递归 alias 会被拒绝
 * （抛出 [YAMLException]）。
 *
 * 同时供 Node 路径（[YamlNodeMapping]）与原生 Token 路径（[YamlTokenReader]）使用，
 * 保证两条路径行为一致。
 *
 * @param options SnakeYAML 装载选项，见 [YamlConstructors.createOptions]
 * @param allowCustomTags 是否允许未知自定义 tag
 * @param readFeatures 上游实现 YAML 对齐的读取特性集合
 */
internal class YamlConstructor(
    options: LoaderOptions,
    private val allowCustomTags: Boolean,
    readFeatures: Set<YamlReadFeature> = YamlConfig.DEFAULT_READ_FEATURES,
) : SafeConstructor(options) {

    private val emptyStringAsNull: Boolean = YamlReadFeature.EMPTY_STRING_AS_NULL in readFeatures
    private val parseBooleanLikeWordsAsStrings: Boolean = YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS in readFeatures
    private val customTagConstruct: Construct = CustomTagConstruct()

    init {
        // 覆盖 SafeConstructor 的 null/bool 构造，实现 上游实现 YAML 读取特性。
        yamlConstructors[Tag.NULL] = EmptyStringNullConstruct()
        yamlConstructors[Tag.BOOL] = BooleanLikeConstruct()
    }

    /**
     * 节点 tag 是否没有对应的构造器（即未知 tag）。
     *
     * 与 [getConstructor] 的判定保持一致；Token 路径据此在关闭自定义 tag 时拒绝
     * 未知 tag，从而与 Node 路径行为一致。
     */
    fun isUndefinedTag(node: YamlNode): Boolean = !node.useClassConstructor() && super.getConstructor(node) === SafeConstructor.undefinedConstructor

    /**
     * 按节点 tag 构造单个节点的值（标量与 mapping 字段名使用）。
     */
    fun constructNodeValue(node: YamlNode): Any? = constructObject(node)

    /**
     * 执行重复 key 校验与 merge key 展开（mapping 使用，与 Node 路径一致）。
     */
    fun flattenMappingNode(node: MappingNode) {
        flattenMapping(node)
    }

    override fun getConstructor(node: YamlNode): Construct {
        val constructor = super.getConstructor(node)
        if (!allowCustomTags || node.useClassConstructor() || constructor !== SafeConstructor.undefinedConstructor) {
            return constructor
        }
        return customTagConstruct
    }

    /**
     * 上游实现 `EMPTY_STRING_AS_NULL` 语义：
     * 空 plain 标量在开启时构造为 null，关闭时构造为空字符串。
     *
     * SnakeYAML 在空文档时以 `null` 节点调用构造器，因此参数可空。
     */
    private inner class EmptyStringNullConstruct : AbstractConstruct() {

        override fun construct(node: YamlNode?): Any? {
            if (node == null || node !is ScalarNode) {
                return null
            }
            val value = constructScalar(node)
            return if (value.isEmpty() && !emptyStringAsNull) "" else null
        }
    }

    /**
     * 上游实现 `PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS` 语义：
     * 匹配的布尔词构造为 [Boolean]，无法匹配时按字符串（上游实现 行为）。
     */
    private inner class BooleanLikeConstruct : AbstractConstruct() {

        override fun construct(node: YamlNode?): Any? {
            if (node == null || node !is ScalarNode) {
                return null
            }
            val value = constructScalar(node)
            return matchBoolean(value) ?: value
        }
    }

    private fun matchBoolean(value: String): Boolean? {
        if (parseBooleanLikeWordsAsStrings) {
            return when {
                value.equals("true", ignoreCase = true) -> true
                value.equals("false", ignoreCase = true) -> false
                else -> null
            }
        }
        return when (value.length) {
            1 -> when (value[0]) {
                'y', 'Y' -> true
                'n', 'N' -> false
                else -> null
            }

            2 -> when {
                value.equals("no", ignoreCase = true) -> false
                value.equals("on", ignoreCase = true) -> true
                else -> null
            }

            3 -> when {
                value.equals("yes", ignoreCase = true) -> true
                value.equals("off", ignoreCase = true) -> false
                else -> null
            }

            4 -> if (value.equals("true", ignoreCase = true)) true else null
            5 -> if (value.equals("false", ignoreCase = true)) false else null
            else -> null
        }
    }

    /**
     * 把未知 tag 节点按节点种类构造为 String/List/Map。
     *
     * 递归 alias 指向自身时，SnakeYAML 的构造器状态会抛出 `YAMLException`，
     * 这里不额外构建环。
     */
    private fun constructCustomTag(node: YamlNode): Any? {
        val realNode = if (node is AnchorNode) node.realNode else node
        return when (realNode) {
            is ScalarNode -> constructScalar(realNode)
            is SequenceNode -> constructSequence(realNode)
            is MappingNode -> constructMapping(realNode)
            else -> throw YAMLException("Unsupported YAML node type for custom tag: ${realNode.javaClass.name}")
        }
    }

    private inner class CustomTagConstruct : AbstractConstruct() {

        override fun construct(node: YamlNode): Any? = constructCustomTag(node)
    }
}

/**
 * 创建 YAML 装载选项与 [YamlConstructor] 的工厂。
 *
 * 集中 Node 路径与 Token 路径的选项，避免两处规则漂移。
 */
internal object YamlConstructors {

    /**
     * 创建装载选项。
     *
     * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
     * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
     * @param allowCustomTags 是否允许未知自定义 tag（同时放开全局自定义 tag 的
     *   `TagInspector` 限制；构造阶段只还原基础类型，不实例化 Java 类型）
     * @param allowDuplicateKeys 是否允许重复 mapping key
     * @param codePointLimit 单次输入最大 code point 数量；`0` 使用 SnakeYAML 默认值
     */
    fun createOptions(
        maxDepth: Int = 0,
        maxAliases: Int = 0,
        allowCustomTags: Boolean = false,
        allowDuplicateKeys: Boolean = false,
        codePointLimit: Int = 0,
    ): LoaderOptions {
        val options = LoaderOptions()
        options.setAllowDuplicateKeys(allowDuplicateKeys)
        if (maxDepth > 0) {
            options.setNestingDepthLimit(maxDepth)
        }
        if (maxAliases > 0) {
            options.setMaxAliasesForCollections(maxAliases)
        }
        if (codePointLimit > 0) {
            options.setCodePointLimit(codePointLimit)
        }
        if (allowCustomTags) {
            options.setTagInspector(TagInspector { true })
        }
        return options
    }

    /**
     * 创建构造器。
     *
     * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
     * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
     * @param allowCustomTags 是否允许未知自定义 tag
     * @param readFeatures 上游实现 YAML 对齐的读取特性集合
     * @param allowDuplicateKeys 是否允许重复 mapping key
     * @param codePointLimit 单次输入最大 code point 数量；`0` 使用 SnakeYAML 默认值
     */
    fun create(
        maxDepth: Int = 0,
        maxAliases: Int = 0,
        allowCustomTags: Boolean = false,
        readFeatures: Set<YamlReadFeature> = YamlConfig.DEFAULT_READ_FEATURES,
        allowDuplicateKeys: Boolean = false,
        codePointLimit: Int = 0,
    ): YamlConstructor = YamlConstructor(
        createOptions(maxDepth, maxAliases, allowCustomTags, allowDuplicateKeys, codePointLimit),
        allowCustomTags,
        readFeatures,
    )
}
