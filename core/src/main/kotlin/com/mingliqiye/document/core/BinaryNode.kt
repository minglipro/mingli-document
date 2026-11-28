package com.mingliqiye.document.core

/**
 * 二进制节点契约。
 *
 * 通过工厂方法决定持有方式：
 * - [of] 复制传入数组：节点与调用方互不影响，是安全的默认选择；
 * - [ofShared] / [ofUnsafe] 零拷贝持有传入数组：不做复制，调用方在节点生命周期内
 *   不得修改该数组，否则会破坏节点的不可变约定（内容、`equals`/`hashCode` 都会变化）。
 */
public interface BinaryNode : Node {

    override val type: NodeType
        get() = NodeType.BINARY

    /**
     * 二进制内容。
     *
     * 由 [of] 创建的节点每次访问都返回副本（调用方修改返回值不影响节点）；
     * 由 [ofShared] / [ofUnsafe] 创建的节点直接返回创建时传入的数组，调用方必须把
     * 它视为只读。
     */
    public val value: ByteArray

    override fun asBinary(): ByteArray = value

    public companion object {

        /**
         * 创建二进制节点，内部复制传入数组。
         *
         * 传入数组之后的修改不会影响节点，适用于调用方可能复用/修改缓冲区的场景。
         *
         * @param value 二进制内容，会被复制
         * @return 持有副本的二进制节点
         */
        @JvmStatic
        public fun of(value: ByteArray): BinaryNode = DefaultBinaryNode(value.copyOf(), shared = false)

        /**
         * 零拷贝创建二进制节点，直接持有传入数组而不复制。
         *
         * 适用于缓冲区内容不再变化、且希望省去一次复制的场景（例如从解析器接管
         * 刚分配的字节数组）。调用方必须遵守不可变约定：
         * - 节点生命周期内不得修改传入数组；
         * - 修改数组会让节点内容、`equals`/`hashCode` 随之变化；
         * - 需要防御性复制时改用 [of]。
         *
         * @param value 二进制内容，不会被复制，调用方不得再修改
         * @return 直接持有 [value] 的二进制节点
         */
        @JvmStatic
        public fun ofShared(value: ByteArray): BinaryNode = DefaultBinaryNode(value, shared = true)

        /**
         * [ofShared] 的别名，强调“不复制、需要调用方保证安全”的语义。
         *
         * @param value 二进制内容，不会被复制，调用方不得再修改
         * @return 直接持有 [value] 的二进制节点
         */
        @JvmStatic
        public fun ofUnsafe(value: ByteArray): BinaryNode = ofShared(value)
    }
}
