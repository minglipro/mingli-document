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
 * Properties 读取入口。
 *
 * 平铺 key 会按 `.` / `[index]` 还原成对象和数组。
 *
 * 解析深度：key 路径的段数决定对象/数组嵌套深度（根对象深度为 `1`，`a.b.c=1` 的
 * 最深层级为 `4`）。超过 [maxDepth] 时抛出 [PropertiesParseException]，并在构建
 * 中间结构前完成校验，避免超深输入 StackOverflow。
 *
 * @param source Properties 文本
 * @param maxDepth 最大嵌套深度，默认 [PropertiesMapper.DEFAULT_MAX_DEPTH]（512）；
 *   `0` 或负数表示不限制（显式关闭保护）
 */
public class PropertiesReader(
    private val source: String,
    private val maxDepth: Int = PropertiesMapper.DEFAULT_MAX_DEPTH,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws PropertiesParseException Properties 格式非法时抛出
     */
    public fun read(): Node {
        val properties = Properties()
        try {
            properties.load(StringReader(source))
        } catch (e: Exception) {
            throw PropertiesParseException("Invalid Properties: ${e.message}", e)
        }
        val names = properties.stringPropertyNames().sorted()
        if (names.isEmpty()) {
            return ObjectNode.EMPTY
        }
        val root = arrayOfNulls<Any?>(1)
        for (name in names) {
            val segments = PropertiesPath.parse(name)
            checkDepth(segments, name)
            val value = inferScalar(properties.getProperty(name))
            if (segments.first().isIndex) {
                root[0] = insert(root[0] as? MutableList<Any?>, segments, 0, value, name)
            } else {
                @Suppress("UNCHECKED_CAST")
                root[0] = insert(root[0] as? MutableMap<String, Any?>, segments, 0, value, name)
            }
        }
        return toNode(root[0])
    }

    /**
     * 校验单个 key 路径的最大嵌套深度。
     *
     * 深度定义为根对象 `1` 加上路径段数，与 Node 树逐节点深度一致。
     */
    private fun checkDepth(segments: List<PropertiesPathSegment>, name: String) {
        if (maxDepth <= 0) {
            return
        }
        val depth = segments.size + 1
        if (depth > maxDepth) {
            throw PropertiesParseException("Properties key path '$name' depth $depth exceeds maxDepth $maxDepth")
        }
    }

    private fun insert(
        current: Any?,
        segments: List<PropertiesPathSegment>,
        depth: Int,
        value: Any?,
        path: String,
    ): Any? {
        if (depth == segments.size) {
            return value
        }
        val segment = segments[depth]
        return if (segment.isIndex) {
            val list = current as? MutableList<Any?> ?: if (current == null) {
                ArrayList()
            } else {
                throw conflict(path)
            }
            while (list.size <= segment.index) {
                list.add(null)
            }
            list[segment.index] = insert(list[segment.index], segments, depth + 1, value, path)
            list
        } else {
            val map = current as? MutableMap<String, Any?> ?: if (current == null) {
                LinkedHashMap()
            } else {
                throw conflict(path)
            }
            val key = segment.key ?: throw conflict(path)
            map[key] = insert(map[key], segments, depth + 1, value, path)
            map
        }
    }

    private fun conflict(path: String): PropertiesParseException = PropertiesParseException("Conflicting Properties key path '$path'")

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

    private fun toNode(value: Any?): Node = when (value) {
        null -> ObjectNode.EMPTY
        is Node -> value
        is Map<*, *> -> {
            val fields = LinkedHashMap<String, Node>(value.size)
            for ((key, child) in value) {
                fields[key.toString()] = toNode(child)
            }
            ObjectNode.from(fields)
        }

        is List<*> -> {
            val elements = ArrayList<Node>(value.size)
            for (element in value) {
                elements.add(toNode(element))
            }
            ArrayNode.from(elements)
        }

        else -> throw PropertiesParseException("Unsupported Properties tree value: ${value.javaClass.name}")
    }

    public companion object {

        /**
         * 解析 Properties 字符串。
         *
         * @param source Properties 文本
         * @return 文档树
         * @throws PropertiesParseException Properties 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = PropertiesReader(source).read()
    }
}

/**
 * 将字符串解析为文档树。
 *
 * @return 文档树
 * @throws PropertiesParseException Properties 格式非法时抛出
 */
public fun String.toPropertiesNode(): Node = PropertiesReader(this).read()
