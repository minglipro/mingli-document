package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ByteArrayPool
import com.mingliqiye.document.core.Node

/**
 * CBOR sequence 多值写出器。
 *
 * 把多个文档树按给定顺序写成 CBOR sequence（RFC 8742）：各数据项直接首尾相接，
 * 不需要长度前缀或分隔符。空集合写出空字节数组，读回为空列表。单值写出入口
 * [CborWriter] 的语义保持不变。
 *
 * @param canonical 是否按 canonical map key 顺序写出
 * @param preserveTags 是否识别并写出 CBOR tag 包装对象，默认 `false`
 * @param bufferPool 可选的中间缓冲区池，默认 `null`（不池化）
 * @param writeFeatures CBOR 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
 */
public class CborSequenceWriter @JvmOverloads constructor(
    private val canonical: Boolean = false,
    private val preserveTags: Boolean = false,
    private val bufferPool: ByteArrayPool? = null,
    private val writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
) {

    /**
     * 按顺序写出全部节点并返回 CBOR sequence 字节。
     *
     * @param nodes 要写出的文档树，按迭代顺序输出
     * @return CBOR sequence 二进制内容；空集合返回空字节数组
     * @throws com.mingliqiye.document.format.cbor.exception.CborException
     *   生成 CBOR 失败，或节点包含非法数字时抛出
     */
    public fun writeAll(nodes: Iterable<Node>): ByteArray = CborWriter(
        canonical = canonical,
        preserveTags = preserveTags,
        bufferPool = bufferPool,
        writeFeatures = writeFeatures,
    ).writeAll(nodes)

    public companion object {

        /**
         * 把多个文档树写成 CBOR sequence 字节。
         *
         * @param nodes 要写出的文档树，按迭代顺序输出
         * @param canonical 是否按 canonical map key 顺序写出，默认 `false`
         * @param preserveTags 是否识别并写出 CBOR tag 包装对象，默认 `false`
         * @param bufferPool 可选的中间缓冲区池，默认 `null`（不池化）
         * @param writeFeatures CBOR 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
         * @return CBOR sequence 二进制内容
         * @throws com.mingliqiye.document.format.cbor.exception.CborException
         *   生成 CBOR 失败，或节点包含非法数字时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun formatAll(
            nodes: Iterable<Node>,
            canonical: Boolean = false,
            preserveTags: Boolean = false,
            bufferPool: ByteArrayPool? = null,
            writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
        ): ByteArray = CborSequenceWriter(canonical, preserveTags, bufferPool, writeFeatures).writeAll(nodes)
    }
}

/**
 * 将多个文档树写成 CBOR sequence 字节。
 *
 * 各数据项直接首尾相接；空集合返回空字节数组。接收 [Collection] 而不是任意
 * [Iterable]，避免把本身是 [Iterable] 的 [com.mingliqiye.document.core.ArrayNode]
 * 误当作"多个数据项"；需要把单个数组节点写成一个数据项时请使用 `listOf(node)`
 * 或 [CborSequenceWriter.writeAll]。
 *
 * @param canonical 是否按 canonical map key 顺序写出，默认 `false`
 * @param preserveTags 是否识别并写出 CBOR tag 包装对象，默认 `false`
 * @param bufferPool 可选的中间缓冲区池，默认 `null`（不池化）
 * @param writeFeatures CBOR 写出特性集合，默认 [CborConfig.DEFAULT_WRITE_FEATURES]
 * @return CBOR sequence 二进制内容
 */
public fun Collection<Node>.toCborSequenceBytes(
    canonical: Boolean = false,
    preserveTags: Boolean = false,
    bufferPool: ByteArrayPool? = null,
    writeFeatures: Set<CborWriteFeature> = CborConfig.DEFAULT_WRITE_FEATURES,
): ByteArray = CborSequenceWriter(canonical, preserveTags, bufferPool, writeFeatures).writeAll(this)
