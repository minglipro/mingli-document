package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.Node

/**
 * 格式无关的流式文档读取接口。
 *
 * Mapper 直接读取实体字段，不要求格式先构建完整 [Node] 树。
 * 只为转换器、既有 Node 路径提供 [readNode] 回退。
 */
public interface DocumentValueReader {

    /** 读取 null；当前值不是 null 时返回 `false` 且不移动位置。 */
    public fun tryReadNull(): Boolean

    /** 读取字符串。 */
    public fun readString(): String

    /** 读取数字原始文本。 */
    public fun readNumberRaw(): String

    /** 直接读取 int，不创建中间字符串。 */
    public fun readInt(): Int

    /** 直接读取 long，不创建中间字符串。 */
    public fun readLong(): Long

    /** 直接读取 double。 */
    public fun readDouble(): Double

    /** 读取布尔值。 */
    public fun readBoolean(): Boolean

    /** 开始读取对象。 */
    public fun beginObject()

    /** 是否还有对象字段。 */
    public fun hasNextField(): Boolean

    /** 读取当前字段名，并消费字段名后的冒号。 */
    public fun readFieldName(): String

    /** 结束对象。 */
    public fun endObject()

    /** 开始读取数组。 */
    public fun beginArray()

    /** 是否还有数组元素。 */
    public fun hasNextElement(): Boolean

    /** 结束数组。 */
    public fun endArray()

    /** 跳过当前值。 */
    public fun skipValue()

    /** 把当前值读成文档树，用于转换器或 Node 兼容路径。 */
    public fun readNode(): Node
}
