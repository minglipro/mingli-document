package com.mingliqiye.document.format.toml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode

/**
 * 按源文本语句顺序重排文档树。
 *
 * tomlj 1.0.0 的 `TomlTable` 内部使用 `HashMap`，key 迭代顺序与源文本无关；为了让保留注释的
 * 写出结果与源文本顺序一致（注释按路径重新挂回语句，因此顺序必须稳定），读取时用扫描器记录的
 * 语句顺序重建对象节点。内联 table 内部的键顺序无法从语句顺序得到（源文本里没有独立语句），
 * 这部分保持 tomlj 的顺序，属于已知限制。
 *
 * 该重排只改变对象字段顺序，不改变任何节点值；[ObjectNode] 的相等性与顺序无关，因此与
 * [TomlReader] 的结果仍然相等。
 */
internal object TomlNodeOrderer {

    /**
     * 重排文档树。
     *
     * @param root tomlj 解析出的文档树
     * @param events 按源文本顺序记录的语句路径
     * @param structure 表结构信息（用于识别数组表，决定元素下标上下文）
     * @return 按源文本顺序排列的文档树
     */
    fun reorder(
        root: ObjectNode,
        events: List<TomlPathEvent>,
        structure: TomlDocumentStructure,
    ): ObjectNode = reorderObject(root, emptyList(), -1, events, structure)

    private fun reorderObject(
        node: ObjectNode,
        path: List<String>,
        tableIndex: Int,
        events: List<TomlPathEvent>,
        structure: TomlDocumentStructure,
    ): ObjectNode {
        val ordered = LinkedHashMap<String, Node>(node.size)
        for (key in childOrder(node, path, tableIndex, events)) {
            val value = node[key] ?: continue
            ordered[key] = reorderNode(value, path + key, tableIndex, events, structure)
        }
        for ((key, value) in node.asMap()) {
            if (!ordered.containsKey(key)) {
                ordered[key] = reorderNode(value, path + key, tableIndex, events, structure)
            }
        }
        return ObjectNode.from(ordered)
    }

    private fun reorderNode(
        node: Node,
        path: List<String>,
        tableIndex: Int,
        events: List<TomlPathEvent>,
        structure: TomlDocumentStructure,
    ): Node = when (node) {
        is ObjectNode -> reorderObject(node, path, tableIndex, events, structure)
        is ArrayNode -> reorderArray(node, path, tableIndex, events, structure)
        else -> node
    }

    private fun reorderArray(
        node: ArrayNode,
        path: List<String>,
        tableIndex: Int,
        events: List<TomlPathEvent>,
        structure: TomlDocumentStructure,
    ): ArrayNode {
        val elements = ArrayList<Node>(node.size)
        if (structure.arrayTablePaths.contains(path)) {
            for (index in 0 until node.size) {
                val element = node[index]
                elements.add(
                    if (element is ObjectNode) {
                        reorderObject(element, path, index, events, structure)
                    } else {
                        element
                    },
                )
            }
        } else {
            for (index in 0 until node.size) {
                elements.add(reorderNode(node[index], path, tableIndex, events, structure))
            }
        }
        return ArrayNode.from(elements)
    }

    private fun childOrder(
        node: ObjectNode,
        path: List<String>,
        tableIndex: Int,
        events: List<TomlPathEvent>,
    ): List<String> {
        val order = LinkedHashSet<String>()
        for (event in events) {
            if (event.path.size <= path.size) {
                continue
            }
            if (event.path.subList(0, path.size) != path) {
                continue
            }
            if (tableIndex >= 0 && event.tableIndex != tableIndex) {
                continue
            }
            val child = event.path[path.size]
            if (node.containsKey(child)) {
                order.add(child)
            }
        }
        return order.toList()
    }
}
