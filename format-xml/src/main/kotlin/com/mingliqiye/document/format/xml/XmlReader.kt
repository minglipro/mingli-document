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
import org.w3c.dom.Attr
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
 * 启用属性映射（[attributePrefix] 非空）时：
 * - 对象元素的 XML attribute 映射为对象字段，键名为 `attributePrefix + 属性名`；
 * - `type` / `nil` 协议属性保留给类型协议，不映射为字段；
 * - 属性值没有类型标记，按 `true` / `false` / 数字 / 字符串顺序推断；
 * - 命名空间感知（[namespaceAware]）时属性名保留 `prefix:local` 形式，
 *   `xmlns` 声明同样作为属性字段保留，命名空间用“前缀限定名 + `xmlns` 字段”表示。
 *
 * 只有对象节点承载属性：标量节点与数组节点上的 XML attribute 仍会被忽略。
 * 混合内容沿用原有推断行为：元素同时包含文本与子元素时按对象/数组处理并忽略文本。
 * 命名空间感知下若 [attributePrefix] 为 `null`，则 `xmlns` 声明无法保留。
 *
 * @param source XML 文本
 * @param attributePrefix 属性字段前缀；`null` 表示读取时忽略 XML attribute
 * @param namespaceAware 是否启用命名空间感知
 */
public class XmlReader(
    private val source: String,
    private val attributePrefix: String? = null,
    private val namespaceAware: Boolean = false,
) {

    init {
        require(attributePrefix?.isEmpty() != true) { "attributePrefix must not be empty" }
    }

    /**
     * 解析并返回文档树。
     *
     * @throws XmlParseException XML 格式非法时抛出
     */
    public fun read(): Node {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = namespaceAware
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
        if (element.getAttribute(PROTOCOL_NIL) == "true") {
            return NullNode.INSTANCE
        }
        return when (element.getAttribute(PROTOCOL_TYPE)) {
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
        val attributes = attributeFields(element)
        return when {
            children.isEmpty() && attributes.isEmpty() -> inferScalar(element.textContent)
            children.isNotEmpty() && children.all { it.nodeName == "item" } -> toArrayNode(element)
            else -> toObjectNode(element, attributes)
        }
    }

    private fun toObjectNode(element: Element): ObjectNode = toObjectNode(element, attributeFields(element))

    private fun toObjectNode(element: Element, attributes: List<Pair<String, Node>>): ObjectNode {
        val children = childElements(element)
        val fields = LinkedHashMap<String, Node>(attributes.size + children.size)
        for ((name, value) in attributes) {
            fields[name] = value
        }
        val grouped = LinkedHashMap<String, MutableList<Node>>(children.size)
        for (child in children) {
            val name = XmlNames.decode(child.nodeName)
            grouped.getOrPut(name) { ArrayList() }.add(toNode(child))
        }
        for ((name, values) in grouped) {
            fields[name] = if (values.size == 1) {
                values[0]
            } else {
                ArrayNode.from(values)
            }
        }
        return ObjectNode.from(fields)
    }

    /**
     * 收集对象元素上的 XML attribute 字段。
     *
     * `type` / `nil` 是类型协议属性，不映射为普通字段；其余属性（含命名空间感知下的
     * `xmlns` 声明）按文档顺序映射为 `attributePrefix + 限定名`。
     */
    private fun attributeFields(element: Element): List<Pair<String, Node>> {
        val prefix = attributePrefix ?: return emptyList()
        val attributes = element.attributes
        val result = ArrayList<Pair<String, Node>>(attributes.length)
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index) as? Attr ?: continue
            val name = attribute.name
            if (name == PROTOCOL_TYPE || name == PROTOCOL_NIL) {
                continue
            }
            result.add(prefix + XmlNames.decode(name) to inferScalar(attribute.value))
        }
        return result
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

        private const val PROTOCOL_TYPE = "type"
        private const val PROTOCOL_NIL = "nil"

        /**
         * 解析 XML 字符串。
         *
         * @param source XML 文本
         * @return 文档树
         * @throws XmlParseException XML 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = XmlReader(source).read()

        /**
         * 按 XML 配置创建读取器。
         *
         * @param source XML 文本
         * @param xmlConfig XML 配置；`null` 时使用默认行为
         * @return XML 读取器
         */
        internal fun from(source: String, xmlConfig: XmlConfig?): XmlReader = XmlReader(
            source,
            xmlConfig?.attributePrefix,
            xmlConfig?.namespaceAware == true,
        )
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @return 文档树
 * @throws XmlParseException XML 格式非法时抛出
 */
public fun String.toXmlNode(): Node = XmlReader(this).read()
