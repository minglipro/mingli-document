package com.mingliqiye.document.format.xml

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
 * 将文档树写成 XML 文本。
 *
 * XML 没有原生类型信息，因此在元素上使用 `type` 与 `nil` 属性保存节点类型：
 * `type="object|array|string|number|boolean"`、`nil="true"`。
 *
 * 启用属性映射（[attributePrefix] 非空）时，对象中以该前缀开头的标量字段写成
 * XML attribute（键名去掉前缀，`type` / `nil` 保留给类型协议，不写出）；
 * `null` 字段被跳过，对象/数组字段抛 [IllegalArgumentException]。
 *
 * 命名空间感知（[namespaceAware]）时元素/属性名允许 `prefix:local` 形式，
 * `xmlns:prefix="uri"` 通过属性字段写出；不做前缀绑定校验，调用方需自行提供声明。
 *
 * @param rootName 根元素名
 * @param attributePrefix 属性字段前缀；`null` 表示不启用属性映射
 * @param namespaceAware 是否启用命名空间感知名称编码
 */
internal class XmlWriter(
    private val rootName: String,
    private val attributePrefix: String? = null,
    private val namespaceAware: Boolean = false,
) {

    init {
        require(attributePrefix?.isEmpty() != true) { "attributePrefix must not be empty" }
    }

    private val builder = StringBuilder()
    private var pretty = false

    fun write(node: Node, pretty: Boolean): String {
        this.pretty = pretty
        builder.setLength(0)
        writeElement(rootName, node, 0)
        return builder.toString()
    }

    private fun writeElement(rawName: String, node: Node, indent: Int) {
        val name = encodeName(rawName)
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
            is BinaryNode -> writeScalar(name, "binary", Base64.getEncoder().encodeToString(node.value), indent)
        }
    }

    private fun writeContainer(name: String, node: ObjectNode, indent: Int) {
        val prefix = attributePrefix
        val attributes = ArrayList<Pair<String, String>>()
        val children = ArrayList<Map.Entry<String, Node>>()
        for (entry in node.entries()) {
            if (prefix != null && entry.key.startsWith(prefix)) {
                val fieldName = entry.key.substring(prefix.length)
                if (fieldName == PROTOCOL_TYPE || fieldName == PROTOCOL_NIL) {
                    continue
                }
                if (fieldName.isEmpty()) {
                    throw IllegalArgumentException("XML attribute field '${entry.key}' has an empty name")
                }
                attributeValue(entry.key, entry.value)?.let { attributes.add(encodeName(fieldName) to it) }
            } else {
                children.add(entry)
            }
        }
        appendIndent(indent)
        builder.append('<').append(name).append(" type=\"object\"")
        for ((fieldName, value) in attributes) {
            builder.append(' ').append(fieldName).append("=\"")
            appendEscapedAttribute(value)
            builder.append('"')
        }
        if (children.isEmpty()) {
            builder.append("/>")
            return
        }
        builder.append('>')
        for (entry in children) {
            appendNewline()
            writeElement(entry.key, entry.value, indent + 1)
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

    /**
     * 读取属性字段的文本值；`null` 返回 `null` 表示跳过该属性。
     *
     * 属性只能是标量，对象/数组字段直接抛 [IllegalArgumentException]。
     */
    private fun attributeValue(key: String, node: Node): String? = when (node) {
        is NullNode -> null
        is StringNode -> node.value
        is NumberNode -> node.raw
        is BooleanNode -> node.value.toString()
        is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
        else -> throw IllegalArgumentException(
            "XML attribute field '$key' must be a scalar node but was ${node.type}",
        )
    }

    private fun encodeName(name: String): String = if (namespaceAware) {
        XmlNames.encodeQualified(name)
    } else {
        XmlNames.encode(name)
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
                '\r' -> builder.append("&#13;")
                else -> builder.append(ch)
            }
        }
    }

    private fun appendEscapedAttribute(value: String) {
        for (ch in value) {
            when (ch) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                '\n' -> builder.append("&#10;")
                '\r' -> builder.append("&#13;")
                '\t' -> builder.append("&#9;")
                else -> builder.append(ch)
            }
        }
    }

    companion object {

        private const val PROTOCOL_TYPE = "type"
        private const val PROTOCOL_NIL = "nil"

        /**
         * 按 XML 配置创建写出器。
         *
         * @param xmlConfig XML 配置；`null` 时使用默认行为
         * @param rootName 根元素名；`null` 时使用配置中的根元素名
         * @return XML 写出器
         */
        fun from(xmlConfig: XmlConfig?, rootName: String? = null): XmlWriter = XmlWriter(
            rootName ?: xmlConfig?.rootName ?: "root",
            xmlConfig?.attributePrefix,
            xmlConfig?.namespaceAware == true,
        )
    }
}

/**
 * 将文档树写成 XML 字符串。
 *
 * @param pretty 是否格式化输出
 * @param rootName 根元素名
 * @param attributePrefix 属性字段前缀；`null` 表示不启用属性映射
 * @param namespaceAware 是否启用命名空间感知名称编码
 * @return XML 文本
 */
public fun Node.toXmlString(
    pretty: Boolean = false,
    rootName: String = "root",
    attributePrefix: String? = null,
    namespaceAware: Boolean = false,
): String = XmlWriter(rootName, attributePrefix, namespaceAware).write(this, pretty)
