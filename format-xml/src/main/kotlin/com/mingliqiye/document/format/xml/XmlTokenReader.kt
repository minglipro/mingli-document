package com.mingliqiye.document.format.xml

import com.mingliqiye.document.format.xml.exception.XmlParseException
import com.mingliqiye.document.serialization.DocumentToken
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import org.w3c.dom.Element
import java.math.BigDecimal
import java.util.Base64

/**
 * XML 原生 Token 读取器（默认配置路径）。
 *
 * 直接遍历安全解析得到的 DOM 生成 [DocumentTokenReader] Token 流，不构建
 * `com.mingliqiye.document.core.Node` 树。仅用于默认读取配置：无属性映射、
 * 无命名空间感知、默认 `textKey`/`itemsKey`/`contentKey`、
 * [XmlMixedContentPolicy.PRESERVE] 混合内容策略；其他配置由 [XmlMapper] 回退
 * [com.mingliqiye.document.serialization.NodeTokenReader]。
 *
 * 生成语义与 `NodeTokenReader(XmlReader(source).read())` 等价：
 * - 根元素直接按内容输出，不额外包根名；
 * - `xsi:nil="true"` / `nil="true"` -> NULL，`type="object|array|string|number|boolean|binary"` 协议；
 * - 无类型属性时按元素结构推断：无子元素 -> 标量推断，子元素全为 `item` -> 数组，
 *   否则对象按元素名分组（重名合并为数组、保持首次出现顺序）；
 * - 对象/数组容器的直接文本（裁剪后非空）输出为 `#text` 字段；
 *   数组元素带直接文本时按 `#items` + `#text` 包装对象输出；
 * - 元素名按 [XmlNames.decode] 规则还原 `_xHHHH_` 转义；
 * - 只有空白直接文本视为格式化缩进，不产生 `#text`。
 *
 * 解析与 [XmlReader] 共用 [XmlDom]，XXE 防护一致（禁用 DOCTYPE 与外部实体）。
 *
 * 元素嵌套深度与 [XmlReader] 共用迭代校验（[XmlReader.checkDepth]）：超过 [maxDepth]
 * 时在生成 Token 前抛出 [XmlParseException]，不会因超深输入 StackOverflow。
 *
 * @param source XML 文本
 * @param maxDepth 最大 XML 元素嵌套深度（根元素深度为 `1`），默认
 *   [XmlConfig.DEFAULT_MAX_DEPTH]；`0` 或负数表示不限制
 */
internal class XmlTokenReader(
    source: String,
    private val maxDepth: Int = XmlConfig.DEFAULT_MAX_DEPTH,
) : DocumentTokenReader {

    private val tokens: List<DocumentToken> = buildTokens(source, maxDepth)
    private var index = -1
    private var current: DocumentToken? = null

    override val currentToken: DocumentTokenType?
        get() = current?.type

    override val textValue: String?
        get() = current?.text

    override val booleanValue: Boolean?
        get() = current?.booleanValue

    override val binaryValue: ByteArray?
        get() = current?.binaryValue

    override fun nextToken(): DocumentTokenType? {
        index++
        if (index >= tokens.size) {
            current = null
            return null
        }
        val token = tokens[index]
        current = token
        return token.type
    }

    private companion object {

        private const val PROTOCOL_TYPE = "type"
        private const val PROTOCOL_NIL = "nil"
        private const val PROTOCOL_XSI_NIL = "xsi:nil"
        private const val ARRAY_ITEM_NAME = "item"
        private const val TEXT_KEY = XmlConfig.DEFAULT_TEXT_KEY
        private const val ITEMS_KEY = XmlConfig.DEFAULT_ITEMS_KEY

        private fun buildTokens(source: String, maxDepth: Int): List<DocumentToken> {
            val root = XmlDom.parse(source)
            XmlReader.checkDepth(root, maxDepth)
            val tokens = ArrayList<DocumentToken>()
            appendElement(root, tokens)
            return tokens
        }

        private fun appendElement(element: Element, tokens: MutableList<DocumentToken>) {
            if (element.getAttribute(PROTOCOL_NIL) == "true" ||
                element.getAttribute(PROTOCOL_XSI_NIL) == "true"
            ) {
                tokens.add(DocumentToken.nullValue())
                return
            }
            when (element.getAttribute(PROTOCOL_TYPE)) {
                "object" -> appendObject(element, tokens)
                "array" -> appendArray(element, tokens)
                "string" -> tokens.add(DocumentToken.string(element.textContent))
                "number" -> tokens.add(DocumentToken.number(numberRaw(element.textContent)))
                "boolean" -> tokens.add(DocumentToken.boolean(booleanValue(element.textContent)))
                "binary" -> tokens.add(DocumentToken.binary(binaryValue(element.textContent)))
                else -> appendInferred(element, tokens)
            }
        }

        /**
         * 无类型属性时的结构推断，与 [XmlReader.inferNode] 对齐。
         */
        private fun appendInferred(element: Element, tokens: MutableList<DocumentToken>) {
            val children = childElements(element)
            when {
                children.isEmpty() -> tokens.add(inferredScalar(element.textContent))
                children.all { elementName(it) == ARRAY_ITEM_NAME } -> appendArray(element, tokens)
                else -> appendObject(element, tokens)
            }
        }

        private fun appendObject(element: Element, tokens: MutableList<DocumentToken>) {
            val children = childElements(element)
            val grouped = LinkedHashMap<String, MutableList<Element>>(children.size)
            for (child in children) {
                grouped.getOrPut(elementName(child)) { ArrayList() }.add(child)
            }
            tokens.add(DocumentToken.startObject())
            for ((name, group) in grouped) {
                tokens.add(DocumentToken.fieldName(name))
                if (group.size == 1) {
                    appendElement(group[0], tokens)
                } else {
                    tokens.add(DocumentToken.startArray())
                    for (child in group) {
                        appendElement(child, tokens)
                    }
                    tokens.add(DocumentToken.endArray())
                }
            }
            containerText(element, children)?.let { text ->
                tokens.add(DocumentToken.fieldName(TEXT_KEY))
                tokens.add(DocumentToken.string(text))
            }
            tokens.add(DocumentToken.endObject())
        }

        /**
         * 数组输出；带非空直接文本时与 [XmlReader.toArrayNode] 一致，包装成
         * `{#items: [...], #text: 文本}` 对象。
         */
        private fun appendArray(element: Element, tokens: MutableList<DocumentToken>) {
            val children = childElements(element)
            val text = containerText(element, children)
            if (text == null) {
                tokens.add(DocumentToken.startArray())
                for (child in children) {
                    appendElement(child, tokens)
                }
                tokens.add(DocumentToken.endArray())
                return
            }
            tokens.add(DocumentToken.startObject())
            tokens.add(DocumentToken.fieldName(ITEMS_KEY))
            tokens.add(DocumentToken.startArray())
            for (child in children) {
                appendElement(child, tokens)
            }
            tokens.add(DocumentToken.endArray())
            tokens.add(DocumentToken.fieldName(TEXT_KEY))
            tokens.add(DocumentToken.string(text))
            tokens.add(DocumentToken.endObject())
        }

        /**
         * 容器直接文本字段，与 [XmlReader.containerText] 对齐。
         *
         * 有子元素时取直接文本的首尾裁剪结果（纯空白视为格式缩进）；无子元素时
         * 取原始 `textContent`。
         */
        private fun containerText(element: Element, children: List<Element>): String? {
            if (children.isEmpty()) {
                val text = element.textContent
                return text.ifEmpty { null }
            }
            val text = directText(element).trim()
            return text.ifEmpty { null }
        }

        private fun inferredScalar(text: String): DocumentToken {
            val trimmed = text.trim()
            return when {
                trimmed == "true" -> DocumentToken.boolean(true)
                trimmed == "false" -> DocumentToken.boolean(false)
                isNumber(trimmed) -> DocumentToken.number(trimmed)
                else -> DocumentToken.string(text)
            }
        }

        private fun numberRaw(text: String): String {
            val raw = text.trim()
            if (!isNumber(raw)) {
                throw XmlParseException("Invalid number '$raw'")
            }
            return raw
        }

        private fun booleanValue(text: String): Boolean = when (text.trim()) {
            "true" -> true
            "false" -> false
            else -> throw XmlParseException("Invalid boolean '${text.trim()}'")
        }

        private fun binaryValue(text: String): ByteArray = Base64.getDecoder().decode(text.trim())

        private fun isNumber(text: String): Boolean = try {
            BigDecimal(text)
            true
        } catch (_: NumberFormatException) {
            false
        }

        private fun elementName(element: Element): String = XmlNames.decode(element.nodeName)

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
    }
}
