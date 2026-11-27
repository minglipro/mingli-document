package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode

/**
 * CBOR tag 包装对象协议。
 *
 * 保真模式下带 tag 的值表示为
 * `{"$cborTag": <tag 整数>, "value": <Node>}`。
 * 只有对象恰好包含 [CborConfig.TAG_FIELD] 与 [CborConfig.VALUE_FIELD] 两个字段，
 * 且 tag 字段是 `>= 0` 的整数值时，才视为包装对象；其他形态按普通对象处理。
 */
internal object CborTagWrapper {

    /**
     * 用 tag 包装节点。
     *
     * @param tag CBOR tag，必须 `>= 0`
     * @param value 被包装的节点
     * @return 包装对象
     */
    fun wrap(tag: Int, value: Node): ObjectNode = ObjectNode.of(
        CborConfig.TAG_FIELD to NumberNode.ofRaw(tag.toString()),
        CborConfig.VALUE_FIELD to value,
    )

    /**
     * 识别并拆解 tag 包装对象。
     *
     * @param node 待识别对象
     * @return 包装的 tag 与值；不是合法包装时返回 `null`
     */
    fun unwrap(node: ObjectNode): TaggedValue? {
        if (node.size != 2) {
            return null
        }
        val tagNode = node[CborConfig.TAG_FIELD] as? NumberNode ?: return null
        val value = node[CborConfig.VALUE_FIELD] ?: return null
        val tag = parseTag(tagNode.raw) ?: return null
        return TaggedValue(tag, value)
    }

    private fun parseTag(raw: String): Int? {
        if (raw.isEmpty() || raw.any { it !in '0'..'9' }) {
            return null
        }
        return raw.toIntOrNull()
    }

    /**
     * 拆解后的 tag 与值。
     */
    internal class TaggedValue(
        /** CBOR tag。 */
        val tag: Int,
        /** 被 tag 包裹的节点。 */
        val value: Node,
    )
}
