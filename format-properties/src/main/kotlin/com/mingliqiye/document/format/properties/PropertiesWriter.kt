package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.properties.exception.PropertiesException

/**
 * 将文档树写成 Java Properties 文本。
 *
 * 根节点必须是对象或数组；对象字段使用 `.` 路径，数组使用 `[index]`。
 */
internal class PropertiesWriter {

    private val lines = ArrayList<String>()

    fun write(node: Node): String {
        lines.clear()
        when (node) {
            is ObjectNode -> flattenObject(node, null)
            is ArrayNode -> flattenArray(node, null)
            else -> throw PropertiesException("Properties root must be an object or array")
        }
        return lines.joinToString("\n")
    }

    private fun flattenObject(node: ObjectNode, parent: String?) {
        for ((key, value) in node.asMap()) {
            flattenNode(value, PropertiesPath.join(parent, key))
        }
    }

    private fun flattenArray(node: ArrayNode, parent: String?) {
        for (index in 0 until node.size) {
            val path = if (parent == null) {
                PropertiesPath.rootIndex(index)
            } else {
                PropertiesPath.appendIndex(parent, index)
            }
            flattenNode(node[index], path)
        }
    }

    private fun flattenNode(node: Node, path: String) {
        when (node) {
            is ObjectNode -> flattenObject(node, path)
            is ArrayNode -> flattenArray(node, path)
            is NullNode -> addLine(path, "")
            is StringNode -> addLine(path, node.value)
            is NumberNode -> addLine(path, node.raw)
            is BooleanNode -> addLine(path, node.value.toString())
        }
    }

    private fun addLine(path: String, value: String) {
        lines.add(PropertiesPath.escapePropertyKey(path) + "=" + PropertiesPath.escapePropertyValue(value))
    }
}

/**
 * 将文档树写成 Java Properties 字符串。
 *
 * @return Properties 文本
 */
public fun Node.toPropertiesString(): String = PropertiesWriter().write(this)
