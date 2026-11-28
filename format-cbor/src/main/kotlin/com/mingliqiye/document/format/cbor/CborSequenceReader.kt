package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node

/**
 * CBOR sequence 多值读取入口。
 *
 * CBOR sequence（RFC 8742）是零个或多个自描述 CBOR 数据项首尾相接的字节流，
 * 本类按出现顺序返回全部文档树。单值入口 [CborReader] 的语义保持不变：它仍然
 * 要求输入恰好包含一个顶层数据项。
 *
 * 边界约定：
 * - 空输入即零个数据项的合法 sequence，返回空列表；
 * - 只有一个数据项时返回长度为 1 的列表；
 * - 任一数据项格式非法或数据不完整时整体抛出 [CborParseException]，不返回部分结果；
 * - 每个数据项独立解析，深度从 1 重新计数；标签保真开关 [preserveTags] 对每一项生效。
 *
 * @param source CBOR sequence 二进制内容
 * @param maxDepth 单个数据项的 Node 树最大深度；0 表示不限制
 * @param maxItems 最大数据项数量；0 表示不限制
 * @param preserveTags 是否保留 CBOR tag（包装对象协议），默认 `false`
 */
public class CborSequenceReader @JvmOverloads constructor(
    private val source: ByteArray,
    private val maxDepth: Int = 0,
    private val maxItems: Int = 0,
    private val preserveTags: Boolean = false,
) {

    /**
     * 解析并返回 sequence 中全部文档树。
     *
     * @return 按出现顺序排列的文档树列表；空输入返回空列表
     * @throws com.mingliqiye.document.format.cbor.exception.CborParseException
     *   任一数据项格式非法、数据不完整、项数超过 maxItems，或包含 NaN/Infinity
     *   等无法表示的浮点值时抛出
     */
    public fun readAll(): List<Node> = CborReader(source, maxDepth, preserveTags).readAll(maxItems)

    public companion object {

        /**
         * 解析 CBOR sequence 字节数组。
         *
         * @param source CBOR sequence 二进制内容
         * @param maxDepth 单个数据项的 Node 树最大深度；0 表示不限制
         * @param maxItems 最大数据项数量；0 表示不限制
         * @param preserveTags 是否保留 CBOR tag，默认 `false`
         * @return 按出现顺序排列的文档树列表；空输入返回空列表
         * @throws com.mingliqiye.document.format.cbor.exception.CborParseException
         *   任一数据项格式非法、数据不完整、项数超过 maxItems，或包含 NaN/Infinity
         *   等无法表示的浮点值时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun parseAll(
            source: ByteArray,
            maxDepth: Int = 0,
            maxItems: Int = 0,
            preserveTags: Boolean = false,
        ): List<Node> = CborSequenceReader(source, maxDepth, maxItems, preserveTags).readAll()
    }
}

/**
 * 将 CBOR sequence 字节数组解析为文档树列表。
 *
 * 空输入返回空列表；任一数据项格式非法时抛出
 * [com.mingliqiye.document.format.cbor.exception.CborParseException]。
 *
 * @return 按出现顺序排列的文档树列表
 */
public fun ByteArray.toCborNodes(): List<Node> = CborSequenceReader(this).readAll()

/**
 * 按 reified 泛型参数把 CBOR sequence 解析为实体列表。
 *
 * 用法：`mapper.parseAll<Person>(bytes)`；需要非 reified 泛型时使用
 * [CborMapper.parseAll] 的 `DocumentTypeReference` 重载。
 *
 * @param source CBOR sequence 二进制内容
 * @param maxItems 最大数据项数量；0 表示不限制
 * @return 按出现顺序反序列化得到的实体列表
 */
public inline fun <reified T> CborMapper.parseAll(source: ByteArray, maxItems: Int = 0): List<T> = parseAll(source, DocumentTypeReference.of<T>(), maxItems)
