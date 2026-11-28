package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node

/**
 * Smile 多消息流读取入口。
 *
 * Smile 字节流允许连续出现多个根值（多消息流），本类按出现顺序返回全部文档树。
 * 单值入口 [SmileReader] 的语义保持不变：它仍然要求输入恰好包含一个根值。
 *
 * 边界约定：
 * - 空输入即零个根值的合法流，返回空列表；
 * - 只有一个根值时返回长度为 1 的列表；
 * - 任一消息格式非法或数据不完整时整体抛出 [SmileParseException]，不返回部分结果；
 * - 每个根值独立解析，深度从 1 重新计数。
 *
 * @param source Smile 多消息流二进制内容
 * @param maxDepth 单个根值的 Node 树最大深度；0 表示不限制
 * @param maxItems 最大消息数量；0 表示不限制
 */
public class SmileSequenceReader @JvmOverloads constructor(
    private val source: ByteArray,
    private val maxDepth: Int = 0,
    private val maxItems: Int = 0,
) {

    /**
     * 解析并返回多消息流中全部文档树。
     *
     * @return 按出现顺序排列的文档树列表；空输入返回空列表
     * @throws SmileParseException 任一消息格式非法、数据不完整、消息数超过
     *   maxItems，或包含 NaN/Infinity 等无法表示的浮点值时抛出
     */
    public fun readAll(): List<Node> = SmileReader(source, maxDepth).readAll(maxItems)

    public companion object {

        /**
         * 解析 Smile 多消息流字节数组。
         *
         * @param source Smile 多消息流二进制内容
         * @param maxDepth 单个根值的 Node 树最大深度；0 表示不限制
         * @param maxItems 最大消息数量；0 表示不限制
         * @return 按出现顺序排列的文档树列表；空输入返回空列表
         * @throws SmileParseException 任一消息格式非法、数据不完整、消息数超过
         *   maxItems，或包含 NaN/Infinity 等无法表示的浮点值时抛出
         */
        @JvmStatic
        @JvmOverloads
        public fun parseAll(
            source: ByteArray,
            maxDepth: Int = 0,
            maxItems: Int = 0,
        ): List<Node> = SmileSequenceReader(source, maxDepth, maxItems).readAll()
    }
}

/**
 * 将 Smile 多消息流字节数组解析为文档树列表。
 *
 * 空输入返回空列表；任一消息格式非法时抛出 [SmileParseException]。
 *
 * @return 按出现顺序排列的文档树列表
 */
public fun ByteArray.toSmileNodes(): List<Node> = SmileSequenceReader(this).readAll()

/**
 * 按 reified 泛型参数把 Smile 多消息流解析为实体列表。
 *
 * 用法：`mapper.parseAll<Person>(bytes)`；需要非 reified 泛型时使用
 * [SmileMapper.parseAll] 的 `DocumentTypeReference` 重载。
 *
 * @param source Smile 多消息流二进制内容
 * @param maxItems 最大消息数量；0 表示不限制
 * @return 按出现顺序反序列化得到的实体列表
 */
public inline fun <reified T> SmileMapper.parseAll(source: ByteArray, maxItems: Int = 0): List<T> = parseAll(source, DocumentTypeReference.of<T>(), maxItems)
