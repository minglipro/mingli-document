package com.mingliqiye.document.core

import java.util.LinkedHashMap

/**
 * 对象节点契约。
 */
public interface ObjectNode : Node {

    override val type: NodeType
        get() = NodeType.OBJECT

    /** 对象字段数量。 */
    public val size: Int

    /** 字段名集合，保持插入顺序。 */
    public val keys: Set<String>

    /** 判断是否包含指定字段（即使字段值为 null 也返回 true）。 */
    public fun containsKey(key: String): Boolean

    /**
     * 读取字段。
     *
     * @param key 字段名
     * @return 字段值；不存在时返回 `null`
     */
    public operator fun get(key: String): Node?

    /** 字段条目集合，保持插入顺序。 */
    public fun entries(): Set<Map.Entry<String, Node>>

    /** 转为不可变 Map。 */
    public fun asMap(): Map<String, Node>

    override fun asObject(): ObjectNode = this

    public companion object {

        /** 空对象。 */
        @JvmField
        public val EMPTY: ObjectNode = DefaultObjectNode(LinkedHashMap())

        /**
         * 从键值对创建对象；重复 key 以后出现的值为准。
         */
        @JvmStatic
        public fun of(vararg entries: Pair<String, Node>): ObjectNode = DefaultObjectNode.of(*entries)

        /**
         * 从 Map 创建对象，保持 Map 的迭代顺序。
         */
        @JvmStatic
        public fun from(map: Map<String, Node>): ObjectNode = DefaultObjectNode.from(map)
    }
}
