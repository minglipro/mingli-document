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
import org.yaml.snakeyaml.nodes.Node as YamlNode

/**
 * 带可选自定义 tag 支持的 SnakeYAML 安全构造器。
 *
 * 默认（[allowCustomTags] 为 `false`）行为与 [SafeConstructor] 完全一致：未知 tag
 * 由 `ConstructUndefined` 抛出 `ConstructorException`，最终归一为
 * [com.mingliqiye.document.format.yaml.exception.YamlParseException]。
 *
 * 开启后，没有注册构造器的未知 tag（如 `!foo`、`!env`、`!!foo` 及
 * `!<tag:example.com,2024:foo>` 等全局自定义 tag）按节点种类还原为基础类型：
 * - 标量 -> [String]（原始标量文本，不做 `!!int`/`!!bool` 等隐式解析）
 * - 序列 -> [List]
 * - 映射 -> [Map]（先执行重复 key 校验与 merge key 展开，与标准 `!!map` 一致）
 *
 * 该构造器仍然继承 [SafeConstructor]：自定义 tag 只影响"tag -> 基础类型"这一步，
 * 不会根据 tag 实例化任意 Java 类型。anchor/alias/深度限制仍由 SnakeYAML 执行，
 * 但以自定义 tag 节点为目标的递归 alias 会被拒绝（抛出 [YAMLException]）。
 *
 * 同时供 Node 路径（[YamlNodeMapping]）与原生 Token 路径（[YamlTokenReader]）使用，
 * 保证两条路径对未知 tag 的行为一致。
 *
 * @param options SnakeYAML 装载选项，见 [YamlConstructors.createOptions]
 * @param allowCustomTags 是否允许未知自定义 tag
 */
internal class YamlConstructor(
    options: LoaderOptions,
    private val allowCustomTags: Boolean,
) : SafeConstructor(options) {

    private val customTagConstruct: Construct = CustomTagConstruct()

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
     */
    fun createOptions(maxDepth: Int, maxAliases: Int, allowCustomTags: Boolean): LoaderOptions {
        val options = LoaderOptions()
        options.setAllowDuplicateKeys(false)
        if (maxDepth > 0) {
            options.setNestingDepthLimit(maxDepth)
        }
        if (maxAliases > 0) {
            options.setMaxAliasesForCollections(maxAliases)
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
     */
    fun create(maxDepth: Int = 0, maxAliases: Int = 0, allowCustomTags: Boolean = false): YamlConstructor = YamlConstructor(
        createOptions(maxDepth, maxAliases, allowCustomTags),
        allowCustomTags,
    )
}
