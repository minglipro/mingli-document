package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.properties.exception.PropertiesParseException
import java.io.StringReader
import java.math.BigDecimal
import java.util.Properties

/**
 * Properties 文本 → 轻量路径树 → [Node] 的解析器。
 *
 * 读取语义对齐 上游实现 `JavaPropsParser`（底层同样是 `java.util.Properties.load`
 * 再按 `JavaPropsSchema` 还原结构）：
 * - 使用 JDK Properties 语法（`#`/`!` 注释、`\uXXXX`、行续接等）；
 * - key 先按字典序排序再插入，保证确定性输出（上游实现 使用 Properties 迭代顺序，
 *   顺序未定义；本模块固定排序为兼容既有行为）；
 * - 路径拆分、下标标记、简单数字下标、前缀、分隔符转义见 [PropertiesPathSplitter]；
 * - 数组稠密：下标只用于排序，不填充空洞（上游实现 语义）；
 * - 节点可同时有值和子节点，转 Node 时值写成数组首元素或空字符串键 `""` 字段
 *   （上游实现 `JPropNode.asRaw()` 语义）。
 *
 * maxDepth 在构建树之前按路径令牌数校验，避免超深输入导致递归溢出。
 */
internal class PropertiesTreeReader(
    private val config: PropertiesConfig,
    private val maxDepth: Int = config.maxDepth,
) {

    private val splitter = PropertiesPathSplitter(config)

    /**
     * 解析 Properties 文本为轻量路径树。
     *
     * @throws PropertiesParseException Properties 语法非法、百分号转义非法或路径超深时抛出
     */
    fun read(source: String): PropertiesPathNode {
        val properties = Properties()
        try {
            properties.load(StringReader(source))
        } catch (e: Exception) {
            throw PropertiesParseException("Invalid Properties: ${e.message}", e)
        }
        val root = PropertiesPathNode()
        for (name in properties.stringPropertyNames().sorted()) {
            val tokens = splitter.split(name) ?: continue
            checkDepth(tokens, name)
            var node = root
            for (token in tokens) {
                node = when (token) {
                    is PropertiesPathToken.Index -> node.addByIndex(token.index)

                    is PropertiesPathToken.Segment -> {
                        if (config.parseSimpleIndexes &&
                            !token.percentEscaped &&
                            isSimpleIndex(token.text)
                        ) {
                            node.addByIndex(token.text.toInt())
                        } else {
                            node.addByName(token.text)
                        }
                    }
                }
            }
            node.setValue(properties.getProperty(name))
        }
        return root
    }

    /**
     * 解析 Properties 文本并转换为文档树。
     *
     * 空输入返回空对象；根只有值（例如 key 为空）时返回标量节点。
     */
    fun readNode(source: String): Node = convert(read(source))

    private fun convert(node: PropertiesPathNode): Node {
        if (node.isArray) {
            val elements = ArrayList<Node>()
            node.nullableValue?.let { elements.add(inferScalar(it)) }
            for (child in node.arrayContents()) {
                elements.add(convert(child))
            }
            return ArrayNode.from(elements)
        }
        if (node.hasNameChildren) {
            val fields = LinkedHashMap<String, Node>()
            node.nullableValue?.let { fields[EMPTY_FIELD_NAME] = inferScalar(it) }
            for ((name, child) in node.nameContents()) {
                fields[name] = convert(child)
            }
            return ObjectNode.from(fields)
        }
        val value = node.nullableValue ?: return ObjectNode.EMPTY
        return inferScalar(value)
    }

    private fun inferScalar(text: String): Node = when {
        text == "true" -> BooleanNode.of(true)
        text == "false" -> BooleanNode.of(false)
        isNumber(text) -> NumberNode.ofRaw(text)
        else -> StringNode.of(text)
    }

    private fun isNumber(text: String): Boolean = try {
        BigDecimal(text)
        true
    } catch (_: NumberFormatException) {
        false
    }

    private fun checkDepth(tokens: List<PropertiesPathToken>, name: String) {
        if (maxDepth <= 0) {
            return
        }
        val depth = tokens.size + 1
        if (depth > maxDepth) {
            throw PropertiesParseException("Properties key path '$name' depth $depth exceeds maxDepth $maxDepth")
        }
    }

    private fun isSimpleIndex(segment: String): Boolean {
        if (segment.isEmpty() || segment.length > MAX_SIMPLE_INDEX_LENGTH) {
            return false
        }
        for (ch in segment) {
            if (ch < '0' || ch > '9') {
                return false
            }
        }
        return true
    }

    internal companion object {

        /** 空字符串键：节点同时有值和子节点时，值写成该字段（上游实现 `asRaw` 语义）。 */
        internal const val EMPTY_FIELD_NAME: String = ""

        /** 简单下标段最大长度（上游实现 限制 9 位）。 */
        internal const val MAX_SIMPLE_INDEX_LENGTH: Int = 9
    }
}
