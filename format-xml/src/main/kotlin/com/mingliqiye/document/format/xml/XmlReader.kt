package com.mingliqiye.document.format.xml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.xml.exception.XmlParseException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.math.BigDecimal
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XML 读取入口。
 *
 * 识别 [XmlWriter] 生成的 `type` / `nil` 属性；没有类型属性时按元素结构推断。
 *
 * @param source XML 文本
 */
public class XmlReader(
    private val source: String,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws XmlParseException XML 格式非法时抛出
     */
    public fun read(): Node {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (e: Exception) {
            throw XmlParseException("Cannot configure XML parser securely", e)
        }

        val document = try {
            factory.newDocumentBuilder().parse(InputSource(StringReader(source)))
        } catch (e: Exception) {
            throw XmlParseException("Invalid XML: ${e.message}", e)
        }
        val root = document.documentElement ?: throw XmlParseException("XML document has no root element")
        return toNode(root)
    }

    private fun toNode(element: Element): Node {
        if (element.getAttribute("nil") == "true") {
            return NullNode.INSTANCE
        }
        return when (element.getAttribute("type")) {
            "object" -> toObjectNode(element)
            "array" -> toArrayNode(element)
            "string" -> StringNode.of(element.textContent)
            "number" -> toNumberNode(element.textContent)
            "boolean" -> toBooleanNode(element.textContent)
            "binary" -> BinaryNode.of(Base64.getDecoder().decode(element.textContent.trim()))
            else -> inferNode(element)
        }
    }

    private fun inferNode(element: Element): Node {
        val children = childElements(element)
        return when {
            children.isEmpty() -> inferScalar(element.textContent)
            children.all { it.nodeName == "item" } -> toArrayNode(element)
            else -> toObjectNode(element)
        }
    }

    private fun toObjectNode(element: Element): ObjectNode {
        val children = childElements(element)
        val grouped = LinkedHashMap<String, MutableList<Node>>(children.size)
        for (child in children) {
            val name = XmlNames.decode(child.nodeName)
            grouped.getOrPut(name) { ArrayList() }.add(toNode(child))
        }
        val fields = LinkedHashMap<String, Node>(grouped.size)
        for ((name, values) in grouped) {
            fields[name] = if (values.size == 1) {
                values[0]
            } else {
                ArrayNode.from(values)
            }
        }
        return ObjectNode.from(fields)
    }

    private fun toArrayNode(element: Element): ArrayNode {
        val children = childElements(element)
        val values = ArrayList<Node>(children.size)
        for (child in children) {
            values.add(toNode(child))
        }
        return ArrayNode.from(values)
    }

    private fun inferScalar(text: String): Node {
        val trimmed = text.trim()
        return when {
            trimmed == "true" -> BooleanNode.of(true)
            trimmed == "false" -> BooleanNode.of(false)
            isNumber(trimmed) -> NumberNode.ofRaw(trimmed)
            else -> StringNode.of(text)
        }
    }

    private fun toNumberNode(text: String): NumberNode {
        val raw = text.trim()
        if (!isNumber(raw)) {
            throw XmlParseException("Invalid number '$raw'")
        }
        return NumberNode.ofRaw(raw)
    }

    private fun toBooleanNode(text: String): BooleanNode = when (text.trim()) {
        "true" -> BooleanNode.of(true)
        "false" -> BooleanNode.of(false)
        else -> throw XmlParseException("Invalid boolean '${text.trim()}'")
    }

    private fun isNumber(text: String): Boolean = try {
        BigDecimal(text)
        true
    } catch (_: NumberFormatException) {
        false
    }

    private fun childElements(element: Element): List<Element> {
        val children = element.childNodes
        val result = ArrayList<Element>(children.length)
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                result.add(child)
            }
        }
        return result
    }

    public companion object {

        /**
         * 解析 XML 字符串。
         *
         * @param source XML 文本
         * @return 文档树
         * @throws XmlParseException XML 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = XmlReader(source).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @return 文档树
 * @throws XmlParseException XML 格式非法时抛出
 */
public fun String.toXmlNode(): Node = XmlReader(this).read()
