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
import java.math.BigDecimal
import java.util.ArrayDeque
import java.util.Base64

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
 * 混合内容（直接文本 + 子元素）按 [mixedContentPolicy] 处理：
 * - [XmlMixedContentPolicy.PRESERVE]：直接文本首尾裁剪后写入 [textKey] 字段（顺序不保留）；
 * - [XmlMixedContentPolicy.PRESERVE_ORDERED]：在 [textKey] 兼容视图之外，额外按
 *   `childNodes` 顺序输出 [contentKey] 有序数组（文本项为 `StringNode`，子元素项为
 *   单字段对象 `{元素名: 节点}`；相邻文本节点合并、纯空白文本忽略）；
 * - [XmlMixedContentPolicy.REJECT]：抛 [XmlParseException]。
 *
 * @param source XML 文本
 * @param attributePrefix 属性字段前缀；`null` 表示读取时忽略 XML attribute
 * @param namespaceAware 是否启用命名空间感知
 * @param namespaceMode 命名空间名称表示模式
 * @param textKey 文本字段名，默认 `#text`
 * @param itemsKey 带属性数组的数组字段名，默认 `#items`
 * @param mixedContentPolicy 混合内容策略
 * @param contentKey 有序混合内容的数组字段名，默认 `#content`
 * @param maxDepth 最大 XML 元素嵌套深度（根元素深度为 `1`），默认
 *   [XmlConfig.DEFAULT_MAX_DEPTH]；`0` 或负数表示不限制（显式关闭保护）
 */
public class XmlReader(
    private val source: String,
    private val attributePrefix: String? = null,
    private val namespaceAware: Boolean = false,
    private val namespaceMode: XmlNamespaceMode = XmlNamespaceMode.PREFIX,
    private val textKey: String = XmlConfig.DEFAULT_TEXT_KEY,
    private val itemsKey: String = XmlConfig.DEFAULT_ITEMS_KEY,
    private val mixedContentPolicy: XmlMixedContentPolicy = XmlMixedContentPolicy.PRESERVE,
    private val contentKey: String = XmlConfig.DEFAULT_CONTENT_KEY,
    private val maxDepth: Int = XmlConfig.DEFAULT_MAX_DEPTH,
) {

    init {
        require(attributePrefix?.isEmpty() != true) { "attributePrefix must not be empty" }
        require(textKey.isNotEmpty()) { "textKey must not be empty" }
        require(itemsKey.isNotEmpty()) { "itemsKey must not be empty" }
        require(contentKey.isNotEmpty()) { "contentKey must not be empty" }
        require(textKey != itemsKey && textKey != contentKey && itemsKey != contentKey) {
            "textKey, itemsKey and contentKey must be different"
        }
        require(
            attributePrefix == null ||
                (
                    !textKey.startsWith(attributePrefix) &&
                        !itemsKey.startsWith(attributePrefix) &&
                        !contentKey.startsWith(attributePrefix)
                    ),
        ) { "textKey, itemsKey and contentKey must not start with attributePrefix" }
        require(namespaceMode != XmlNamespaceMode.CLARK || namespaceAware) {
            "CLARK namespace mode requires namespaceAware"
        }
    }

    /**
     * 解析并返回文档树。
     *
     * @throws XmlParseException XML 格式非法、混合内容被拒绝或元素嵌套深度超过
     *   [maxDepth] 时抛出
     */
    public fun read(): Node {
        val root = XmlDom.parse(source, namespaceAware)
        checkDepth(root, maxDepth)
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
        val fields = LinkedHashMap<String, Node>(attributes.size + children.size + 2)
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
        orderedContent(element, children, forceItemName = false)?.let { fields[contentKey] = it }
        return ObjectNode.from(fields)
    }

    private fun toArrayNode(element: Element, attributes: List<Pair<String, Node>>): Node {
        val children = childElements(element)
        val values = ArrayList<Node>(children.size)
        for (child in children) {
            values.add(toNode(child))
        }
        val text = containerText(element, children)
        val ordered = orderedContent(element, children, forceItemName = true)
        if (attributes.isEmpty() && text == null && ordered == null) {
            return ArrayNode.from(values)
        }
        val fields = LinkedHashMap<String, Node>(attributes.size + 3)
        for ((name, value) in attributes) {
            fields[name] = value
        }
        fields[itemsKey] = ArrayNode.from(values)
        text?.let { fields[textKey] = it }
        ordered?.let { fields[contentKey] = it }
        return ObjectNode.from(fields)
    }

    /**
     * 按 `childNodes` 顺序生成有序混合内容数组；非混合内容或策略未开启时返回 `null`。
     *
     * 相邻文本/CDATA 节点合并为一个文本项，纯空白文本项忽略（视为格式化缩进）；
     * 子元素项为单字段对象 `{元素名: 节点}`，[forceItemName] 为 `true` 时统一用 `item`
     * （数组容器的子元素与元素名无关，写出时固定写回 `<item>`）。
     */
    private fun orderedContent(
        element: Element,
        children: List<Element>,
        forceItemName: Boolean,
    ): ArrayNode? {
        if (mixedContentPolicy != XmlMixedContentPolicy.PRESERVE_ORDERED) {
            return null
        }
        if (children.isEmpty() || directText(element).isBlank()) {
            return null
        }
        val entries = ArrayList<Node>(children.size * 2 + 1)
        val childNodes = element.childNodes
        val text = StringBuilder()
        for (index in 0 until childNodes.length) {
            val child = childNodes.item(index)
            when {
                isTextNode(child) -> text.append(child.nodeValue)
                child is Element -> {
                    flushTextItem(text, entries)
                    val name = if (forceItemName) ARRAY_ITEM_NAME else elementName(child)
                    entries.add(ObjectNode.of(name to toNode(child)))
                }
            }
        }
        flushTextItem(text, entries)
        return ArrayNode.from(entries)
    }

    private fun flushTextItem(text: StringBuilder, entries: MutableList<Node>) {
        if (text.isEmpty()) {
            return
        }
        val value = text.toString()
        text.setLength(0)
        if (value.isNotBlank()) {
            entries.add(StringNode.of(value))
        }
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
            if (isTextNode(child)) {
                builder.append(child.nodeValue)
            }
        }
        return builder.toString()
    }

    private fun isTextNode(node: org.w3c.dom.Node): Boolean = node.nodeType == org.w3c.dom.Node.TEXT_NODE || node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE

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
         * 迭代校验 DOM 元素嵌套深度（根元素深度为 `1`），超限抛 [XmlParseException]。
         *
         * 使用显式栈而不是递归，保证超深输入不会 StackOverflow；供 [XmlReader] 与
         * [XmlTokenReader] 共用。[maxDepth] 小于等于 `0` 时不做限制（显式关闭保护）。
         */
        internal fun checkDepth(root: Element, maxDepth: Int) {
            if (maxDepth <= 0) {
                return
            }
            val stack = ArrayDeque<Pair<org.w3c.dom.Node, Int>>()
            stack.addLast(root to 1)
            while (stack.isNotEmpty()) {
                val (current, depth) = stack.removeLast()
                if (depth > maxDepth) {
                    throw XmlParseException("XML nesting depth $depth exceeds maxDepth $maxDepth")
                }
                val children = current.childNodes
                for (index in 0 until children.length) {
                    val child = children.item(index)
                    if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                        stack.addLast(child to depth + 1)
                    }
                }
            }
        }

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
        internal fun from(source: String, xmlConfig: XmlConfig?, maxDepth: Int = XmlConfig.DEFAULT_MAX_DEPTH): XmlReader = XmlReader(
            source,
            xmlConfig?.attributePrefix,
            xmlConfig?.namespaceAware == true,
            xmlConfig?.namespaceMode ?: XmlNamespaceMode.PREFIX,
            xmlConfig?.textKey ?: XmlConfig.DEFAULT_TEXT_KEY,
            xmlConfig?.itemsKey ?: XmlConfig.DEFAULT_ITEMS_KEY,
            xmlConfig?.mixedContentPolicy ?: XmlMixedContentPolicy.PRESERVE,
            xmlConfig?.contentKey ?: XmlConfig.DEFAULT_CONTENT_KEY,
            maxDepth,
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
