package com.mingliqiye.document.core

/**
 * 数组节点契约。
 */
public interface ArrayNode :
    Node,
    Iterable<Node> {

    override val type: NodeType
        get() = NodeType.ARRAY

    /** 数组元素数量。 */
    public val size: Int

    /**
     * 按下标读取元素。
     *
     * @throws IndexOutOfBoundsException 下标越界时抛出
     */
    public operator fun get(index: Int): Node

    /** 转为不可变 List。 */
    public fun asList(): List<Node>

    override fun asArray(): ArrayNode = this

    public companion object {

        /** 空数组。 */
        @JvmField
        public val EMPTY: ArrayNode = DefaultArrayNode(emptyList())

        /** 从节点数组创建数组。 */
        @JvmStatic
        public fun of(vararg elements: Node): ArrayNode = DefaultArrayNode(elements.toList())

        /** 从可迭代对象创建数组。 */
        @JvmStatic
        public fun from(elements: Iterable<Node>): ArrayNode = DefaultArrayNode(elements.toList())
    }
}
