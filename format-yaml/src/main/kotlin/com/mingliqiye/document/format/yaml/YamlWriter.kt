package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.NodeId
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.io.Writer
import java.math.BigDecimal
import java.math.BigInteger
import java.util.regex.Pattern
import org.yaml.snakeyaml.nodes.Node as YamlNode

/**
 * 将文档树写成 YAML 文本。
 *
 * 由 [toYamlString] 与 [YamlMapper] 调用；不对外暴露。
 *
 * 写出只根据 [Node] 类型生成标准 YAML，不保留自定义 tag 信息
 * （读取侧 [YamlReader] 的 `allowCustomTags` 只影响解析）。
 *
 * [BinaryNode] 交给 SnakeYAML 的 `byte[]` 表示器写出，因此输出为 `!!binary`
 * Base64 标量（流式格式为双引号标量，块式格式为字面量标量），可被
 * [YamlReader] 还原为 [BinaryNode]。
 *
 * 字符串标量的引号/字面量块风格对齐 上游实现 YAML 2.15 `YAMLGenerator`：
 * 默认（[YamlWriteFeature.MINIMIZE_QUOTES] 关闭）字符串值一律双引号；开启后按
 * 上游实现 的 quoting checker 规则输出 plain/双引号/字面量块。mapping key 仍由
 * SnakeYAML 按"plain 是否安全"决定（必要时单引号）。
 */
internal class YamlWriter(
    private val settings: YamlWriteSettings = YamlWriteSettings.DEFAULT,
    private val pretty: Boolean = false,
) {

    /**
     * 写出单个文档并返回 YAML 文本。
     */
    fun write(node: Node): String = createYaml().dump(toPlain(node)).trimEnd()

    /**
     * 直接将单个文档写到字符流，不先构造完整 String。
     *
     * 与 [write] 一样去掉尾部空白。
     */
    fun writeTo(node: Node, out: Writer) {
        val trim = TrimEndWriter(out)
        createYaml().dump(toPlain(node), trim)
        trim.finish()
    }

    /**
     * 写出多个文档并返回 `---` 分隔的 YAML 文本。
     *
     * 空列表返回空字符串；是否输出起始 `---` 由
     * [YamlWriteFeature.WRITE_DOC_START_MARKER] 决定。
     */
    fun writeAll(nodes: Iterable<Node>): String = createYaml().dumpAll(nodes.map(::toPlain).iterator()).trimEnd()

    private fun createYaml(): Yaml {
        val options = createDumperOptions()
        val representer = YamlValueRepresenter(options, settings)
        return Yaml(SafeConstructor(LoaderOptions()), representer, options)
    }

    private fun createDumperOptions(): DumperOptions {
        val options = DumperOptions()
        options.setIndent(settings.indent)
        options.setAllowUnicode(settings.allowUnicode)
        options.setSplitLines(settings.isEnabled(YamlWriteFeature.SPLIT_LINES))
        if (settings.isEnabled(YamlWriteFeature.CANONICAL_OUTPUT)) {
            options.setCanonical(true)
        } else {
            options.setCanonical(false)
            val flowStyle = settings.flowStyle() ?: if (pretty) {
                DumperOptions.FlowStyle.BLOCK
            } else {
                DumperOptions.FlowStyle.FLOW
            }
            options.setDefaultFlowStyle(flowStyle)
        }
        options.setPrettyFlow(pretty)
        if (settings.isEnabled(YamlWriteFeature.INDENT_ARRAYS)) {
            // 对齐 上游实现：indicatorIndent=1（与 indent 区分，否则缩进不可见）。
            options.setIndicatorIndent(1)
        }
        if (settings.isEnabled(YamlWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)) {
            options.setIndicatorIndent(settings.indent)
            options.setIndentWithIndicator(true)
        }
        if (settings.isEnabled(YamlWriteFeature.USE_PLATFORM_LINE_BREAKS)) {
            options.setLineBreak(DumperOptions.LineBreak.getPlatformLineBreak())
        }
        if (settings.isEnabled(YamlWriteFeature.ALLOW_LONG_KEYS)) {
            options.setMaxSimpleKeyLength(MAX_LONG_KEY_LENGTH)
        }
        options.setExplicitStart(settings.isEnabled(YamlWriteFeature.WRITE_DOC_START_MARKER))
        options.setExplicitEnd(settings.explicitEnd)
        if (settings.lineWidth > 0) {
            options.setWidth(settings.lineWidth)
        }
        return options
    }

    private fun toPlain(node: Node): Any? = when (node) {
        is ObjectNode -> toPlainObject(node)
        is ArrayNode -> node.map(::toPlain)
        is NumberNode -> toPlainNumber(node)
        is StringNode -> YamlStringValue(node.value)
        is BooleanNode -> node.value
        is BinaryNode -> node.value
        is NullNode -> null
    }

    private fun toPlainObject(node: ObjectNode): Map<String, Any?> {
        val fields = LinkedHashMap<String, Any?>(node.size)
        for ((key, value) in node.asMap()) {
            fields[key] = toPlain(value)
        }
        return fields
    }

    private fun toPlainNumber(node: NumberNode): Number = if (node.raw.contains('.') || node.raw.contains('e', ignoreCase = true)) {
        BigDecimal(node.raw)
    } else {
        BigInteger(node.raw)
    }

    /**
     * 字符串值包装：与 mapping key（普通 [String]）区分开，使值可以按 上游实现
     * 规则选择引号/字面量块风格，而 key 仍使用 SnakeYAML 的 plain 安全判断。
     */
    private class YamlStringValue(
        val value: String,
    )

    /**
     * 上游实现 YAML 2.15 `YAMLGenerator` 字符串风格规则的 SnakeYAML 表示器。
     */
    private class YamlValueRepresenter(
        options: DumperOptions,
        settings: YamlWriteSettings,
    ) : Representer(options) {

        private val resolver = Resolver()
        private val minimizeQuotes = settings.isEnabled(YamlWriteFeature.MINIMIZE_QUOTES)
        private val literalBlockStyle = settings.isEnabled(YamlWriteFeature.LITERAL_BLOCK_STYLE)
        private val alwaysQuoteNumbers = settings.isEnabled(YamlWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)

        init {
            representers[YamlStringValue::class.java] = RepresentStringValue()
        }

        private fun representStringValue(value: String): YamlNode {
            if (value.isEmpty()) {
                return representScalar(Tag.STR, value, DumperOptions.ScalarStyle.DOUBLE_QUOTED)
            }
            return when {
                minimizeQuotes -> when {
                    value.indexOf('\n') >= 0 -> representScalar(Tag.STR, value, DumperOptions.ScalarStyle.LITERAL)
                    needToQuoteValue(value) ||
                        (alwaysQuoteNumbers && PLAIN_NUMBER_P.matcher(value).matches()) ->
                        representScalar(Tag.STR, value, DumperOptions.ScalarStyle.DOUBLE_QUOTED)

                    else -> representPlain(value)
                }

                literalBlockStyle && value.indexOf('\n') >= 0 ->
                    representScalar(Tag.STR, value, DumperOptions.ScalarStyle.LITERAL)

                else -> representScalar(Tag.STR, value, DumperOptions.ScalarStyle.DOUBLE_QUOTED)
            }
        }

        /**
         * plain 风格：使用 SnakeYAML 解析器探测到的 tag，避免输出多余的 `!!str`。
         *
         * 与 上游实现 一致：看起来像数字/布尔的字符串写成 plain 后，读回时会被解析为
         * 对应标量类型（除非开启 [YamlWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS]）。
         */
        private fun representPlain(value: String): YamlNode {
            val tag = resolver.resolve(NodeId.scalar, value, true)
            return representScalar(tag, value, DumperOptions.ScalarStyle.PLAIN)
        }

        private inner class RepresentStringValue : Represent {

            override fun representData(data: Any): YamlNode = representStringValue((data as YamlStringValue).value)
        }

        private fun needToQuoteValue(value: String): Boolean = isReservedKeyword(value) || valueHasQuotableChar(value)

        private fun isReservedKeyword(value: String): Boolean {
            if (value.isEmpty()) {
                return true
            }
            return when (value[0]) {
                'f', 'n', 'o', 't', 'y', 'F', 'N', 'O', 'T', 'Y' -> value in RESERVED_KEYWORDS
                '~' -> true
                else -> false
            }
        }

        private fun valueHasQuotableChar(value: String): Boolean {
            for (index in value.indices) {
                when (value[index]) {
                    '[', ']', '{', '}', ',' -> return true
                    '#' -> if (index > 0 && isBlank(value[index - 1])) return true
                    ':' -> if (index < value.length - 1 && isBlank(value[index + 1])) return true
                }
            }
            return false
        }

        private fun isBlank(value: Char): Boolean = value == ' ' || value == '\t'

        private companion object {

            private val RESERVED_KEYWORDS: Set<String> = setOf(
                "false", "False", "FALSE",
                "n", "N",
                "no", "No", "NO",
                "null", "Null", "NULL",
                "on", "On", "ON",
                "off", "Off", "OFF",
                "true", "True", "TRUE",
                "y", "Y",
                "yes", "Yes", "YES",
            )
        }
    }
}

/** 上游实现 `YAMLGenerator.PLAIN_NUMBER_P`：判断字符串是否看起来像 YAML 数字。 */
private val PLAIN_NUMBER_P: Pattern = Pattern.compile("[+-]?[0-9]*(\\.[0-9]*)?")

/** `ALLOW_LONG_KEYS` 开启后的简单 key 最大长度（上游实现 值）。 */
private const val MAX_LONG_KEY_LENGTH = 1024

/**
 * 将文档树写成 YAML 字符串。
 *
 * 使用与 [YamlConfig] 默认值一致的写出特性（对齐 上游实现 YAML 2.15 默认）。
 *
 * @param pretty 是否使用块格式输出；默认使用流式紧凑格式
 * @return YAML 文本
 */
public fun Node.toYamlString(pretty: Boolean = false): String = YamlWriter(YamlWriteSettings.DEFAULT, pretty).write(this)
