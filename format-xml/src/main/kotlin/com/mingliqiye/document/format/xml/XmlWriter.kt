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
 * XML attribute（键名去掉前缀，`type` / `nil` 保留给类型协议，不写出）。
 *
 * 包装结构（属性与容器合并）：
 * - 标量属性包装：对象所有字段都是属性字段、且 [textKey] 为标量 → 写成带属性的标量元素；
 * - 数组属性包装：对象包含 [itemsKey] 数组字段，其余字段为属性字段或 [textKey] 文本
 *   → 写成带属性的数组元素；
 * - 普通对象含 [textKey] 标量字段 → 视为混合内容，文本写在子元素之前。
 * `null` 属性被跳过，对象/数组属性字段抛 [IllegalArgumentException]。
 *
 * 命名空间模式为 [XmlNamespaceMode.CLARK] 时，字段名使用 `{uri}local`：
 * 写出时按首次出现顺序分配 `ns0`、`ns1`... 前缀，并在根元素生成 `xmlns:nsN` 声明。
 * [XmlNamespaceMode.PREFIX] 模式继续使用 `prefix:local` 与显式 `xmlns` 属性字段。
 *
 * @param rootName 根元素名
 * @param attributePrefix 属性字段前缀；`null` 表示不启用属性映射
 * @param namespaceAware 是否启用前缀限定名编码（仅 [XmlNamespaceMode.PREFIX] 生效）
 * @param namespaceMode 命名空间名称表示模式
 * @param textKey 文本字段名
 * @param itemsKey 带属性数组的数组字段名
 */
internal class XmlWriter(
    private val rootName: String,
    private val attributePrefix: String? = null,
    private val namespaceAware: Boolean = false,
    private val namespaceMode: XmlNamespaceMode = XmlNamespaceMode.PREFIX,
    private val textKey: String = XmlConfig.DEFAULT_TEXT_KEY,
    private val itemsKey: String = XmlConfig.DEFAULT_ITEMS_KEY,
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
    }

    private val builder = StringBuilder()
    private val namespacePrefixes = LinkedHashMap<String, String>()
    private var pretty = false

    fun write(node: Node, pretty: Boolean): String {
        this.pretty = pretty
        builder.setLength(0)
        namespacePrefixes.clear()
        if (namespaceMode == XmlNamespaceMode.CLARK) {
            collectNamespaces(rootName, node)
        }
        writeElement(rootName, node, 0, isRoot = true)
        return builder.toString()
    }

    private fun writeElement(rawName: String, node: Node, indent: Int, isRoot: Boolean) {
        if (node is ObjectNode) {
            if (isScalarWrapper(node)) {
                writeScalarWrapper(rawName, node, indent, isRoot)
                return
            }
            if (isArrayWrapper(node)) {
                writeArrayWrapper(rawName, node, indent, isRoot)
                return
            }
        }
        val name = encodeName(rawName)
        when (node) {
            is NullNode -> {
                appendIndent(indent)
                appendStart(name, isRoot)
                builder.append(" nil=\"true\"/>")
            }

            is ObjectNode -> writeContainer(name, node, indent, isRoot)
            is ArrayNode -> writeArray(name, node, indent, isRoot)
            is StringNode -> writeScalar(name, "string", node.value, indent, isRoot)
            is NumberNode -> writeScalar(name, "number", node.raw, indent, isRoot)
            is BooleanNode -> writeScalar(name, "boolean", node.value.toString(), indent, isRoot)
            is BinaryNode -> writeScalar(name, "binary", Base64.getEncoder().encodeToString(node.value), indent, isRoot)
        }
    }

    /**
     * 写出标量属性包装：`{ 属性字段..., textKey: 标量 }` → `<name type="..." 属性...>文本</name>`。
     */
    private fun writeScalarWrapper(rawName: String, node: ObjectNode, indent: Int, isRoot: Boolean) {
        val text = requireNotNull(node[textKey])
        val name = encodeName(rawName)
        val attributes = collectWrapperAttributes(node)
        appendIndent(indent)
        appendStart(name, isRoot)
        if (text is NullNode) {
            builder.append(" nil=\"true\"")
            appendAttributes(attributes)
            builder.append("/>")
            return
        }
        builder.append(" type=\"").append(scalarTypeName(text)).append('"')
        appendAttributes(attributes)
        builder.append('>')
        appendEscapedText(scalarTextValue(text))
        builder.append("</").append(name).append('>')
    }

    /**
     * 写出数组属性包装：`{ 属性字段..., itemsKey: 数组, textKey?: 文本 }`。
     */
    private fun writeArrayWrapper(rawName: String, node: ObjectNode, indent: Int, isRoot: Boolean) {
        val items = node[itemsKey] as ArrayNode
        val name = encodeName(rawName)
        val attributes = collectWrapperAttributes(node)
        val text = wrapperText(node)
        appendIndent(indent)
        appendStart(name, isRoot)
        builder.append(" type=\"array\"")
        appendAttributes(attributes)
        if (text == null && items.size == 0) {
            builder.append("/>")
            return
        }
        builder.append('>')
        if (text != null) {
            appendEscapedText(scalarTextValue(text))
        }
        for (child in items) {
            appendNewline()
            writeElement(ARRAY_ITEM_NAME, child, indent + 1, isRoot = false)
        }
        if (items.size > 0) {
            appendNewline()
            appendIndent(indent)
        }
        builder.append("</").append(name).append('>')
    }

    private fun writeContainer(name: String, node: ObjectNode, indent: Int, isRoot: Boolean) {
        val attributes = ArrayList<Pair<String, String>>()
        val children = ArrayList<Map.Entry<String, Node>>()
        var text: Node? = null
        for (entry in node.entries()) {
            val key = entry.key
            val value = entry.value
            if (key == textKey && isNonNilScalar(value)) {
                text = value
                continue
            }
            if (isAttributeField(key)) {
                addAttribute(attributes, key, value)
                continue
            }
            if (isReservedAttributeField(key)) {
                continue
            }
            children.add(entry)
        }
        appendIndent(indent)
        appendStart(name, isRoot)
        builder.append(" type=\"object\"")
        appendAttributes(attributes)
        if (text == null && children.isEmpty()) {
            builder.append("/>")
            return
        }
        builder.append('>')
        if (text != null) {
            appendEscapedText(scalarTextValue(text))
        }
        for (entry in children) {
            appendNewline()
            writeElement(entry.key, entry.value, indent + 1, isRoot = false)
        }
        if (children.isNotEmpty()) {
            appendNewline()
            appendIndent(indent)
        }
        builder.append("</").append(name).append('>')
    }

    private fun writeArray(name: String, node: ArrayNode, indent: Int, isRoot: Boolean) {
        appendIndent(indent)
        appendStart(name, isRoot)
        builder.append(" type=\"array\"")
        if (node.size == 0) {
            builder.append("/>")
            return
        }
        builder.append('>')
        for (child in node) {
            appendNewline()
            writeElement(ARRAY_ITEM_NAME, child, indent + 1, isRoot = false)
        }
        appendNewline()
        appendIndent(indent)
        builder.append("</").append(name).append('>')
    }

    private fun writeScalar(name: String, type: String, value: String, indent: Int, isRoot: Boolean) {
        appendIndent(indent)
        appendStart(name, isRoot)
        builder.append(" type=\"").append(type).append("\">")
        appendEscapedText(value)
        builder.append("</").append(name).append('>')
    }

    private fun appendStart(name: String, isRoot: Boolean) {
        builder.append('<').append(name)
        if (isRoot) {
            appendNamespaceDeclarations()
        }
    }

    private fun appendNamespaceDeclarations() {
        for ((uri, prefix) in namespacePrefixes) {
            builder.append(" xmlns:").append(prefix).append("=\"")
            appendEscapedAttribute(uri)
            builder.append('"')
        }
    }

    private fun appendAttributes(attributes: List<Pair<String, String>>) {
        for ((name, value) in attributes) {
            builder.append(' ').append(name).append("=\"")
            appendEscapedAttribute(value)
            builder.append('"')
        }
    }

    private fun addAttribute(attributes: MutableList<Pair<String, String>>, key: String, value: Node) {
        val prefix = requireNotNull(attributePrefix)
        val fieldName = key.substring(prefix.length)
        attributeValue(key, value)?.let { attributes.add(encodeName(fieldName) to it) }
    }

    /**
     * 包装对象中除 [textKey] / [itemsKey] 外的字段都按属性处理；协议保留字段跳过。
     */
    private fun collectWrapperAttributes(node: ObjectNode): List<Pair<String, String>> {
        val attributes = ArrayList<Pair<String, String>>()
        for ((key, value) in node.asMap()) {
            if (key == textKey || key == itemsKey || isReservedAttributeField(key)) {
                continue
            }
            addAttribute(attributes, key, value)
        }
        return attributes
    }

    /**
     * 判断字段是否以属性前缀开头（`type` / `nil` 保留给类型协议）。
     */
    private fun isAttributeField(key: String): Boolean {
        val prefix = attributePrefix ?: return false
        if (!key.startsWith(prefix)) {
            return false
        }
        val fieldName = key.substring(prefix.length)
        if (fieldName.isEmpty()) {
            throw IllegalArgumentException("XML attribute field '$key' has an empty name")
        }
        return !isReservedAttributeName(fieldName)
    }

    /**
     * 判断是否为保留给协议的属性字段（`prefix + type|nil`），这类字段不写出。
     */
    private fun isReservedAttributeField(key: String): Boolean {
        val prefix = attributePrefix ?: return false
        if (!key.startsWith(prefix)) {
            return false
        }
        val fieldName = key.substring(prefix.length)
        return isReservedAttributeName(fieldName)
    }

    private fun isReservedAttributeName(fieldName: String): Boolean = fieldName == PROTOCOL_TYPE || fieldName == PROTOCOL_NIL

    /**
     * 标量属性包装判定：至少一个属性字段，其余字段只有 [textKey] 且为标量。
     */
    private fun isScalarWrapper(node: ObjectNode): Boolean {
        val text = node[textKey] ?: return false
        if (!isScalarNode(text)) {
            return false
        }
        var hasAttribute = false
        for ((key, _) in node.asMap()) {
            if (key == textKey) {
                continue
            }
            if (!isAttributeField(key)) {
                return false
            }
            hasAttribute = true
        }
        return hasAttribute
    }

    /**
     * 数组属性包装判定：包含 [itemsKey] 数组字段，其余字段为属性字段或 [textKey] 标量。
     */
    private fun isArrayWrapper(node: ObjectNode): Boolean {
        if (node[itemsKey] !is ArrayNode) {
            return false
        }
        var hasAttributeOrText = false
        for ((key, value) in node.asMap()) {
            when {
                key == itemsKey -> Unit
                key == textKey && isNonNilScalar(value) -> hasAttributeOrText = true
                isAttributeField(key) -> hasAttributeOrText = true
                isReservedAttributeField(key) -> Unit
                else -> return false
            }
        }
        return hasAttributeOrText
    }

    private fun wrapperText(node: ObjectNode): Node? = node[textKey]?.takeIf { isNonNilScalar(it) }

    private fun isNonNilScalar(node: Node): Boolean = node is StringNode || node is NumberNode || node is BooleanNode || node is BinaryNode

    private fun isScalarNode(node: Node): Boolean = isNonNilScalar(node) || node is NullNode

    private fun scalarTypeName(node: Node): String = when (node) {
        is StringNode -> "string"
        is NumberNode -> "number"
        is BooleanNode -> "boolean"
        is BinaryNode -> "binary"
        else -> throw IllegalArgumentException("Expected scalar node but was ${node.type}")
    }

    private fun scalarTextValue(node: Node): String = when (node) {
        is StringNode -> node.value
        is NumberNode -> node.raw
        is BooleanNode -> node.value.toString()
        is BinaryNode -> Base64.getEncoder().encodeToString(node.value)
        else -> throw IllegalArgumentException("Expected scalar node but was ${node.type}")
    }

    /**
     * 读取属性字段的文本值；`null` 返回 `null` 表示跳过该属性。
     *
     * 属性只能是标量，对象/数组字段直接抛 [IllegalArgumentException]。
     */
    private fun attributeValue(key: String, node: Node): String? = when (node) {
        is NullNode -> null
        else -> try {
            scalarTextValue(node)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("XML attribute field '$key' must be a scalar node but was ${node.type}", e)
        }
    }

    /**
     * 编码元素/属性名称。
     *
     * Clark 模式：`{uri}local` 转为分配到的前缀 `nsN:local`（无 URI 时保持本地名）；
     * 前缀模式：命名空间感知时保留 `prefix:local` 冒号，否则转义非法字符。
     */
    private fun encodeName(name: String): String {
        if (namespaceMode == XmlNamespaceMode.CLARK) {
            val (uri, localName) = XmlNames.splitClark(name)
            if (uri == null) {
                return XmlNames.encode(localName)
            }
            val prefix = namespacePrefixes[uri]
                ?: throw IllegalArgumentException("Namespace URI '$uri' was not collected")
            return prefix + ":" + XmlNames.encode(localName)
        }
        return if (namespaceAware) {
            XmlNames.encodeQualified(name)
        } else {
            XmlNames.encode(name)
        }
    }

    /**
     * 收集 Clark 名称中的命名空间 URI，按首次出现顺序分配 `ns0`、`ns1`... 前缀。
     */
    private fun collectNamespaces(name: String, node: Node) {
        collectNamespaceFromName(name)
        collectNamespaces(node)
    }

    private fun collectNamespaces(node: Node) {
        when (node) {
            is ObjectNode -> {
                for ((key, child) in node.asMap()) {
                    if (key == textKey || key == itemsKey) {
                        collectNamespaces(child)
                        continue
                    }
                    val prefix = attributePrefix
                    if (prefix != null && key.startsWith(prefix)) {
                        collectNamespaceFromName(key.substring(prefix.length))
                    } else {
                        collectNamespaceFromName(key)
                    }
                    collectNamespaces(child)
                }
            }

            is ArrayNode -> {
                for (child in node) {
                    collectNamespaces(child)
                }
            }

            else -> Unit
        }
    }

    private fun collectNamespaceFromName(name: String) {
        val uri = XmlNames.splitClark(name).first ?: return
        namespacePrefixes.getOrPut(uri) { "ns" + namespacePrefixes.size }
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
        private const val ARRAY_ITEM_NAME = "item"

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
            xmlConfig?.namespaceMode ?: XmlNamespaceMode.PREFIX,
            xmlConfig?.textKey ?: XmlConfig.DEFAULT_TEXT_KEY,
            xmlConfig?.itemsKey ?: XmlConfig.DEFAULT_ITEMS_KEY,
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
 * @param namespaceMode 命名空间名称表示模式
 * @param textKey 文本字段名
 * @param itemsKey 带属性数组的数组字段名
 * @return XML 文本
 */
public fun Node.toXmlString(
    pretty: Boolean = false,
    rootName: String = "root",
    attributePrefix: String? = null,
    namespaceAware: Boolean = false,
    namespaceMode: XmlNamespaceMode = XmlNamespaceMode.PREFIX,
    textKey: String = XmlConfig.DEFAULT_TEXT_KEY,
    itemsKey: String = XmlConfig.DEFAULT_ITEMS_KEY,
): String = XmlWriter(rootName, attributePrefix, namespaceAware, namespaceMode, textKey, itemsKey).write(this, pretty)
