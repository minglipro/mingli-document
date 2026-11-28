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
 * 有序混合内容：对象/数组包装包含 [contentKey] 数组字段时（与读取策略无关），按数组顺序
 * 交错写出文本项（`StringNode`，转义写出）与子元素项（单字段对象 `{元素名: 节点}`）；
 * 数组元素项的元素名固定为 `item`。此时 [textKey] 与普通子元素字段作为兼容视图被忽略。
 * 有序内容区域内部不插入 pretty 空白，避免格式缩进污染文本。
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
 * @param contentKey 有序混合内容的数组字段名
 */
internal class XmlWriter(
    private val rootName: String,
    private val attributePrefix: String? = null,
    private val namespaceAware: Boolean = false,
    private val namespaceMode: XmlNamespaceMode = XmlNamespaceMode.PREFIX,
    private val textKey: String = XmlConfig.DEFAULT_TEXT_KEY,
    private val itemsKey: String = XmlConfig.DEFAULT_ITEMS_KEY,
    private val contentKey: String = XmlConfig.DEFAULT_CONTENT_KEY,
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
    }

    private var out: Appendable = StringBuilder()
    private val namespacePrefixes = LinkedHashMap<String, String>()
    private var pretty = false

    fun write(node: Node, pretty: Boolean): String {
        val builder = StringBuilder()
        writeTo(node, pretty, builder)
        return builder.toString()
    }

    /**
     * 直接写出到 [Appendable]（例如 Writer），不先构造完整 String。
     */
    fun writeTo(node: Node, pretty: Boolean, target: Appendable) {
        this.pretty = pretty
        this.out = target
        namespacePrefixes.clear()
        if (namespaceMode == XmlNamespaceMode.CLARK) {
            collectNamespaces(rootName, node)
        }
        writeElement(rootName, node, 0, isRoot = true)
    }

    private fun writeElement(
        rawName: String,
        node: Node,
        indent: Int,
        isRoot: Boolean,
        compact: Boolean = false,
    ) {
        if (node is ObjectNode) {
            if (isScalarWrapper(node)) {
                writeScalarWrapper(rawName, node, indent, isRoot, compact)
                return
            }
            if (isArrayWrapper(node)) {
                writeArrayWrapper(rawName, node, indent, isRoot, compact)
                return
            }
        }
        val name = encodeName(rawName)
        when (node) {
            is NullNode -> {
                appendIndent(indent, compact)
                appendStart(name, isRoot)
                out.append(" nil=\"true\"/>")
            }

            is ObjectNode -> writeContainer(name, node, indent, isRoot, compact)
            is ArrayNode -> writeArray(name, node, indent, isRoot, compact)
            is StringNode -> writeScalar(name, "string", node.value, indent, isRoot, compact)
            is NumberNode -> writeScalar(name, "number", node.raw, indent, isRoot, compact)
            is BooleanNode -> writeScalar(name, "boolean", node.value.toString(), indent, isRoot, compact)
            is BinaryNode -> writeScalar(
                name,
                "binary",
                Base64.getEncoder().encodeToString(node.value),
                indent,
                isRoot,
                compact,
            )
        }
    }

    /**
     * 写出标量属性包装：`{ 属性字段..., textKey: 标量 }` → `<name type="..." 属性...>文本</name>`。
     */
    private fun writeScalarWrapper(
        rawName: String,
        node: ObjectNode,
        indent: Int,
        isRoot: Boolean,
        compact: Boolean,
    ) {
        val text = requireNotNull(node[textKey])
        val name = encodeName(rawName)
        val attributes = collectWrapperAttributes(node)
        appendIndent(indent, compact)
        appendStart(name, isRoot)
        if (text is NullNode) {
            out.append(" nil=\"true\"")
            appendAttributes(attributes)
            out.append("/>")
            return
        }
        out.append(" type=\"").append(scalarTypeName(text)).append('"')
        appendAttributes(attributes)
        out.append('>')
        appendEscapedText(scalarTextValue(text))
        out.append("</").append(name).append('>')
    }

    /**
     * 写出数组属性包装：`{ 属性字段..., itemsKey: 数组, textKey?: 文本 }`。
     *
     * 含 [contentKey] 有序数组时按有序内容写出（忽略 [textKey] 与 [itemsKey] 兼容视图）。
     */
    private fun writeArrayWrapper(
        rawName: String,
        node: ObjectNode,
        indent: Int,
        isRoot: Boolean,
        compact: Boolean,
    ) {
        val content = node[contentKey] as? ArrayNode
        val items = node[itemsKey] as ArrayNode
        val name = encodeName(rawName)
        val attributes = collectWrapperAttributes(node)
        val text = wrapperText(node)
        appendIndent(indent, compact)
        appendStart(name, isRoot)
        out.append(" type=\"array\"")
        appendAttributes(attributes)
        if (content != null) {
            writeOrderedBody(content, indent, name, ARRAY_ITEM_NAME)
            return
        }
        if (text == null && items.size == 0) {
            out.append("/>")
            return
        }
        out.append('>')
        if (text != null) {
            appendEscapedText(scalarTextValue(text))
        }
        for (child in items) {
            appendNewline()
            writeElement(ARRAY_ITEM_NAME, child, indent + 1, isRoot = false)
        }
        if (items.size > 0) {
            appendNewline()
            appendIndent(indent, compact = false)
        }
        out.append("</").append(name).append('>')
    }

    private fun writeContainer(
        name: String,
        node: ObjectNode,
        indent: Int,
        isRoot: Boolean,
        compact: Boolean,
    ) {
        val attributes = ArrayList<Pair<String, String>>()
        val children = ArrayList<Map.Entry<String, Node>>()
        var text: Node? = null
        var content: ArrayNode? = null
        for (entry in node.entries()) {
            val key = entry.key
            val value = entry.value
            if (key == contentKey && value is ArrayNode) {
                content = value
                continue
            }
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
        appendIndent(indent, compact)
        appendStart(name, isRoot)
        out.append(" type=\"object\"")
        appendAttributes(attributes)
        if (content != null) {
            writeOrderedBody(content, indent, name, expectedElementName = null)
            return
        }
        if (text == null && children.isEmpty()) {
            out.append("/>")
            return
        }
        out.append('>')
        if (text != null) {
            appendEscapedText(scalarTextValue(text))
        }
        for (entry in children) {
            appendNewline()
            writeElement(entry.key, entry.value, indent + 1, isRoot = false)
        }
        if (children.isNotEmpty()) {
            appendNewline()
            appendIndent(indent, compact = false)
        }
        out.append("</").append(name).append('>')
    }

    /**
     * 按 [contentKey] 数组顺序交错写出混合内容。
     *
     * 文本项 `StringNode` 直接转义写出；子元素项为单字段对象 `{元素名: 节点}`，
     * 以紧凑方式写出（不加格式缩进）。[expectedElementName] 非空时校验元素名（数组固定 `item`）。
     */
    private fun writeOrderedBody(
        content: ArrayNode,
        indent: Int,
        name: String,
        expectedElementName: String?,
    ) {
        if (content.size == 0) {
            out.append("/>")
            return
        }
        out.append('>')
        for (entry in content) {
            when (entry) {
                is StringNode -> appendEscapedText(entry.value)
                is ObjectNode -> {
                    val fields = entry.entries()
                    if (fields.size != 1) {
                        throw IllegalArgumentException(
                            "Ordered mixed content element entry must have exactly one field but had ${fields.size}",
                        )
                    }
                    val field = fields.first()
                    if (expectedElementName != null && field.key != expectedElementName) {
                        throw IllegalArgumentException(
                            "Ordered mixed content element entry must be named '$expectedElementName' but was '${field.key}'",
                        )
                    }
                    writeElement(field.key, field.value, indent + 1, isRoot = false, compact = true)
                }

                else -> throw IllegalArgumentException(
                    "Ordered mixed content entry must be a StringNode or single-field ObjectNode but was ${entry.type}",
                )
            }
        }
        out.append("</").append(name).append('>')
    }

    private fun writeArray(
        name: String,
        node: ArrayNode,
        indent: Int,
        isRoot: Boolean,
        compact: Boolean,
    ) {
        appendIndent(indent, compact)
        appendStart(name, isRoot)
        out.append(" type=\"array\"")
        if (node.size == 0) {
            out.append("/>")
            return
        }
        out.append('>')
        for (child in node) {
            appendNewline()
            writeElement(ARRAY_ITEM_NAME, child, indent + 1, isRoot = false)
        }
        appendNewline()
        appendIndent(indent, compact = false)
        out.append("</").append(name).append('>')
    }

    private fun writeScalar(
        name: String,
        type: String,
        value: String,
        indent: Int,
        isRoot: Boolean,
        compact: Boolean,
    ) {
        appendIndent(indent, compact)
        appendStart(name, isRoot)
        out.append(" type=\"").append(type).append("\">")
        appendEscapedText(value)
        out.append("</").append(name).append('>')
    }

    private fun appendStart(name: String, isRoot: Boolean) {
        out.append('<').append(name)
        if (isRoot) {
            appendNamespaceDeclarations()
        }
    }

    private fun appendNamespaceDeclarations() {
        for ((uri, prefix) in namespacePrefixes) {
            out.append(" xmlns:").append(prefix).append("=\"")
            appendEscapedAttribute(uri)
            out.append('"')
        }
    }

    private fun appendAttributes(attributes: List<Pair<String, String>>) {
        for ((name, value) in attributes) {
            out.append(' ').append(name).append("=\"")
            appendEscapedAttribute(value)
            out.append('"')
        }
    }

    private fun addAttribute(attributes: MutableList<Pair<String, String>>, key: String, value: Node) {
        val prefix = requireNotNull(attributePrefix)
        val fieldName = key.substring(prefix.length)
        attributeValue(key, value)?.let { attributes.add(encodeName(fieldName) to it) }
    }

    /**
     * 包装对象中除 [textKey] / [itemsKey] / [contentKey] 外的字段都按属性处理；协议保留字段跳过。
     */
    private fun collectWrapperAttributes(node: ObjectNode): List<Pair<String, String>> {
        val attributes = ArrayList<Pair<String, String>>()
        for ((key, value) in node.asMap()) {
            if (key == textKey || key == itemsKey || key == contentKey || isReservedAttributeField(key)) {
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
     * 数组属性包装判定：包含 [itemsKey] 数组字段，其余字段为属性字段、[textKey] 标量
     * 或 [contentKey] 数组。
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
                key == contentKey && value is ArrayNode -> hasAttributeOrText = true
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
                    if (key == textKey || key == itemsKey || key == contentKey) {
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

    private fun appendIndent(indent: Int, compact: Boolean) {
        if (pretty && !compact) {
            repeat(indent) { out.append("  ") }
        }
    }

    private fun appendNewline() {
        if (pretty) {
            out.append('\n')
        }
    }

    private fun appendEscapedText(value: String) {
        for (ch in value) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '\r' -> out.append("&#13;")
                else -> out.append(ch)
            }
        }
    }

    private fun appendEscapedAttribute(value: String) {
        for (ch in value) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\n' -> out.append("&#10;")
                '\r' -> out.append("&#13;")
                '\t' -> out.append("&#9;")
                else -> out.append(ch)
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
            xmlConfig?.contentKey ?: XmlConfig.DEFAULT_CONTENT_KEY,
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
 * @param contentKey 有序混合内容的数组字段名
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
    contentKey: String = XmlConfig.DEFAULT_CONTENT_KEY,
): String = XmlWriter(
    rootName,
    attributePrefix,
    namespaceAware,
    namespaceMode,
    textKey,
    itemsKey,
    contentKey,
).write(this, pretty)
