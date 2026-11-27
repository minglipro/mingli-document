package com.mingliqiye.document.core

import com.mingliqiye.document.exception.DocumentTypeException

/**
 * 与具体格式无关的文档树节点。
 *
 * 公共 API 只暴露下列接口；具体实现由 `Default*` 类内部提供。
 */
public sealed interface Node {

    /** 当前节点的类型。 */
    public val type: NodeType

    /** 是否为对象。 */
    public val isObject: Boolean
        get() = type == NodeType.OBJECT

    /** 是否为数组。 */
    public val isArray: Boolean
        get() = type == NodeType.ARRAY

    /** 是否为数字。 */
    public val isNumber: Boolean
        get() = type == NodeType.NUMBER

    /** 是否为字符串。 */
    public val isString: Boolean
        get() = type == NodeType.STRING

    /** 是否为布尔值。 */
    public val isBoolean: Boolean
        get() = type == NodeType.BOOLEAN

    /** 是否为 null。 */
    public val isNull: Boolean
        get() = type == NodeType.NULL

    /**
     * 转为对象节点。
     *
     * @throws DocumentTypeException 当前节点不是对象时抛出
     */
    public fun asObject(): ObjectNode = asObjectOrNull()
        ?: throw DocumentTypeException("Expected object but was $type")

    /** 转为对象节点；类型不匹配时返回 `null`。 */
    public fun asObjectOrNull(): ObjectNode? = this as? ObjectNode

    /**
     * 转为数组节点。
     *
     * @throws DocumentTypeException 当前节点不是数组时抛出
     */
    public fun asArray(): ArrayNode = asArrayOrNull()
        ?: throw DocumentTypeException("Expected array but was $type")

    /** 转为数组节点；类型不匹配时返回 `null`。 */
    public fun asArrayOrNull(): ArrayNode? = this as? ArrayNode

    /**
     * 转为数字节点。
     *
     * @throws DocumentTypeException 当前节点不是数字时抛出
     */
    public fun asNumber(): NumberNode = asNumberOrNull()
        ?: throw DocumentTypeException("Expected number but was $type")

    /** 转为数字节点；类型不匹配时返回 `null`。 */
    public fun asNumberOrNull(): NumberNode? = this as? NumberNode

    /**
     * 转为字符串值。
     *
     * @throws DocumentTypeException 当前节点不是字符串时抛出
     */
    public fun asString(): String = asStringOrNull()
        ?: throw DocumentTypeException("Expected string but was $type")

    /** 转为字符串值；类型不匹配时返回 `null`。 */
    public fun asStringOrNull(): String? = (this as? StringNode)?.value

    /**
     * 转为布尔值。
     *
     * @throws DocumentTypeException 当前节点不是布尔值时抛出
     */
    public fun asBoolean(): Boolean = asBooleanOrNull()
        ?: throw DocumentTypeException("Expected boolean but was $type")

    /** 转为布尔值；类型不匹配时返回 `null`。 */
    public fun asBooleanOrNull(): Boolean? = (this as? BooleanNode)?.value
}
