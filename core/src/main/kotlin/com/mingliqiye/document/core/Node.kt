package com.mingliqiye.document.core

import com.mingliqiye.document.exception.DocumentTypeException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections
import java.util.LinkedHashMap

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

/**
 * 对象节点契约。
 */
public interface ObjectNode : Node {

    override val type: NodeType
        get() = NodeType.OBJECT

    /** 对象字段数量。 */
    public val size: Int

    /** 字段名集合，保持插入顺序。 */
    public val keys: Set<String>

    /** 判断是否包含指定字段（即使字段值为 null 也返回 true）。 */
    public fun containsKey(key: String): Boolean

    /**
     * 读取字段。
     *
     * @param key 字段名
     * @return 字段值；不存在时返回 `null`
     */
    public operator fun get(key: String): Node?

    /** 字段条目集合，保持插入顺序。 */
    public fun entries(): Set<Map.Entry<String, Node>>

    /** 转为不可变 Map。 */
    public fun asMap(): Map<String, Node>

    override fun asObject(): ObjectNode = this

    public companion object {

        /** 空对象。 */
        @JvmField
        public val EMPTY: ObjectNode = DefaultObjectNode(LinkedHashMap())

        /**
         * 从键值对创建对象；重复 key 以后出现的值为准。
         */
        @JvmStatic
        public fun of(vararg entries: Pair<String, Node>): ObjectNode = DefaultObjectNode.of(*entries)

        /**
         * 从 Map 创建对象，保持 Map 的迭代顺序。
         */
        @JvmStatic
        public fun from(map: Map<String, Node>): ObjectNode = DefaultObjectNode.from(map)
    }
}

/**
 * 数组节点契约。
 */
public interface ArrayNode :
    Node,
    Iterable<Node> {

    override val type: NodeType
        get() = NodeType.ARRAY

    /** 数组元素数量。 */
    public val size: Int

    /**
     * 按下标读取元素。
     *
     * @throws IndexOutOfBoundsException 下标越界时抛出
     */
    public operator fun get(index: Int): Node

    /** 转为不可变 List。 */
    public fun asList(): List<Node>

    override fun asArray(): ArrayNode = this

    public companion object {

        /** 空数组。 */
        @JvmField
        public val EMPTY: ArrayNode = DefaultArrayNode(emptyList())

        /** 从节点数组创建数组。 */
        @JvmStatic
        public fun of(vararg elements: Node): ArrayNode = DefaultArrayNode(elements.toList())

        /** 从可迭代对象创建数组。 */
        @JvmStatic
        public fun from(elements: Iterable<Node>): ArrayNode = DefaultArrayNode(elements.toList())
    }
}

/**
 * 数字节点契约。
 *
 * 数字使用 [BigDecimal] 保存精确值；[raw] 保留原始文本。
 */
public interface NumberNode : Node {

    override val type: NodeType
        get() = NodeType.NUMBER

    /** 数字原始文本。 */
    public val raw: String

    /** 数字的精确十进制值。 */
    public val value: BigDecimal

    /** 转为 Byte。 */
    public fun toByte(): Byte = value.byteValueExact()

    /** 转为 Short。 */
    public fun toShort(): Short = value.shortValueExact()

    /** 转为 Int。 */
    public fun toInt(): Int = value.intValueExact()

    /** 转为 Long。 */
    public fun toLong(): Long = value.longValueExact()

    /** 转为 BigInteger。 */
    public fun toBigInteger(): BigInteger = value.toBigIntegerExact()

    /** 转为 Float，可能丢失精度。 */
    public fun toFloat(): Float = value.toFloat()

    /** 转为 Double，可能丢失精度。 */
    public fun toDouble(): Double = value.toDouble()

    /** 转为 BigDecimal。 */
    public fun toBigDecimal(): BigDecimal = value

    override fun asNumber(): NumberNode = this

    public companion object {

        /** 从原始数字文本创建，保留原始写法。 */
        @JvmStatic
        public fun ofRaw(raw: String): NumberNode = DefaultNumberNode(raw)

        /** 从 [BigDecimal] 创建。 */
        @JvmStatic
        public fun of(value: BigDecimal): NumberNode = DefaultNumberNode(value.toPlainString(), value)

        /** 从 [Int] 创建。 */
        @JvmStatic
        public fun of(value: Int): NumberNode = of(BigDecimal.valueOf(value.toLong()))

        /** 从 [Long] 创建。 */
        @JvmStatic
        public fun of(value: Long): NumberNode = of(BigDecimal.valueOf(value))

        /** 从 [Double] 创建。 */
        @JvmStatic
        public fun of(value: Double): NumberNode {
            require(value.isFinite()) { "NaN and Infinity are not valid numbers" }
            return of(BigDecimal.valueOf(value))
        }

        /** 从 [Float] 创建。 */
        @JvmStatic
        public fun of(value: Float): NumberNode {
            require(value.isFinite()) { "NaN and Infinity are not valid numbers" }
            return of(BigDecimal.valueOf(value.toDouble()))
        }

        /** 从 [BigInteger] 创建。 */
        @JvmStatic
        public fun of(value: BigInteger): NumberNode = of(BigDecimal(value))
    }
}

/**
 * 字符串节点契约。
 */
public interface StringNode : Node {

    override val type: NodeType
        get() = NodeType.STRING

    /** 字符串值。 */
    public val value: String

    override fun asString(): String = value

    public companion object {

        /** 创建字符串节点。 */
        @JvmStatic
        public fun of(value: String): StringNode = DefaultStringNode(value)
    }
}

/**
 * 布尔节点契约。
 */
public interface BooleanNode : Node {

    override val type: NodeType
        get() = NodeType.BOOLEAN

    /** 布尔值。 */
    public val value: Boolean

    override fun asBoolean(): Boolean = value

    public companion object {

        /** 创建布尔节点。 */
        @JvmStatic
        public fun of(value: Boolean): BooleanNode = DefaultBooleanNode(value)
    }
}

/**
 * null 节点契约。
 */
public interface NullNode : Node {

    override val type: NodeType
        get() = NodeType.NULL

    override val isNull: Boolean
        get() = true

    public companion object {

        /** 全局 null 节点实例。 */
        @JvmField
        public val INSTANCE: NullNode = DefaultNullNode
    }
}

/**
 * 节点类型。
 */
public enum class NodeType(public val value: String) {
    OBJECT("object"),
    ARRAY("array"),
    NUMBER("number"),
    STRING("string"),
    BOOLEAN("boolean"),
    NULL("null"),
}

/**
 * 对象节点默认实现。
 */
internal class DefaultObjectNode(
    private val fields: LinkedHashMap<String, Node>,
) : ObjectNode {

    override val size: Int
        get() = fields.size

    override val keys: Set<String>
        get() = Collections.unmodifiableSet(fields.keys)

    override fun containsKey(key: String): Boolean = fields.containsKey(key)

    override fun get(key: String): Node? = fields[key]

    override fun entries(): Set<Map.Entry<String, Node>> = asMap().entries

    override fun asMap(): Map<String, Node> = Collections.unmodifiableMap(fields)

    override fun equals(other: Any?): Boolean = other is ObjectNode && fields == other.asMap()

    override fun hashCode(): Int = fields.hashCode()

    override fun toString(): String = "ObjectNode($fields)"

    companion object {

        fun of(vararg entries: Pair<String, Node>): ObjectNode {
            val fields = LinkedHashMap<String, Node>(entries.size)
            for ((key, value) in entries) {
                fields[key] = value
            }
            return DefaultObjectNode(fields)
        }

        fun from(map: Map<String, Node>): ObjectNode = DefaultObjectNode(LinkedHashMap(map))
    }
}

/**
 * 数组节点默认实现。
 */
internal class DefaultArrayNode(
    private val elements: List<Node>,
) : ArrayNode {

    override val size: Int
        get() = elements.size

    override fun get(index: Int): Node = elements[index]

    override fun asList(): List<Node> = Collections.unmodifiableList(elements)

    override fun iterator(): Iterator<Node> = asList().iterator()

    override fun equals(other: Any?): Boolean = other is ArrayNode && elements == other.asList()

    override fun hashCode(): Int = elements.hashCode()

    override fun toString(): String = "ArrayNode($elements)"
}

/**
 * 数字节点默认实现。
 */
internal class DefaultNumberNode(
    override val raw: String,
    override val value: BigDecimal,
) : NumberNode {

    constructor(raw: String) : this(raw, BigDecimal(raw))

    override fun equals(other: Any?): Boolean = other is NumberNode && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = raw
}

/**
 * 字符串节点默认实现。
 */
internal data class DefaultStringNode(
    override val value: String,
) : StringNode

/**
 * 布尔节点默认实现。
 */
internal data class DefaultBooleanNode(
    override val value: Boolean,
) : BooleanNode

/**
 * null 节点默认实现。
 */
internal object DefaultNullNode : NullNode {
    override fun toString(): String = "null"
}
