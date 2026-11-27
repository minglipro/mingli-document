package com.mingliqiye.document.core

import com.mingliqiye.document.exception.DocumentTypeException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 与具体格式无关的文档树节点。
 *
 * 支持的类型见 [NodeType]：
 * - 对象：[ObjectNode]
 * - 数组：[ArrayNode]
 * - 数字：[NumberNode]
 * - 字符串：[StringNode]
 * - 布尔：[BooleanNode]
 * - null：[NullNode]
 *
 * 所有具体节点都不可变，可作为 `Map` 的 key 或集合元素使用。
 */
sealed interface Node {

    /** 当前节点的类型。 */
    val type: NodeType

    /** 是否为对象。 */
    val isObject: Boolean
        get() = type == NodeType.OBJECT

    /** 是否为数组。 */
    val isArray: Boolean
        get() = type == NodeType.ARRAY

    /** 是否为数字。 */
    val isNumber: Boolean
        get() = type == NodeType.NUMBER

    /** 是否为字符串。 */
    val isString: Boolean
        get() = type == NodeType.STRING

    /** 是否为布尔值。 */
    val isBoolean: Boolean
        get() = type == NodeType.BOOLEAN

    /** 是否为 null。 */
    val isNull: Boolean
        get() = type == NodeType.NULL

    /**
     * 转为对象节点。
     *
     * @throws DocumentTypeException 当前节点不是对象时抛出
     */
    fun asObject(): ObjectNode = asObjectOrNull()
        ?: throw DocumentTypeException("Expected object but was $type")

    /**
     * 转为对象节点；类型不匹配时返回 `null`。
     */
    fun asObjectOrNull(): ObjectNode? = this as? ObjectNode

    /**
     * 转为数组节点。
     *
     * @throws DocumentTypeException 当前节点不是数组时抛出
     */
    fun asArray(): ArrayNode = asArrayOrNull()
        ?: throw DocumentTypeException("Expected array but was $type")

    /**
     * 转为数组节点；类型不匹配时返回 `null`。
     */
    fun asArrayOrNull(): ArrayNode? = this as? ArrayNode

    /**
     * 转为数字节点。
     *
     * @throws DocumentTypeException 当前节点不是数字时抛出
     */
    fun asNumber(): NumberNode = asNumberOrNull()
        ?: throw DocumentTypeException("Expected number but was $type")

    /**
     * 转为数字节点；类型不匹配时返回 `null`。
     */
    fun asNumberOrNull(): NumberNode? = this as? NumberNode

    /**
     * 转为字符串值。
     *
     * @throws DocumentTypeException 当前节点不是字符串时抛出
     */
    fun asString(): String = asStringOrNull()
        ?: throw DocumentTypeException("Expected string but was $type")

    /**
     * 转为字符串值；类型不匹配时返回 `null`。
     */
    fun asStringOrNull(): String? = (this as? StringNode)?.value

    /**
     * 转为布尔值。
     *
     * @throws DocumentTypeException 当前节点不是布尔值时抛出
     */
    fun asBoolean(): Boolean = asBooleanOrNull()
        ?: throw DocumentTypeException("Expected boolean but was $type")

    /**
     * 转为布尔值；类型不匹配时返回 `null`。
     */
    fun asBooleanOrNull(): Boolean? = (this as? BooleanNode)?.value
}

/**
 * 对象节点，字段保持插入顺序。
 */
class ObjectNode private constructor(
    private val fields: LinkedHashMap<String, Node>,
) : Node {

    override val type: NodeType
        get() = NodeType.OBJECT

    /** 对象字段数量。 */
    val size: Int
        get() = fields.size

    /** 字段名集合，保持插入顺序。 */
    val keys: Set<String>
        get() = Collections.unmodifiableSet(fields.keys)

    /** 判断是否包含指定字段（即使字段值为 null 也返回 true）。 */
    fun containsKey(key: String): Boolean = fields.containsKey(key)

    /**
     * 读取字段。
     *
     * @param key 字段名
     * @return 字段值；不存在时返回 `null`
     */
    operator fun get(key: String): Node? = fields[key]

    /** 字段条目集合，保持插入顺序。 */
    fun entries(): Set<Map.Entry<String, Node>> = Collections.unmodifiableSet(fields.entries)

    /** 转为不可变 Map。 */
    fun asMap(): Map<String, Node> = Collections.unmodifiableMap(fields)

    override fun equals(other: Any?): Boolean = other is ObjectNode && fields == other.fields

    override fun hashCode(): Int = fields.hashCode()

    override fun toString(): String = "ObjectNode($fields)"

    companion object {

        /** 空对象。 */
        @JvmField
        val EMPTY: ObjectNode = ObjectNode(LinkedHashMap())

        /**
         * 从键值对创建对象；重复 key 以后出现的值为准。
         */
        @JvmStatic
        fun of(vararg entries: Pair<String, Node>): ObjectNode {
            val fields = LinkedHashMap<String, Node>(entries.size)
            for ((key, value) in entries) {
                fields[key] = value
            }
            return ObjectNode(fields)
        }

        /**
         * 从 Map 创建对象，保持 Map 的迭代顺序。
         */
        @JvmStatic
        fun from(map: Map<String, Node>): ObjectNode = ObjectNode(LinkedHashMap(map))
    }
}

/**
 * 数组节点。
 */
class ArrayNode private constructor(
    private val elements: List<Node>,
) : Node,
    Iterable<Node> {

    override val type: NodeType
        get() = NodeType.ARRAY

    /** 数组元素数量。 */
    val size: Int
        get() = elements.size

    /**
     * 按下标读取元素。
     *
     * @throws IndexOutOfBoundsException 下标越界时抛出
     */
    operator fun get(index: Int): Node = elements[index]

    /** 转为不可变 List。 */
    fun asList(): List<Node> = Collections.unmodifiableList(elements)

    override fun iterator(): Iterator<Node> = asList().iterator()

    override fun equals(other: Any?): Boolean = other is ArrayNode && elements == other.elements

    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String = "ArrayNode($elements)"

    companion object {

        /** 空数组。 */
        @JvmField
        val EMPTY: ArrayNode = ArrayNode(emptyList())

        /** 从节点数组创建数组。 */
        @JvmStatic
        fun of(vararg elements: Node): ArrayNode = ArrayNode(elements.toList())

        /** 从可迭代对象创建数组。 */
        @JvmStatic
        fun from(elements: Iterable<Node>): ArrayNode = ArrayNode(elements.toList())
    }
}

/**
 * 数字节点。
 *
 * 数字使用 [BigDecimal] 保存，避免 `Double` 精度丢失；[raw] 保留原始文本。
 */
class NumberNode private constructor(
    /** 数字原始文本。 */
    val raw: String,
    /** 数字的精确十进制值。 */
    val value: BigDecimal,
) : Node {

    override val type: NodeType
        get() = NodeType.NUMBER

    /**
     * 转为 Byte。
     *
     * @throws ArithmeticException 数字有小数或超出 Byte 范围时抛出
     */
    fun toByte(): Byte = value.byteValueExact()

    /**
     * 转为 Short。
     *
     * @throws ArithmeticException 数字有小数或超出 Short 范围时抛出
     */
    fun toShort(): Short = value.shortValueExact()

    /**
     * 转为 Int。
     *
     * @throws ArithmeticException 数字有小数或超出 Int 范围时抛出
     */
    fun toInt(): Int = value.intValueExact()

    /**
     * 转为 Long。
     *
     * @throws ArithmeticException 数字有小数或超出 Long 范围时抛出
     */
    fun toLong(): Long = value.longValueExact()

    /**
     * 转为 BigInteger。
     *
     * @throws ArithmeticException 数字有小数时抛出
     */
    fun toBigInteger(): BigInteger = value.toBigIntegerExact()

    /** 转为 Float，可能丢失精度。 */
    fun toFloat(): Float = value.toFloat()

    /** 转为 Double，可能丢失精度。 */
    fun toDouble(): Double = value.toDouble()

    /** 转为 BigDecimal。 */
    fun toBigDecimal(): BigDecimal = value

    override fun equals(other: Any?): Boolean = other is NumberNode && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = raw

    companion object {

        /**
         * 从原始数字文本创建，保留原始写法。
         *
         * @throws NumberFormatException 文本不是合法数字时抛出
         */
        @JvmStatic
        fun ofRaw(raw: String): NumberNode = NumberNode(raw, BigDecimal(raw))

        /** 从 [BigDecimal] 创建。 */
        @JvmStatic
        fun of(value: BigDecimal): NumberNode = NumberNode(value.toPlainString(), value)

        /** 从 [Int] 创建。 */
        @JvmStatic
        fun of(value: Int): NumberNode = of(BigDecimal.valueOf(value.toLong()))

        /** 从 [Long] 创建。 */
        @JvmStatic
        fun of(value: Long): NumberNode = of(BigDecimal.valueOf(value))

        /** 从 [Double] 创建，要求必须是有限值。 */
        @JvmStatic
        fun of(value: Double): NumberNode {
            require(value.isFinite()) { "NaN and Infinity are not valid numbers" }
            return of(BigDecimal.valueOf(value))
        }

        /** 从 [Float] 创建，要求必须是有限值。 */
        @JvmStatic
        fun of(value: Float): NumberNode {
            require(value.isFinite()) { "NaN and Infinity are not valid numbers" }
            return of(BigDecimal.valueOf(value.toDouble()))
        }

        /** 从 [BigInteger] 创建。 */
        @JvmStatic
        fun of(value: BigInteger): NumberNode = of(BigDecimal(value))
    }
}

/**
 * 字符串节点。
 */
data class StringNode(
    /** 字符串值。 */
    val value: String,
) : Node {

    override val type: NodeType
        get() = NodeType.STRING
}

/**
 * 布尔节点。
 */
data class BooleanNode(
    /** 布尔值。 */
    val value: Boolean,
) : Node {

    override val type: NodeType
        get() = NodeType.BOOLEAN
}

/**
 * null 节点。
 */
object NullNode : Node {

    override val type: NodeType
        get() = NodeType.NULL

    override val isNull: Boolean
        get() = true

    override fun toString(): String = "null"
}

/**
 * 节点类型。
 */
enum class NodeType(val value: String) {
    OBJECT("object"),
    ARRAY("array"),
    NUMBER("number"),
    STRING("string"),
    BOOLEAN("boolean"),
    NULL("null"),
}
