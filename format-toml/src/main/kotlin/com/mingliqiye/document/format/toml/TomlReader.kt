package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.toml.exception.TomlParseException
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal

/**
 * TOML 读取入口。
 *
 * 使用 tomlj 解析 TOML 文本，并把 `TomlTable` / `TomlArray` 还原为通用文档树。
 *
 * 日期时间（offset date-time、local date-time、local date、local time）在 Node 模型中
 * 没有对应类型，读取为字符串。默认（[TomlReadFeature.PARSE_JAVA_TIME] 关闭）返回
 * TOML 源码原文，仅把日期时间分隔符 `T`/空格与 `z`/`Z` 归一化，与 上游实现 TOML 的
 * 默认字符串行为一致；开启 [TomlReadFeature.PARSE_JAVA_TIME] 后返回经 `java.time`
 * 规范化的 ISO-8601 字符串（上游实现 以 embedded object 返回，Node 模型无法表达，
 * 详见 format-toml README）。
 *
 * TOML 文档根必须是表；合法文档解析后根节点总是 [ObjectNode]。
 *
 * 解析深度：默认安全上限 [maxDepth] 为 [TomlConfig.DEFAULT_MAX_DEPTH]（512，根表深度
 * 为 `1`）。解析前先做词法预扫描拒绝超深数组/内联 table，解析后再对结果树做迭代
 * 深度校验，超限统一抛 [TomlParseException]，不会 StackOverflow。
 *
 * @param source TOML 文本
 * @param maxDepth 最大嵌套深度，默认 [TomlConfig.DEFAULT_MAX_DEPTH]；
 *   `0` 或负数表示不限制（显式关闭保护）
 * @param readFeatures TOML 读取特性集合，默认 [TomlConfig.DEFAULT_READ_FEATURES]
 */
public class TomlReader @JvmOverloads constructor(
    private val source: String,
    private val maxDepth: Int = TomlConfig.DEFAULT_MAX_DEPTH,
    private val readFeatures: Set<TomlReadFeature> = TomlConfig.DEFAULT_READ_FEATURES,
) {

    private val parseJavaTime: Boolean = TomlReadFeature.PARSE_JAVA_TIME in readFeatures
    private val validateDateTime: Boolean = TomlReadFeature.VALIDATE_DATE_TIME in readFeatures
    private val temporalText: TomlTemporalText by lazy(LazyThreadSafetyMode.NONE) { TomlTemporalText.scan(source) }

    /**
     * 解析并返回文档树。
     *
     * @return 根对象节点
     * @throws TomlParseException TOML 文本非法、根不是表、包含不支持的值类型或
     *   嵌套深度超过 [maxDepth] 时抛出
     */
    public fun read(): Node {
        val result = TomlParser.parse(source, maxDepth)
        return toObjectNode(rootTable(result))
    }

    /**
     * 强制根节点为表。
     *
     * [TomlParseResult] 本身继承 [TomlTable]，这里保留显式校验，避免解析器返回
     * 非表根节点时得到含糊的类型错误。
     */
    private fun rootTable(result: TomlParseResult): TomlTable {
        val root: Any? = result
        return root as? TomlTable ?: throw TomlParseException("TOML document root must be a table")
    }

    private fun toObjectNode(table: TomlTable): ObjectNode {
        val fields = LinkedHashMap<String, Node>(table.size())
        for (key in table.keySet()) {
            // 必须按 List 路径读取：TomlTable.get(String) 会把 key 当成点分路径解析，
            // 含 `.`、引号或为空的字面量 key 会被误解析。
            fields[key] = toNode(table.get(listOf(key)))
        }
        return ObjectNode.from(fields)
    }

    private fun toArrayNode(array: TomlArray): ArrayNode {
        val elements = ArrayList<Node>(array.size())
        for (index in 0 until array.size()) {
            elements.add(toNode(array.get(index)))
        }
        return ArrayNode.from(elements)
    }

    private fun toNode(value: Any?): Node = when (value) {
        is TomlTable -> toObjectNode(value)
        is TomlArray -> toArrayNode(value)
        is String -> StringNode.of(value)
        is Boolean -> BooleanNode.of(value)
        is Long -> NumberNode.of(value)
        is Double -> toDoubleNode(value)
        is OffsetDateTime -> toTemporalNode(value)
        is LocalDateTime -> toTemporalNode(value)
        is LocalDate -> toTemporalNode(value)
        is LocalTime -> toTemporalNode(value)
        else -> throw TomlParseException("Unsupported TOML value type: ${value?.javaClass?.name ?: "null"}")
    }

    private fun toDoubleNode(value: Double): NumberNode {
        if (!value.isFinite()) {
            throw TomlParseException("TOML NaN/Infinity numbers are not supported")
        }
        return NumberNode.of(value)
    }

    /**
     * 日期时间 -> StringNode。
     *
     * - `PARSE_JAVA_TIME` 关闭（默认）：返回源码原文（已归一化分隔符/时区大小写）；
     * - `PARSE_JAVA_TIME` 开启：返回 `java.time` 规范化 ISO-8601 字符串；
     * - `VALIDATE_DATE_TIME` 开启：额外用 `java.time` 复核一遍，失败抛 [TomlParseException]。
     */
    private fun toTemporalNode(value: Temporal): Node {
        val text = if (parseJavaTime) value.toString() else temporalText.rawFor(value) ?: value.toString()
        if (parseJavaTime || validateDateTime) {
            validateTemporal(text, value)
        }
        return StringNode.of(text)
    }

    private fun validateTemporal(text: String, value: Temporal) {
        try {
            when (value) {
                is OffsetDateTime -> OffsetDateTime.parse(text)
                is LocalDateTime -> LocalDateTime.parse(text)
                is LocalDate -> LocalDate.parse(text)
                else -> LocalTime.parse(text)
            }
        } catch (e: DateTimeParseException) {
            throw TomlParseException("Invalid TOML date/time value '$text': ${e.message}", e)
        }
    }

    public companion object {

        /**
         * 解析 TOML 字符串。
         *
         * @param source TOML 文本
         * @param maxDepth 最大嵌套深度，默认 [TomlConfig.DEFAULT_MAX_DEPTH]
         * @param readFeatures TOML 读取特性集合，默认 [TomlConfig.DEFAULT_READ_FEATURES]
         * @return 文档树
         * @throws TomlParseException TOML 文本非法或嵌套深度超限时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun parse(
            source: String,
            maxDepth: Int = TomlConfig.DEFAULT_MAX_DEPTH,
            readFeatures: Set<TomlReadFeature> = TomlConfig.DEFAULT_READ_FEATURES,
        ): Node = TomlReader(source, maxDepth, readFeatures).read()
    }
}

/**
 * 将 TOML 字符串解析为文档树。
 *
 * @param maxDepth 最大嵌套深度，默认 [TomlConfig.DEFAULT_MAX_DEPTH]
 * @param readFeatures TOML 读取特性集合，默认 [TomlConfig.DEFAULT_READ_FEATURES]
 * @return 文档树
 * @throws TomlParseException TOML 文本非法或嵌套深度超限时抛出
 */
public fun String.toTomlNode(
    maxDepth: Int = TomlConfig.DEFAULT_MAX_DEPTH,
    readFeatures: Set<TomlReadFeature> = TomlConfig.DEFAULT_READ_FEATURES,
): Node = TomlReader(this, maxDepth, readFeatures).read()
