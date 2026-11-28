package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.math.BigDecimal
import java.math.BigInteger

/**
 * 将文档树写成 YAML 文本。
 *
 * 由 [toYamlString] 调用；不对外暴露。
 *
 * 写出只根据 [Node] 类型生成标准 YAML，不保留自定义 tag 信息
 * （读取侧 [YamlReader] 的 `allowCustomTags` 只影响解析）。
 *
 * [BinaryNode] 交给 SnakeYAML 的 `byte[]` 表示器写出，因此输出为 `!!binary`
 * Base64 标量（流式格式为双引号标量，块式格式为字面量标量），可被
 * [YamlReader] 还原为 [BinaryNode]。
 */
internal class YamlWriter(
    private val pretty: Boolean,
) {

    /**
     * 写出单个文档并返回 YAML 文本。
     */
    fun write(node: Node): String = createYaml().dump(toPlain(node)).trimEnd()

    /**
     * 写出多个文档并返回 `---` 分隔的 YAML 文本。
     *
     * 单个文档不额外添加起始 `---`；空列表返回空字符串。
     */
    fun writeAll(nodes: Iterable<Node>): String = createYaml().dumpAll(nodes.map(::toPlain).iterator()).trimEnd()

    private fun createYaml(): Yaml {
        val options = DumperOptions()
        options.setAllowUnicode(true)
        options.setIndent(2)
        options.setPrettyFlow(pretty)
        options.setDefaultFlowStyle(
            if (pretty) DumperOptions.FlowStyle.BLOCK else DumperOptions.FlowStyle.FLOW,
        )
        return Yaml(options)
    }

    private fun toPlain(node: Node): Any? = when (node) {
        is ObjectNode -> toPlainObject(node)
        is ArrayNode -> node.map(::toPlain)
        is NumberNode -> toPlainNumber(node)
        is StringNode -> node.value
        is BooleanNode -> node.value
        is BinaryNode -> node.value
        is NullNode -> null
    }

    private fun toPlainObject(node: ObjectNode): Map<String, Any?> {
        val fields = LinkedHashMap<String, Any?>(node.size)
        for ((key, value) in node.asMap()) {
            fields[key] = toPlain(value)
        }
        return fields
    }

    private fun toPlainNumber(node: NumberNode): Number = if (node.raw.contains('.') || node.raw.contains('e', ignoreCase = true)) {
        BigDecimal(node.raw)
    } else {
        BigInteger(node.raw)
    }
}

/**
 * 将文档树写成 YAML 字符串。
 *
 * @param pretty 是否使用块格式输出；默认使用流式紧凑格式
 * @return YAML 文本
 */
public fun Node.toYamlString(pretty: Boolean = false): String = YamlWriter(pretty).write(this)
