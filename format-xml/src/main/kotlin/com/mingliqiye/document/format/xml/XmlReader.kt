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
 * 启用属性映射（[attributePrefix] 非空）时，XML attribute 映射为对象字段
 * `attributePrefix + 名称`；`type` / `nil` 协议属性保留给类型协议，不映射为字段。
 * 带属性的标量元素包装为 `{ 属性字段..., textKey: 标量 }` 对象，
 * 带属性的数组元素包装为 `{ 属性字段..., itemsKey: 数组 }` 对象；
 * 无属性的标量/数组保持原有节点类型。
 *
 * 命名空间模式为 [XmlNamespaceMode.CLARK] 时，元素/属性名用 `{uri}local` 表示，
 * `xmlns` 声明不映射为字段（URI 已内嵌在名称中）；[XmlNamespaceMode.PREFIX] 模式
 * 继续保留 `prefix:local` 与 `xmlns` 属性字段。CLARK 模式要求 [namespaceAware] 为 `true`。
 *
 * 混合内容（直接文本 + 子元素）按 [mixedContentPolicy] 处理：PRESERVE 时直接文本
 * 首尾裁剪后写入 [textKey] 字段（与子元素相对顺序不保留），REJECT 时抛 [XmlParseException]。
 * 只有空白直接文本视为格式化缩进，不产生文本字段。
 *
 * @param source XML 文本
 * @param attributePrefix 属性字段前缀；`null` 表示读取时忽略 XML attribute
 * @param namespaceAware 是否启用命名空间感知
 * @param namespaceMode 命名空间名称表示模式
 * @param textKey 文本字段名，默认 `#text`
 * @param itemsKey 带属性数组的数组字段名，默认 `#items`
 * @param mixedContentPolicy 混合内容策略
 */
public class XmlReader(
    private val source: String,
    private val attributePrefix: String? = null,
    private val namespaceAware: Boolean = false,
    private val namespaceMode: XmlNamespaceMode = XmlNamespaceMode.PREFIX,
    private val textKey: String = XmlConfig.DEFAULT_TEXT_KEY,
    private val itemsKey: String = XmlConfig.DEFAULT_ITEMS_KEY,
    private val mixedContentPolicy: XmlMixedContentPolicy = XmlMixedContentPolicy.PRESERVE,
) {

    init {
        require(attributePrefix?.isEmpty() != true) { "attributePrefix must not be empty" }
        require(textKey.isNotEmpty()) { "textKey must not be empty" }
        require(itemsKey.isNotEmpty()) { "itemsKey must not be empty" }
        require(textKey != itemsKey) { "textKey and itemsKey must be different" }
        require(
            attributePrefix == null ||
                (!textKey.startsWith(attributePrefix) && !itemsKey.startsWith(attributePrefix)),
        ) { "textKey and itemsKey must not start with attributePrefix" }
        require(namespaceMode != XmlNamespaceMode.CLARK || namespaceAware) {
            "CLARK namespace mode requires namespaceAware"
        }
    }

    /**
     * 解析并返回文档树。
     *
     * @throws XmlParseException XML 格式非法或混合内容被拒绝时抛出
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
        val attributes = attributeFields(element)
        if (element.getAttribute(PROTOCOL_NIL) == "true") {
            return wrapScalar(element, attributes, NullNode.INSTANCE)
        }
        return when (element.getAttribute(PROTOCOL_TYPE)) {
            "object" -> toObjectNode(element, attributes)
            "array" -> toArrayNode(element, attributes)
            "string" -> wrapScalar(element, attributes, StringNode.of(element.textContent))
            "number" -> wrapScalar(element, attributes, toNumberNode(element.textContent))
            "boolean" -> wrapScalar(element, attributes, toBooleanNode(element.textContent))
            "binary" -> wrapScalar(element, attributes, BinaryNode.of(Base64.getDecoder().decode(element.textContent.trim())))
            else -> inferNode(element, attributes)
        }
    }

    /**
     * 属性 + 标量文本包装；没有属性时保持标量节点原样。
     */
    private fun wrapScalar(
        element: Element,
        attributes: List<Pair<String, Node>>,
        scalar: Node,
    ): Node {
        if (attributes.isEmpty()) {
            return scalar
        }
        val fields = LinkedHashMap<String, Node>(attributes.size + 1)
        for ((name, value) in attributes) {
            fields[name] = value
        }
        fields[textKey] = scalar
        return ObjectNode.from(fields)
    }

    private fun inferNode(element: Element, attributes: List<Pair<String, Node>>): Node {
        val children = childElements(element)
        return when {
            children.isEmpty() && attributes.isEmpty() -> inferScalar(element.textContent)
            children.isEmpty() -> {
                val fields = LinkedHashMap<String, Node>(attributes.size + 1)
                for ((name, value) in attributes) {
                    fields[name] = value
                }
                val text = element.textContent
                if (text.isNotEmpty()) {
                    fields[textKey] = inferScalar(text)
                }
                ObjectNode.from(fields)
            }

            children.all { elementName(it) == ARRAY_ITEM_NAME } -> toArrayNode(element, attributes)
            else -> toObjectNode(element, attributes)
        }
    }

    private fun toObjectNode(element: Element, attributes: List<Pair<String, Node>>): ObjectNode {
        val children = childElements(element)
        val fields = LinkedHashMap<String, Node>(attributes.size + children.size + 1)
        for ((name, value) in attributes) {
            fields[name] = value
        }
        val grouped = LinkedHashMap<String, MutableList<Node>>(children.size)
        for (child in children) {
            val name = elementName(child)
            grouped.getOrPut(name) { ArrayList() }.add(toNode(child))
        }
        for ((name, values) in grouped) {
            fields[name] = if (values.size == 1) {
                values[0]
            } else {
                ArrayNode.from(values)
            }
        }
        containerText(element, children)?.let { fields[textKey] = it }
        return ObjectNode.from(fields)
    }

    private fun toArrayNode(element: Element, attributes: List<Pair<String, Node>>): Node {
        val children = childElements(element)
        val values = ArrayList<Node>(children.size)
        for (child in children) {
            values.add(toNode(child))
        }
        val text = containerText(element, children)
        if (attributes.isEmpty() && text == null) {
            return ArrayNode.from(values)
        }
        val fields = LinkedHashMap<String, Node>(attributes.size + 2)
        for ((name, value) in attributes) {
            fields[name] = value
        }
        fields[itemsKey] = ArrayNode.from(values)
        text?.let { fields[textKey] = it }
        return ObjectNode.from(fields)
    }

    /**
     * 计算容器元素的文本字段；混合内容为空/仅空白时返回 `null`。
     *
     * @throws XmlParseException 混合内容策略为 REJECT 且存在非空白文本时抛出
     */
    private fun containerText(element: Element, children: List<Element>): StringNode? {
        if (children.isEmpty()) {
            val text = element.textContent
            return if (text.isEmpty()) null else StringNode.of(text)
        }
        val text = directText(element).trim()
        if (text.isEmpty()) {
            return null
        }
        if (mixedContentPolicy == XmlMixedContentPolicy.REJECT) {
            throw XmlParseException("Mixed content is not supported in element '${displayName(element)}'")
        }
        return StringNode.of(text)
    }

    /**
     * 收集对象元素上的 XML attribute 字段。
     *
     * `type` / `nil` 是类型协议属性，不映射为普通字段；Clark 模式下跳过 `xmlns` 声明，
     * 其余属性按 `attributePrefix + 名称` 映射。
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
            val keyName = if (namespaceMode == XmlNamespaceMode.CLARK) {
                if (attribute.namespaceURI == XMLNS_NAMESPACE) {
                    continue
                }
                XmlNames.clark(attribute.namespaceURI, XmlNames.decode(attribute.localName ?: name))
            } else {
                XmlNames.decode(name)
            }
            result.add(prefix + keyName to inferScalar(attribute.value))
        }
        return result
    }

    /**
     * 元素在 Node 中的字段名。
     */
    private fun elementName(element: Element): String = if (namespaceMode == XmlNamespaceMode.CLARK) {
        XmlNames.clark(
            element.namespaceURI,
            XmlNames.decode(element.localName ?: element.nodeName),
        )
    } else {
        XmlNames.decode(element.nodeName)
    }

    private fun displayName(element: Element): String = elementName(element)

    /**
     * 元素直接子文本节点（不含后代元素文本）。
     */
    private fun directText(element: Element): String {
        val children = element.childNodes
        val builder = StringBuilder()
        for (index in 0 until children.length) {
            val child = children.item(index)
            val isText = child.nodeType == org.w3c.dom.Node.TEXT_NODE ||
                child.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE
            if (isText) {
                builder.append(child.nodeValue)
            }
        }
        return builder.toString()
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
        private const val ARRAY_ITEM_NAME = "item"
        private const val XMLNS_NAMESPACE = "http://www.w3.org/2000/xmlns/"

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
            xmlConfig?.namespaceMode ?: XmlNamespaceMode.PREFIX,
            xmlConfig?.textKey ?: XmlConfig.DEFAULT_TEXT_KEY,
            xmlConfig?.itemsKey ?: XmlConfig.DEFAULT_ITEMS_KEY,
            xmlConfig?.mixedContentPolicy ?: XmlMixedContentPolicy.PRESERVE,
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
