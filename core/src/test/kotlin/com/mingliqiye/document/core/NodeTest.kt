package com.mingliqiye.document.core

import com.mingliqiye.document.exception.DocumentTypeException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NodeTest {

    @Test
    fun createObjectAndArray() {
        val objectNode = ObjectNode.of(
            "name" to StringNode("小于"),
            "age" to NumberNode.of(18),
            "tags" to ArrayNode.of(StringNode("a"), StringNode("b")),
            "empty" to NullNode,
        )

        assertEquals(NodeType.OBJECT, objectNode.type)
        assertTrue(objectNode.isObject)
        assertEquals(4, objectNode.size)
        assertEquals("小于", objectNode["name"]?.asString())
        assertEquals(18, objectNode["age"]?.asNumber()?.toInt())
        assertEquals(2, objectNode["tags"]?.asArray()?.size)
        assertTrue(objectNode["empty"]!!.isNull)
        assertEquals(listOf("name", "age", "tags", "empty"), objectNode.keys.toList())
    }

    @Test
    fun immutableCollections() {
        val objectNode = ObjectNode.from(mapOf("a" to NumberNode.of(1)))
        val arrayNode = ArrayNode.of(StringNode("x"))

        assertFailsWith<UnsupportedOperationException> {
            (objectNode.asMap() as MutableMap<String, Node>)["b"] = NullNode
        }
        assertFailsWith<UnsupportedOperationException> {
            (arrayNode.asList() as MutableList<Node>).add(NullNode)
        }
    }

    @Test
    fun numberEqualityAndRaw() {
        val raw = NumberNode.ofRaw("1.0")
        assertEquals("1.0", raw.raw)
        assertEquals(0, raw.value.compareTo(BigDecimal("1")))
        assertEquals(NumberNode.of(1), raw)
        assertEquals(NumberNode.of(1).hashCode(), raw.hashCode())
    }

    @Test
    fun typeMismatch() {
        val array = ArrayNode.of()
        assertNull(array.asObjectOrNull())
        assertFailsWith<DocumentTypeException> { array.asObject() }
        assertFailsWith<DocumentTypeException> { array.asString() }
    }

    @Test
    fun nullNodeIsSingleton() {
        assertSame(NullNode, NullNode)
        assertTrue(NullNode.isNull)
    }
}
