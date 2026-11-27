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

private const val HEX_DIGITS = "0123456789abcdef"

/**
 * 将 JSON 树序列化为文本。
 *
 * 由 [toJsonString] 调用；不对外暴露。
 */
internal class JsonWriter(
    private val pretty: Boolean,
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
            is NumberNode -> builder.append(node.raw)
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
            appendString(key)
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

    private fun appendString(value: String) {
        builder.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (ch < ' ') {
                        appendUnicodeEscape(ch)
                    } else {
                        builder.append(ch)
                    }
                }
            }
        }
        builder.append('"')
    }

    private fun appendUnicodeEscape(ch: Char) {
        builder.append("\\u")
        val code = ch.code
        for (shift in 12 downTo 0 step 4) {
            builder.append(HEX_DIGITS[(code shr shift) and 0xF])
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
 * @return JSON 文本
 */
public fun Node.toJsonString(pretty: Boolean = false): String = JsonWriter(pretty).write(this)
