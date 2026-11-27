package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.toml.exception.TomlParseException
import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseError
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * TOML 读取入口。
 *
 * 使用 tomlj 解析 TOML 文本，并把 `TomlTable` / `TomlArray` 还原为通用文档树。
 * TOML 的日期时间类型（offset date-time、local date-time、local date、local time）
 * 在文档树中没有对应类型，统一写成 ISO-8601 字符串。
 *
 * TOML 文档根必须是表；合法文档解析后根节点总是 [ObjectNode]。
 *
 * @param source TOML 文本
 */
public class TomlReader(
    private val source: String,
) {

    /**
     * 解析并返回文档树。
     *
     * @return 根对象节点
     * @throws TomlParseException TOML 文本非法、根不是表或包含不支持的值类型时抛出
     */
    public fun read(): Node {
        val result = parseResult()
        return toObjectNode(rootTable(result))
    }

    private fun parseResult(): TomlParseResult {
        val result = try {
            Toml.parse(source)
        } catch (e: TomlParseError) {
            throw parseException(e)
        } catch (e: RuntimeException) {
            throw TomlParseException("Invalid TOML: ${e.message}", e)
        }
        if (result.hasErrors()) {
            throw parseException(result.errors().first())
        }
        return result
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

    private fun parseException(error: TomlParseError): TomlParseException {
        val position = error.position()
        return TomlParseException(
            "Invalid TOML at line ${position.line()}, column ${position.column()}: ${error.message}",
            error,
        )
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
        is OffsetDateTime -> StringNode.of(value.toString())
        is LocalDateTime -> StringNode.of(value.toString())
        is LocalDate -> StringNode.of(value.toString())
        is LocalTime -> StringNode.of(value.toString())
        else -> throw TomlParseException("Unsupported TOML value type: ${value?.javaClass?.name ?: "null"}")
    }

    private fun toDoubleNode(value: Double): NumberNode {
        if (!value.isFinite()) {
            throw TomlParseException("TOML NaN/Infinity numbers are not supported")
        }
        return NumberNode.of(value)
    }

    public companion object {

        /**
         * 解析 TOML 字符串。
         *
         * @param source TOML 文本
         * @return 文档树
         * @throws TomlParseException TOML 文本非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = TomlReader(source).read()
    }
}

/**
 * 将 TOML 字符串解析为文档树。
 *
 * @return 文档树
 * @throws TomlParseException TOML 文本非法时抛出
 */
public fun String.toTomlNode(): Node = TomlReader(this).read()
