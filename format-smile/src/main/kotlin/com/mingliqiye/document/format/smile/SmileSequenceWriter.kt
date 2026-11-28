package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.Node

/**
 * Smile 多消息流写出器。
 *
 * 把多个文档树按给定顺序写成连续的 Smile 根值（多消息流）：Smile 头部只在流
 * 开头写一次，各根值直接首尾相接，不需要长度前缀或分隔符。空集合写出空字节数组，
 * 读回为空列表。单值写出入口 [SmileWriter] 的语义保持不变。
 */
public class SmileSequenceWriter {

    /**
     * 按顺序写出全部节点并返回 Smile 多消息流字节。
     *
     * @param nodes 要写出的文档树，按迭代顺序输出
     * @return Smile 多消息流二进制内容；空集合返回空字节数组
     * @throws com.mingliqiye.document.format.smile.exception.SmileException
     *   节点包含无法编码的内容时抛出
     */
    public fun writeAll(nodes: Iterable<Node>): ByteArray = SmileWriter().writeAll(nodes)

    public companion object {

        /**
         * 把多个文档树写成 Smile 多消息流字节。
         *
         * @param nodes 要写出的文档树，按迭代顺序输出
         * @return Smile 多消息流二进制内容；空集合返回空字节数组
         * @throws com.mingliqiye.document.format.smile.exception.SmileException
         *   节点包含无法编码的内容时抛出
         */
        @JvmStatic
        public fun formatAll(nodes: Iterable<Node>): ByteArray = SmileSequenceWriter().writeAll(nodes)
    }
}

/**
 * 将多个文档树写成 Smile 多消息流字节。
 *
 * 各根值直接首尾相接；空集合返回空字节数组。接收 [Collection] 而不是任意
 * [Iterable]，避免把本身是 [Iterable] 的 [com.mingliqiye.document.core.ArrayNode]
 * 误当作"多个消息"；需要把单个数组节点写成一个根值时请使用 `listOf(node)`
 * 或 [SmileSequenceWriter.writeAll]。
 *
 * @return Smile 多消息流二进制内容
 */
public fun Collection<Node>.toSmileSequenceBytes(): ByteArray = SmileSequenceWriter().writeAll(this)
