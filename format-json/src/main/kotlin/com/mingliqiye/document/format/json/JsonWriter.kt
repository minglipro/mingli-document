package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import java.util.Base64

/**
 * 将 JSON 树序列化为文本。
 *
 * 输出行为由 [JsonWriteOptions] 控制（引号字段名、数字转字符串、非 ASCII 转义等）。
 * 由 [toJsonString] 调用；不对外暴露。
 *
 * @param pretty 是否使用两空格缩进的格式化输出
 * @param options 写出特性
 */
internal class JsonWriter(
    private val pretty: Boolean,
    private val options: JsonWriteOptions = JsonWriteOptions.DEFAULT,
) {

    private val builder = StringBuilder()

    /**
     * 写出节点并返回 JSON 文本。
     */
    fun write(node: Node): String {
        appendNode(node, 0)
        return builder.toString()
    }

    private fun appendNode(node: Node, indent: Int) {
        when (node) {
            is ObjectNode -> appendObject(node, indent)
            is ArrayNode -> appendArray(node, indent)
            is NumberNode -> appendNumber(node.raw)
            is StringNode -> appendString(node.value)
            is BooleanNode -> builder.append(if (node.value) "true" else "false")
            is BinaryNode -> appendString(Base64.getEncoder().encodeToString(node.value))
            is NullNode -> builder.append("null")
        }
    }

    private fun appendObject(node: ObjectNode, indent: Int) {
        if (node.size == 0) {
            builder.append("{}")
            return
        }
        builder.append('{')
        var first = true
        for ((key, value) in node.asMap()) {
            if (!first) {
                builder.append(',')
            }
            first = false
            if (pretty) {
                appendNewline(indent + 1)
            }
            appendFieldName(key)
            builder.append(if (pretty) ": " else ":")
            appendNode(value, indent + 1)
        }
        if (pretty) {
            appendNewline(indent)
        }
        builder.append('}')
    }

    private fun appendArray(node: ArrayNode, indent: Int) {
        if (node.size == 0) {
            builder.append("[]")
            return
        }
        builder.append('[')
        var first = true
        for (element in node) {
            if (!first) {
                builder.append(',')
            }
            first = false
            if (pretty) {
                appendNewline(indent + 1)
            }
            appendNode(element, indent + 1)
        }
        if (pretty) {
            appendNewline(indent)
        }
        builder.append(']')
    }

    private fun appendFieldName(name: String) {
        if (options.quoteFieldNames) {
            appendString(name)
        } else {
            builder.append(name)
        }
    }

    private fun appendString(value: String) {
        builder.append('"')
        if (options.needsEscape(value)) {
            builder.append(options.escape(value))
        } else {
            builder.append(value)
        }
        builder.append('"')
    }

    private fun appendNumber(raw: String) {
        val text = options.numberLiteral(raw)
        if (options.shouldQuoteNumber(text)) {
            appendString(text)
        } else {
            builder.append(text)
        }
    }

    private fun appendNewline(indent: Int) {
        builder.append('\n')
        repeat(indent) {
            builder.append("  ")
        }
    }
}

/**
 * 将 JSON 树序列化为 JSON 字符串。
 *
 * @param pretty 是否使用两空格缩进的格式化输出
 * @param writeFeatures JSON 写出特性；默认与 [JsonConfig.DEFAULT_WRITE_FEATURES] 一致
 * @return JSON 文本
 */
public fun Node.toJsonString(
    pretty: Boolean = false,
    writeFeatures: Set<JsonWriteFeature> = JsonConfig.DEFAULT_WRITE_FEATURES,
): String = JsonWriter(pretty, JsonWriteOptions(writeFeatures)).write(this)
