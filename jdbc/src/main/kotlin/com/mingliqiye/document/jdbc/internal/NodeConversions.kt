package com.mingliqiye.document.jdbc.internal

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jdbc.exception.JdbcWriteException
import com.mingliqiye.document.serialization.DocumentMapper
import java.math.BigDecimal
import java.math.BigInteger
import java.util.ArrayList
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.UUID

/**
 * Java 值与 [Node] 之间的转换辅助。
 *
 * 写入端用于把 `Map` 输入中的原始 Java 值转换为节点；行映射器用于把节点转换为
 * 普通 Java 值。
 */
internal object NodeConversions {

    /**
     * 把 Map 输入中的原始 Java 值转换为节点。
     *
     * 标量、日期/时间（ISO-8601 字符串）、二进制、枚举、UUID、Map、Iterable、数组直接
     * 转换；其它对象需要 [documentMapper] 才能转换。
     */
    internal fun fromJavaValue(value: Any?, documentMapper: DocumentMapper?): Node = when (value) {
        null -> NullNode.INSTANCE
        is Node -> value
        is CharSequence -> StringNode.of(value.toString())
        is Char -> StringNode.of(value.toString())
        is Boolean -> BooleanNode.of(value)
        is Byte -> NumberNode.of(value.toInt())
        is Short -> NumberNode.of(value.toInt())
        is Int -> NumberNode.of(value)
        is Long -> NumberNode.of(value)
        is Float -> NumberNode.of(value)
        is Double -> NumberNode.of(value)
        is BigInteger -> NumberNode.of(value)
        is BigDecimal -> NumberNode.of(value)
        is Number -> NumberNode.of(BigDecimal(value.toString()))
        is ByteArray -> BinaryNode.of(value)
        is Enum<*> -> StringNode.of(value.name)
        is Map<*, *> -> fromJavaMap(value, documentMapper)
        is Iterable<*> -> fromJavaIterable(value, documentMapper)
        is Array<*> -> fromJavaIterable(value.asList(), documentMapper)
        else -> fromOtherJavaValue(value, documentMapper)
    }

    private fun fromJavaMap(map: Map<*, *>, documentMapper: DocumentMapper?): ObjectNode {
        val fields = LinkedHashMap<String, Node>(map.size)
        for ((key, element) in map) {
            val fieldName = key as? String
                ?: throw JdbcWriteException("Map 的 key 必须是 String，实际为 ${key?.javaClass?.name ?: "null"}")
            fields[fieldName] = fromJavaValue(element, documentMapper)
        }
        return ObjectNode.from(fields)
    }

    private fun fromJavaIterable(elements: Iterable<*>, documentMapper: DocumentMapper?): ArrayNode {
        val nodes = ArrayList<Node>()
        for (element in elements) {
            nodes.add(fromJavaValue(element, documentMapper))
        }
        return ArrayNode.from(nodes)
    }

    private fun fromOtherJavaValue(value: Any, documentMapper: DocumentMapper?): Node {
        formatTemporal(value)?.let { return StringNode.of(it) }
        if (value is UUID) {
            return StringNode.of(value.toString())
        }
        val mapper = documentMapper
            ?: throw JdbcWriteException("Map 中的值 ${value.javaClass.name} 需要 DocumentMapper 才能转换为 Node")
        return mapper.toNode(value)
    }

    /**
     * 把日期/时间对象格式化为 ISO-8601 字符串；不是日期/时间类型时返回 `null`。
     */
    internal fun formatTemporal(value: Any): String? = when (value) {
        is java.sql.Date -> value.toLocalDate().toString()
        is java.sql.Time -> value.toLocalTime().toString()
        is java.sql.Timestamp -> value.toLocalDateTime().toString()
        is java.time.LocalDate -> value.toString()
        is java.time.LocalTime -> value.toString()
        is java.time.LocalDateTime -> value.toString()
        is java.time.OffsetDateTime -> value.toString()
        is java.time.OffsetTime -> value.toString()
        is java.time.ZonedDateTime -> value.toString()
        is java.time.Instant -> value.toString()
        is java.util.Date -> value.toInstant().toString()
        is Calendar -> value.toInstant().toString()
        else -> null
    }

    /**
     * 把节点转换为普通 Java 值：对象 -> Map，数组 -> List，数字 -> BigDecimal，
     * 二进制 -> ByteArray。
     */
    internal fun toJavaValue(node: Node): Any? = when (node) {
        is NullNode -> null
        is StringNode -> node.value
        is BooleanNode -> node.value
        is NumberNode -> node.value
        is BinaryNode -> node.value.copyOf()
        is ArrayNode -> toJavaList(node)
        is ObjectNode -> toJavaMap(node)
        else -> throw JdbcWriteException("不支持的节点类型 ${node.type}")
    }

    private fun toJavaList(node: ArrayNode): List<Any?> {
        val values = ArrayList<Any?>(node.size)
        for (element in node) {
            values.add(toJavaValue(element))
        }
        return values
    }

    /**
     * 把对象节点转换为普通 Java Map（保持插入顺序）。
     */
    internal fun toJavaMap(node: ObjectNode): Map<String, Any?> {
        val values = LinkedHashMap<String, Any?>(node.size)
        for ((key, value) in node.entries()) {
            values[key] = toJavaValue(value)
        }
        return values
    }
}
