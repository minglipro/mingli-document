package com.mingliqiye.document.format.properties

import java.util.TreeMap

/**
 * 扁平 Properties key 还原出的轻量路径树节点。
 *
 * 语义对齐 上游实现 `JPropNode`：
 * - 下标子节点按数字排序（读取时下标只用于排序，不表示绝对位置，因而数组是稠密的）；
 * - 同时存在下标子节点与命名字节点时，下标子节点被“降级”为名字（上游实现 语义）；
 * - 节点可以同时有值和子节点；转成 `Node` 时值写成数组首元素或空字符串键字段。
 *
 * 本类只用于 [PropertiesReader] / [PropertiesTokenReader] 的中间结构，不暴露。
 */
internal class PropertiesPathNode {

    /** 叶值原始文本；`null` 表示没有值。 */
    var value: String? = null
        private set

    private var indexChildren: TreeMap<Int, PropertiesPathNode>? = null
    private var nameChildren: LinkedHashMap<String, PropertiesPathNode>? = null

    /** 是否为数组节点（只含下标子节点）。 */
    val isArray: Boolean
        get() = indexChildren != null

    /** 是否含有按名字访问的子节点。 */
    val hasNameChildren: Boolean
        get() = nameChildren != null

    val nullableValue: String?
        get() = value

    fun setValue(value: String): PropertiesPathNode {
        this.value = value
        return this
    }

    fun addByIndex(index: Int): PropertiesPathNode {
        if (nameChildren != null) {
            return addByName(index.toString())
        }
        var children = indexChildren
        if (children == null) {
            children = TreeMap()
            indexChildren = children
        }
        return children.getOrPut(index) { PropertiesPathNode() }
    }

    fun addByName(name: String): PropertiesPathNode {
        var names = nameChildren
        val indexes = indexChildren
        if (indexes != null) {
            names = LinkedHashMap(indexes.size * 2)
            for ((index, child) in indexes) {
                names[index.toString()] = child
            }
            indexChildren = null
        } else if (names == null) {
            names = LinkedHashMap()
        }
        nameChildren = names
        return names.getOrPut(name) { PropertiesPathNode() }
    }

    /** 按 上游实现 `JPropNode.arrayContents()` 的顺序返回下标子节点（升序）。 */
    fun arrayContents(): Collection<PropertiesPathNode> = indexChildren?.values ?: emptyList()

    /** 按插入顺序返回命名字节点。 */
    fun nameContents(): Set<Map.Entry<String, PropertiesPathNode>> = nameChildren?.entries ?: emptySet()
}
