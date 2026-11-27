package com.mingliqiye.document.format.xml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode

/**
 * 将文档树写成 XML 文本。
 *
 * XML 没有原生类型信息，因此在元素上使用 `type` 与 `nil` 属性保存节点类型：
 * `type="object|array|string|number|boolean"`、`nil="true"`。
 *
 * @param rootName 根元素名
 */
internal class XmlWriter(
    private val rootName: String,
) {

    private val builder = StringBuilder()
    private var pretty = false

    fun write(node: Node, pretty: Boolean): String {
        this.pretty = pretty
        builder.setLength(0)
        writeElement(XmlNames.encode(rootName), node, 0)
        return builder.toString()
    }

    private fun writeElement(name: String, node: Node, indent: Int) {
        when (node) {
            is NullNode -> {
                appendIndent(indent)
                builder.append('<').append(name).append(" nil=\"true\"/>")
            }
            is ObjectNode -> writeContainer(name, node, indent)
            is ArrayNode -> writeArray(name, node, indent)
            is StringNode -> writeScalar(name, "string", node.value, indent)
            is NumberNode -> writeScalar(name, "number", node.raw, indent)
            is BooleanNode -> writeScalar(name, "boolean", node.value.toString(), indent)
        }
    }

    private fun writeContainer(name: String, node: ObjectNode, indent: Int) {
        appendIndent(indent)
        builder.append('<').append(name).append(" type=\"object\"")
        if (node.size == 0) {
            builder.append("/>")
            return
        }
        builder.append('>')
        for ((key, child) in node.asMap()) {
            appendNewline()
            writeElement(XmlNames.encode(key), child, indent + 1)
        }
        appendNewline()
        appendIndent(indent)
        builder.append("</").append(name).append('>')
    }

    private fun writeArray(name: String, node: ArrayNode, indent: Int) {
        appendIndent(indent)
        builder.append('<').append(name).append(" type=\"array\"")
        if (node.size == 0) {
            builder.append("/>")
            return
        }
        builder.append('>')
        for (child in node) {
            appendNewline()
            writeElement("item", child, indent + 1)
        }
        appendNewline()
        appendIndent(indent)
        builder.append("</").append(name).append('>')
    }

    private fun writeScalar(name: String, type: String, value: String, indent: Int) {
        appendIndent(indent)
        builder.append('<').append(name).append(" type=\"").append(type).append("\">")
        appendEscapedText(value)
        builder.append("</").append(name).append('>')
    }

    private fun appendIndent(indent: Int) {
        if (pretty) {
            repeat(indent) { builder.append("  ") }
        }
    }

    private fun appendNewline() {
        if (pretty) {
            builder.append('\n')
        }
    }

    private fun appendEscapedText(value: String) {
        for (ch in value) {
            when (ch) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                else -> builder.append(ch)
            }
        }
    }
}

/**
 * 将文档树写成 XML 字符串。
 *
 * @param pretty 是否格式化输出
 * @param rootName 根元素名
 * @return XML 文本
 */
public fun Node.toXmlString(
    pretty: Boolean = false,
    rootName: String = "root",
): String = XmlWriter(rootName).write(this, pretty)
