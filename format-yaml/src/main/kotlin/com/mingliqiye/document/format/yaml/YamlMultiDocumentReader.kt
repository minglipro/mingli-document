package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException

/**
 * YAML 多文档读取入口。
 *
 * YAML 流可以用 `---` 开始一个新文档、用 `...` 显式结束当前文档；本类按顺序
 * 返回全部文档树。单文档入口 [YamlReader] 语义保持不变。
 *
 * 默认拒绝未知自定义 tag；将 [allowCustomTags] 设为 `true` 后，每个文档中的未知
 * tag 按节点种类还原（标量 -> StringNode，序列 -> ArrayNode，映射 -> ObjectNode），
 * 与 [YamlReader] 一致。
 *
 * 边界约定：
 * - 空白或只含注释的输入不构成文档，返回空列表；
 * - `---` / `...` 界定的空文档按 [NullNode] 返回，因此返回列表长度等于显式
 *   文档个数；
 * - 任一文档格式非法时整体抛出 [YamlParseException]；
 * - anchor/alias 只在单个文档内有效，跨文档引用按 YAML 规范不支持（会抛
 *   [YamlParseException]）。
 *
 * @param source YAML 文本
 * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
 * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
 * @param allowCustomTags 是否允许未知自定义 tag，默认 `false`（保持 SafeConstructor 行为）
 */
public class YamlMultiDocumentReader @JvmOverloads constructor(
    private val source: String,
    private val maxDepth: Int = 0,
    private val maxAliases: Int = 0,
    private val allowCustomTags: Boolean = false,
) {

    /**
     * 解析并返回全部文档树。
     *
     * @return 按文档顺序排列的文档树列表
     * @throws YamlParseException YAML 格式非法或包含不支持的类型时抛出
     */
    public fun readAll(): List<Node> = YamlNodeMapping.readAll(source, maxDepth, maxAliases, allowCustomTags)

    public companion object {

        /**
         * 解析多文档 YAML 字符串。
         *
         * @param source YAML 文本
         * @return 按文档顺序排列的文档树列表
         * @throws YamlParseException YAML 格式非法时抛出
         */
        @JvmStatic
        public fun parseAll(source: String): List<Node> = YamlMultiDocumentReader(source).readAll()
    }
}

/**
 * 将多文档 YAML 字符串解析为文档树列表。
 *
 * @return 按文档顺序排列的文档树列表
 * @throws YamlParseException YAML 格式非法时抛出
 */
public fun String.toYamlNodes(): List<Node> = YamlMultiDocumentReader(this).readAll()

/**
 * 按 reified 泛型参数把多文档 YAML 解析为实体列表。
 *
 * 用法：`mapper.parseAll<Person>(text)`；需要非 reified 泛型时使用
 * [YamlMapper.parseAll] 的 `DocumentTypeReference` 重载。
 *
 * @param source YAML 文本
 * @return 按文档顺序反序列化得到的实体列表
 */
public inline fun <reified T> YamlMapper.parseAll(source: String): List<T> = parseAll(source, DocumentTypeReference.of<T>())
