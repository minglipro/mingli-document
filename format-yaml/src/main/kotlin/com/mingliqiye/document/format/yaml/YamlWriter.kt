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
import java.util.Base64

/**
 * 将文档树写成 YAML 文本。
 *
 * 由 [toYamlString] 调用；不对外暴露。
 */
internal class YamlWriter(
    private val pretty: Boolean,
) {

    /**
     * 写出节点并返回 YAML 文本。
     */
    fun write(node: Node): String {
        val options = DumperOptions()
        options.setAllowUnicode(true)
        options.setIndent(2)
        options.setPrettyFlow(pretty)
        options.setDefaultFlowStyle(
            if (pretty) DumperOptions.FlowStyle.BLOCK else DumperOptions.FlowStyle.FLOW,
        )
        return Yaml(options).dump(toPlain(node)).trimEnd()
    }

    private fun toPlain(node: Node): Any? = when (node) {
        is ObjectNode -> toPlainObject(node)
        is ArrayNode -> node.map(::toPlain)
        is NumberNode -> toPlainNumber(node)
        is StringNode -> node.value
        is BooleanNode -> node.value
        is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
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
