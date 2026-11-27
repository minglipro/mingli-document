package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node

/**
 * 格式无关的流式文档写出接口。
 *
 * Mapper 直接遍历实体并调用本接口，不构建完整 [Node] 树。
 */
public interface DocumentValueWriter {

    /** 写出 null。 */
    public fun writeNull()

    /** 写出字符串。 */
    public fun writeString(value: String)

    /** 写出数字原始文本。 */
    public fun writeNumber(raw: String)

    /** 写出 int。 */
    public fun writeInt(value: Int)

    /** 写出 long。 */
    public fun writeLong(value: Long)

    /** 写出 double。 */
    public fun writeDouble(value: Double)

    /** 写出布尔值。 */
    public fun writeBoolean(value: Boolean)

    /** 开始对象。 */
    public fun startObject()

    /** 结束对象。 */
    public fun endObject()

    /** 开始数组。 */
    public fun startArray()

    /** 结束数组。 */
    public fun endArray()

    /** 写出对象字段名。 */
    public fun fieldName(name: String)

    /** 写出已有文档树节点。 */
    public fun writeNode(node: Node)
}
