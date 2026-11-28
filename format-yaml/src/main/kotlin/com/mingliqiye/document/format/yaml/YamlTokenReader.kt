package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.format.yaml.exception.YamlParseException
import com.mingliqiye.document.serialization.DocumentToken
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.AnchorNode
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.NodeTuple
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import java.util.IdentityHashMap
import org.yaml.snakeyaml.nodes.Node as YamlNode

/**
 * YAML 原生 Token 读取器。
 *
 * 通过 SnakeYAML 的 `compose` 直接得到 YAML AST（[MappingNode]/[SequenceNode]/
 * [ScalarNode]），遍历生成 Token 流，不构建 core `Node` 树。Scalar tag 到 Token
 * 的映射与 [YamlNodeMapping] 对齐：
 *
 * - `!!null` -> [DocumentTokenType.NULL]；[YamlReadFeature.EMPTY_STRING_AS_NULL]
 *   关闭时空 plain 标量输出 STRING `""`
 * - `!!bool` -> [DocumentTokenType.BOOLEAN]；[YamlReadFeature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS]
 *   开启时 `yes`/`no`/`on`/`off` 等输出 STRING，显式 `!!bool` 无法匹配的值按 STRING
 * - `!!int` / `!!float` -> [DocumentTokenType.NUMBER]，数字文本经 SafeConstructor
 *   归一化（`0x1F`、`1_000` 等输出十进制 raw；NaN/Infinity 拒绝）
 * - `!!binary` -> [DocumentTokenType.BINARY]，Base64 解码为原始字节
 * - `!!timestamp` -> [DocumentTokenType.STRING]，ISO-8601 instant 文本
 * - 未知 tag：默认拒绝（与 [YamlReader] 的 Node 路径一致）；[allowCustomTags]
 *   开启后标量按 [DocumentTokenType.STRING] 输出，序列/映射按底层集合结构输出
 *
 * mapping 字段名沿用 [YamlNodeMapping] 规则（null -> `"null"`，string/number/boolean
 * -> `toString()`）；merge key 与重复 key 复用 SafeConstructor 的 AST 级校验，
 * 与 [YamlReader] 语义一致。
 *
 * 限制在 Token 生成阶段执行（`0` 表示使用 SnakeYAML 默认值，与 [YamlReader] 一致）：
 * - [maxDepth] 交给 SnakeYAML `nestingDepthLimit`；
 * - [maxAliases] 交给 SnakeYAML `maxAliasesForCollections`；
 * - [maxStringLength] 在遍历时校验 STRING / FIELD_NAME 文本长度。
 *
 * Token 层无法等价实现的语义：
 * - 递归节点（如 `&a {self: *a}`）抛出 [YamlParseException]，不构建环；
 * - 显式 `!!set` / `!!omap` / `!!pairs` 集合 tag 按底层 mapping/sequence 结构输出
 *   （Node 路径会构造 Set/List 后得到 ArrayNode）。
 *
 * @param source YAML 文本（单文档，多文档输入抛异常）
 * @param maxDepth 最大嵌套深度；`0` 使用 SnakeYAML 默认值
 * @param maxAliases 非标量 alias 最大数量；`0` 使用 SnakeYAML 默认值
 * @param maxStringLength 单个字符串最大长度；`0` 表示不限制
 * @param allowCustomTags 是否允许未知自定义 tag，默认 `false`；开启后与 Node 路径一致
 * @param readFeatures 读取特性集合，默认 [YamlConfig.DEFAULT_READ_FEATURES]
 * @param allowDuplicateKeys 是否允许重复 mapping key，默认 `false`
 * @param codePointLimit 单次输入最大 code point 数量；`0` 使用 SnakeYAML 默认值
 */
internal class YamlTokenReader(
    source: String,
    maxDepth: Int = 0,
    maxAliases: Int = 0,
    maxStringLength: Int = 0,
    allowCustomTags: Boolean = false,
    readFeatures: Set<YamlReadFeature> = YamlConfig.DEFAULT_READ_FEATURES,
    allowDuplicateKeys: Boolean = false,
    codePointLimit: Int = 0,
) : DocumentTokenReader {

    private val tokens: List<DocumentToken> = buildTokens(
        source,
        maxDepth,
        maxAliases,
        maxStringLength,
        allowCustomTags,
        readFeatures,
        allowDuplicateKeys,
        codePointLimit,
    )
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

        private fun buildTokens(
            source: String,
            maxDepth: Int,
            maxAliases: Int,
            maxStringLength: Int,
            allowCustomTags: Boolean,
            readFeatures: Set<YamlReadFeature>,
            allowDuplicateKeys: Boolean,
            codePointLimit: Int,
        ): List<DocumentToken> = try {
            val constructor = YamlConstructors.create(
                maxDepth,
                maxAliases,
                allowCustomTags,
                readFeatures,
                allowDuplicateKeys,
                codePointLimit,
            )
            val root = Yaml(constructor).compose(StringReader(source))
            val tokens = ArrayList<DocumentToken>()
            if (root == null) {
                tokens.add(DocumentToken.nullValue())
            } else {
                YamlTokenTraversal(constructor, maxStringLength, allowCustomTags).append(root, tokens)
            }
            tokens
        } catch (e: YAMLException) {
            throw YamlParseException("Invalid YAML: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw YamlParseException("Invalid YAML: ${e.message}", e)
        }
    }
}

/**
 * YAML AST -> Token 遍历器。
 */
private class YamlTokenTraversal(
    private val constructor: YamlConstructor,
    private val maxStringLength: Int,
    private val allowCustomTags: Boolean,
) {

    private val activeNodes = IdentityHashMap<YamlNode, Boolean>()

    fun append(node: YamlNode, tokens: MutableList<DocumentToken>) {
        val realNode = if (node is AnchorNode) node.realNode else node
        if (!allowCustomTags && constructor.isUndefinedTag(realNode)) {
            // Node 路径由 ConstructUndefined 抛出构造器异常；Token 路径不构造值，
            // 因此在遍历时显式拒绝，保证两条路径对未知 tag 行为一致。
            throw YamlParseException("Unsupported YAML tag: ${realNode.tag}")
        }
        when (realNode) {
            is MappingNode -> appendMapping(realNode, tokens)
            is SequenceNode -> appendSequence(realNode, tokens)
            is ScalarNode -> appendScalar(realNode, tokens)
            else -> throw YamlParseException("Unsupported YAML node type: ${realNode.javaClass.name}")
        }
    }

    private fun appendMapping(node: MappingNode, tokens: MutableList<DocumentToken>) {
        enter(node) {
            constructor.flattenMappingNode(node)
            tokens.add(DocumentToken.startObject())
            for (tuple in node.value) {
                appendField(tuple, tokens)
            }
            tokens.add(DocumentToken.endObject())
        }
    }

    private fun appendField(tuple: NodeTuple, tokens: MutableList<DocumentToken>) {
        val name = fieldName(constructor.constructNodeValue(tuple.keyNode))
        checkStringLength(name)
        tokens.add(DocumentToken.fieldName(name))
        append(tuple.valueNode, tokens)
    }

    private fun appendSequence(node: SequenceNode, tokens: MutableList<DocumentToken>) {
        enter(node) {
            tokens.add(DocumentToken.startArray())
            for (element in node.value) {
                append(element, tokens)
            }
            tokens.add(DocumentToken.endArray())
        }
    }

    private fun appendScalar(node: ScalarNode, tokens: MutableList<DocumentToken>) {
        when {
            node.tag == Tag.NULL -> appendNull(node, tokens)
            node.tag == Tag.BOOL -> appendBoolean(node, tokens)
            node.tag == Tag.INT || node.tag == Tag.FLOAT -> appendNumber(node, tokens)
            node.tag == Tag.BINARY -> appendBinary(node, tokens)
            node.tag == Tag.TIMESTAMP -> appendTimestamp(node, tokens)
            else -> appendString(node.value, tokens)
        }
    }

    /**
     * 上游实现 `EMPTY_STRING_AS_NULL`：关闭时空 plain 标量构造为空字符串。
     */
    private fun appendNull(node: ScalarNode, tokens: MutableList<DocumentToken>) {
        val value = constructor.constructNodeValue(node)
        if (value is String) {
            appendString(value, tokens)
        } else {
            tokens.add(DocumentToken.nullValue())
        }
    }

    private fun appendBoolean(node: ScalarNode, tokens: MutableList<DocumentToken>) {
        when (val value = constructor.constructNodeValue(node)) {
            is Boolean -> tokens.add(DocumentToken.boolean(value))
            is String -> appendString(value, tokens)
            else -> tokens.add(DocumentToken.nullValue())
        }
    }

    private fun appendNumber(node: ScalarNode, tokens: MutableList<DocumentToken>) {
        tokens.add(DocumentToken.number(numberRaw(constructor.constructNodeValue(node))))
    }

    private fun appendBinary(node: ScalarNode, tokens: MutableList<DocumentToken>) {
        val value = constructor.constructNodeValue(node)
        if (value !is ByteArray) {
            throw YamlParseException("Invalid YAML binary value")
        }
        tokens.add(DocumentToken.binary(value))
    }

    private fun appendTimestamp(node: ScalarNode, tokens: MutableList<DocumentToken>) {
        val value = constructor.constructNodeValue(node)
        if (value !is Date) {
            throw YamlParseException("Invalid YAML timestamp value")
        }
        appendString(value.toInstant().toString(), tokens)
    }

    private fun appendString(value: String, tokens: MutableList<DocumentToken>) {
        checkStringLength(value)
        tokens.add(DocumentToken.string(value))
    }

    private fun numberRaw(value: Any?): String = when (value) {
        is Double -> {
            if (!value.isFinite()) {
                throw YamlParseException("YAML NaN/Infinity numbers are not supported")
            }
            BigDecimal.valueOf(value).toPlainString()
        }

        is Float -> {
            if (!value.isFinite()) {
                throw YamlParseException("YAML NaN/Infinity numbers are not supported")
            }
            BigDecimal.valueOf(value.toDouble()).toPlainString()
        }

        is BigDecimal -> value.toPlainString()
        is BigInteger -> value.toString()
        is Number -> value.toString()
        else -> throw YamlParseException("Unsupported YAML number type: ${value?.javaClass?.name ?: "null"}")
    }

    private fun fieldName(value: Any?): String = when (value) {
        null -> "null"
        is String -> value
        is Number, is Boolean, is Char -> value.toString()
        else -> throw YamlParseException("Unsupported YAML mapping key type: ${value.javaClass.name}")
    }

    private fun checkStringLength(value: String) {
        if (maxStringLength > 0 && value.length > maxStringLength) {
            throw YamlParseException("String length ${value.length} exceeds maxStringLength $maxStringLength")
        }
    }

    private fun enter(node: YamlNode, block: () -> Unit) {
        if (activeNodes.put(node, true) != null) {
            throw YamlParseException("Recursive YAML nodes are not supported")
        }
        try {
            block()
        } finally {
            activeNodes.remove(node)
        }
    }
}
