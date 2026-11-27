package com.mingliqiye.document.core

import com.mingliqiye.document.exception.DocumentTypeException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 公共 [Node] 接口契约测试。
 *
 * 覆盖类型访问、字段语义、只读集合、相等性和 null 单例；与 [NodeTest] 互补。
 */
class NodeContractTest {

    @Test
    fun typeFlagsMatchNodeType() {
        val cases = listOf(
            NullNode.INSTANCE to NodeType.NULL,
            BooleanNode.of(true) to NodeType.BOOLEAN,
            StringNode.of("text") to NodeType.STRING,
            NumberNode.of(1) to NodeType.NUMBER,
            ArrayNode.EMPTY to NodeType.ARRAY,
            ObjectNode.EMPTY to NodeType.OBJECT,
        )

        for ((node, expectedType) in cases) {
            assertEquals(expectedType, node.type)
            assertEquals(expectedType == NodeType.OBJECT, node.isObject)
            assertEquals(expectedType == NodeType.ARRAY, node.isArray)
            assertEquals(expectedType == NodeType.NUMBER, node.isNumber)
            assertEquals(expectedType == NodeType.STRING, node.isString)
            assertEquals(expectedType == NodeType.BOOLEAN, node.isBoolean)
            assertEquals(expectedType == NodeType.NULL, node.isNull)
        }
    }

    @Test
    fun nodeTypeExposesFormatNames() {
        assertEquals(
            listOf("object", "array", "number", "string", "boolean", "null"),
            NodeType.values().map { it.value },
        )
    }

    @Test
    fun typedAccessorsReturnValuesOnMatchingNodes() {
        val text = StringNode.of("小于")
        assertEquals("小于", text.asString())
        assertEquals("小于", text.asStringOrNull())

        val number = NumberNode.ofRaw("42")
        assertSame(number, number.asNumber())
        assertEquals(42, number.asNumber().toInt())
        assertEquals(42, number.asNumberOrNull()?.toInt())

        val flag = BooleanNode.of(false)
        assertFalse(flag.asBoolean())
        assertEquals(false, flag.asBooleanOrNull())

        val objectNode = ObjectNode.of("text" to text)
        assertSame(objectNode, objectNode.asObject())
        assertSame(objectNode, objectNode.asObjectOrNull())

        val arrayNode = ArrayNode.of(text)
        assertSame(arrayNode, arrayNode.asArray())
        assertSame(arrayNode, arrayNode.asArrayOrNull())
    }

    @Test
    fun typedAccessorsRejectMismatchedNodes() {
        val objectNode = ObjectNode.of("number" to NumberNode.of(1))

        assertNull(objectNode.asArrayOrNull())
        assertNull(objectNode.asNumberOrNull())
        assertNull(objectNode.asStringOrNull())
        assertNull(objectNode.asBooleanOrNull())

        assertFailsWith<DocumentTypeException> { objectNode.asArray() }
        assertFailsWith<DocumentTypeException> { objectNode.asNumber() }
        assertFailsWith<DocumentTypeException> { objectNode.asString() }
        assertFailsWith<DocumentTypeException> { objectNode.asBoolean() }
        assertFailsWith<DocumentTypeException> { NullNode.INSTANCE.asObject() }
    }

    @Test
    fun numberConversionsAreExact() {
        val number = NumberNode.of(300)
        assertEquals(300.toShort(), number.toShort())
        assertEquals(300, number.toInt())
        assertEquals(300L, number.toLong())
        assertEquals(BigInteger.valueOf(300), number.toBigInteger())
        assertEquals(300.0f, number.toFloat())
        assertFailsWith<ArithmeticException> { number.toByte() }

        val decimal = NumberNode.of(BigDecimal("2.50"))
        assertEquals("2.50", decimal.raw)
        assertEquals(0, BigDecimal("2.5").compareTo(decimal.value))
        assertEquals(0, BigDecimal("2.5").compareTo(decimal.toBigDecimal()))
        assertEquals(2.5, decimal.toDouble())
        assertFailsWith<ArithmeticException> { decimal.toInt() }

        val exponent = NumberNode.ofRaw("1e3")
        assertEquals("1e3", exponent.raw)
        assertEquals(0, BigDecimal("1000").compareTo(exponent.value))

        assertFailsWith<IllegalArgumentException> { NumberNode.of(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { NumberNode.of(Float.POSITIVE_INFINITY) }
    }

    @Test
    fun absentFieldIsNullButExplicitNullFieldIsPreserved() {
        val objectNode = ObjectNode.of(
            "nothing" to NullNode.INSTANCE,
            "value" to StringNode.of("v"),
        )

        assertEquals(2, objectNode.size)
        assertTrue(objectNode.containsKey("nothing"))
        assertTrue(objectNode.containsKey("value"))
        assertFalse(objectNode.containsKey("missing"))
        assertNull(objectNode["missing"])
        assertSame(NullNode.INSTANCE, objectNode["nothing"])
        assertTrue(objectNode["nothing"]!!.isNull)
        assertEquals("v", objectNode["value"]?.asString())
    }

    @Test
    fun duplicateFieldsKeepLastValueAndInsertionOrder() {
        val objectNode = ObjectNode.of(
            "a" to NumberNode.of(1),
            "b" to NumberNode.of(2),
            "a" to NumberNode.of(3),
        )

        assertEquals(2, objectNode.size)
        assertEquals(3, objectNode["a"]!!.asNumber().toInt())
        assertEquals(listOf("a", "b"), objectNode.keys.toList())
        assertEquals(listOf("a", "b"), objectNode.entries().map { it.key })
    }

    @Test
    fun objectFactoryPreservesIterationOrderAndCopiesSource() {
        val source = linkedMapOf<String, Node>("second" to NumberNode.of(2), "first" to NumberNode.of(1))
        val objectNode = ObjectNode.from(source)

        assertEquals(listOf("second", "first"), objectNode.keys.toList())
        source["third"] = NumberNode.of(3)
        assertEquals(2, objectNode.size)
        assertFalse(objectNode.containsKey("third"))
    }

    @Test
    fun arrayFactoryCopiesSourceElements() {
        val source = mutableListOf<StringNode>(StringNode.of("a"))
        val arrayNode = ArrayNode.from(source)
        source.add(StringNode.of("b"))

        assertEquals(1, arrayNode.size)
        assertEquals("a", arrayNode[0].asString())
        assertEquals(listOf("a"), arrayNode.map { it.asString() })
        assertFailsWith<IndexOutOfBoundsException> { arrayNode[1] }
    }

    @Test
    fun emptyConstantsExposeEmptyCollections() {
        assertEquals(0, ObjectNode.EMPTY.size)
        assertTrue(ObjectNode.EMPTY.keys.isEmpty())
        assertTrue(ObjectNode.EMPTY.asMap().isEmpty())
        assertTrue(ObjectNode.EMPTY.entries().isEmpty())

        assertEquals(0, ArrayNode.EMPTY.size)
        assertTrue(ArrayNode.EMPTY.asList().isEmpty())
        assertFalse(ArrayNode.EMPTY.iterator().hasNext())
    }

    @Test
    fun objectFieldViewsAreReadOnly() {
        val objectNode = ObjectNode.of("a" to NumberNode.of(1))

        assertFailsWith<UnsupportedOperationException> {
            (objectNode.keys as MutableSet<String>).add("b")
        }
        assertFailsWith<UnsupportedOperationException> {
            (objectNode.asMap() as MutableMap<String, Node>)["b"] = NullNode.INSTANCE
        }
        assertFailsWith<UnsupportedOperationException> {
            (objectNode.entries() as MutableSet<Map.Entry<String, Node>>)
                .add(java.util.AbstractMap.SimpleImmutableEntry("b", NullNode.INSTANCE))
        }
    }

    @Test
    fun objectEntryValuesAreReadOnly() {
        val objectNode = ObjectNode.of("a" to NumberNode.of(1))

        @Suppress("UNCHECKED_CAST")
        val entry = objectNode.entries().first() as MutableMap.MutableEntry<String, Node>

        assertFailsWith<UnsupportedOperationException> {
            entry.setValue(NullNode.INSTANCE)
        }
        assertEquals(1, objectNode["a"]?.asNumber()?.toInt())
    }

    @Test
    fun objectEqualityIsStructuralAndConsistentWithHashCode() {
        val first = ObjectNode.of(
            "id" to NumberNode.of(1),
            "tags" to ArrayNode.of(StringNode.of("x")),
        )
        val second = ObjectNode.from(
            linkedMapOf(
                "id" to NumberNode.of(1L),
                "tags" to ArrayNode.of(StringNode.of("x")),
            ),
        )

        assertEquals(first, second)
        assertEquals(second, first)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals<Any>(first, ObjectNode.of("id" to NumberNode.of(2)))
        assertNotEquals<Any>(first, ArrayNode.of(NumberNode.of(1)))
        assertFalse(first.equals("ObjectNode"))
    }

    @Test
    fun arrayEqualityIsStructuralAndOrderSensitive() {
        val first = ArrayNode.of(NumberNode.of(1), StringNode.of("x"))
        val second = ArrayNode.from(listOf(NumberNode.of(1L), StringNode.of("x")))

        assertEquals(first, second)
        assertEquals(second, first)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals<Any>(first, ArrayNode.of(StringNode.of("x"), NumberNode.of(1)))
        assertNotEquals<Any>(first, ObjectNode.EMPTY)
    }

    @Test
    fun scalarEqualityIsValueBased() {
        assertEquals(StringNode.of("x"), StringNode.of("x"))
        assertEquals(StringNode.of("x").hashCode(), StringNode.of("x").hashCode())
        assertEquals(BooleanNode.of(true), BooleanNode.of(true))
        assertEquals(BooleanNode.of(true).hashCode(), BooleanNode.of(true).hashCode())
        assertNotEquals<Any>(StringNode.of("x"), StringNode.of("y"))
        assertNotEquals<Any>(BooleanNode.of(true), BooleanNode.of(false))
        assertNotEquals<Any>(StringNode.of("1"), NumberNode.of(1))
        assertNotEquals<Any>(BooleanNode.of(true), StringNode.of("true"))
    }

    @Test
    fun nullNodeIsSharedSingleton() {
        val fromObject: Node? = ObjectNode.of("v" to NullNode.INSTANCE)["v"]
        val fromArray: Node = ArrayNode.of(NullNode.INSTANCE)[0]

        assertSame(NullNode.INSTANCE, NullNode.INSTANCE)
        assertSame(NullNode.INSTANCE, fromObject)
        assertSame(NullNode.INSTANCE, fromArray)
        assertEquals(NodeType.NULL, NullNode.INSTANCE.type)
        assertTrue(NullNode.INSTANCE.isNull)
        assertFalse(NullNode.INSTANCE.isObject)
    }
}
