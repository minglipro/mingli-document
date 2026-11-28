package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date

/**
 * SnakeYAML 装载结果与 [Node] 树之间的转换。
 *
 * 由 [YamlReader]（单文档）与 [YamlMultiDocumentReader]（多文档）共用：
 * - 使用安全构造器，只还原标准 YAML 标量、集合和映射，不实例化任意 Java 类型；
 * - `!!binary` 由 SnakeYAML 解码为 `byte[]`，这里映射为 [BinaryNode] 以保留原始字节；
 * - `!!timestamp` 等日期值写成 ISO-8601 字符串节点；
 * - 开启自定义 tag 支持后，未知 tag 按基础类型还原：标量 -> [StringNode]，
 *   序列 -> [ArrayNode]，映射 -> [ObjectNode]（见 [YamlConstructor]）。
 */
internal object YamlNodeMapping {

    /**
     * 创建安全 YAML 实例。
     *
     * 关闭重复键容忍，重复映射键会作为格式错误上报。
     *
     * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
     * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
     * @param allowCustomTags 是否允许未知自定义 tag
     */
    fun createYaml(maxDepth: Int = 0, maxAliases: Int = 0, allowCustomTags: Boolean = false): Yaml = Yaml(
        YamlConstructors.create(maxDepth, maxAliases, allowCustomTags),
    )

    /**
     * 装载单个文档；输入为空或只含注释时得到 [NullNode]。
     *
     * @param source YAML 文本
     * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
     * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
     * @param allowCustomTags 是否允许未知自定义 tag
     * @throws YamlParseException YAML 格式非法时抛出
     */
    fun readRoot(source: String, maxDepth: Int = 0, maxAliases: Int = 0, allowCustomTags: Boolean = false): Node = load {
        toNode(createYaml(maxDepth, maxAliases, allowCustomTags).load<Any?>(source))
    }

    /**
     * 按文档顺序装载全部文档。
     *
     * 空白或只含注释的输入不构成文档，返回空列表；`---` 显式界定的空文档
     * 对应 [NullNode]。
     *
     * @param source YAML 文本
     * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
     * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
     * @param allowCustomTags 是否允许未知自定义 tag
     * @throws YamlParseException YAML 格式非法时抛出
     */
    fun readAll(source: String, maxDepth: Int = 0, maxAliases: Int = 0, allowCustomTags: Boolean = false): List<Node> = load {
        val nodes = ArrayList<Node>()
        for (document in createYaml(maxDepth, maxAliases, allowCustomTags).loadAll(source)) {
            nodes.add(toNode(document))
        }
        nodes
    }

    /**
     * 统一包装 SnakeYAML 的异常。
     *
     * `!!binary` 的 Base64 解码失败抛出的是 [IllegalArgumentException]，同样归一为
     * [YamlParseException]。
     */
    private fun <T> load(block: () -> T): T = try {
        block()
    } catch (e: YAMLException) {
        throw YamlParseException("Invalid YAML: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw YamlParseException("Invalid YAML: ${e.message}", e)
    }

    private fun toNode(value: Any?): Node = when (value) {
        null -> NullNode.INSTANCE
        is Node -> value
        is Map<*, *> -> toObjectNode(value)
        is Iterable<*> -> toArrayNode(value)
        is String -> StringNode.of(value)
        is Char -> StringNode.of(value.toString())
        is Boolean -> BooleanNode.of(value)
        is Number -> toNumberNode(value)
        is Date -> StringNode.of(value.toInstant().toString())
        is ByteArray -> BinaryNode.of(value)
        else -> throw YamlParseException("Unsupported YAML value type: ${value.javaClass.name}")
    }

    private fun toObjectNode(value: Map<*, *>): ObjectNode {
        val fields = LinkedHashMap<String, Node>(value.size)
        for ((key, child) in value) {
            fields[toFieldName(key)] = toNode(child)
        }
        return ObjectNode.from(fields)
    }

    private fun toArrayNode(value: Iterable<*>): ArrayNode {
        val elements = ArrayList<Node>()
        for (element in value) {
            elements.add(toNode(element))
        }
        return ArrayNode.from(elements)
    }

    private fun toFieldName(key: Any?): String = when (key) {
        null -> "null"
        is String -> key
        is Number, is Boolean, is Char -> key.toString()
        else -> throw YamlParseException("Unsupported YAML mapping key type: ${key.javaClass.name}")
    }

    private fun toNumberNode(value: Number): NumberNode = when (value) {
        is BigDecimal -> NumberNode.of(value)
        is BigInteger -> NumberNode.of(value)
        is Double -> {
            if (!value.isFinite()) {
                throw YamlParseException("YAML NaN/Infinity numbers are not supported")
            }
            NumberNode.of(value)
        }

        is Float -> {
            if (!value.isFinite()) {
                throw YamlParseException("YAML NaN/Infinity numbers are not supported")
            }
            NumberNode.of(value)
        }

        is Int, is Short, is Byte -> NumberNode.of(value.toLong())
        is Long -> NumberNode.of(value)
        else -> NumberNode.ofRaw(value.toString())
    }
}
